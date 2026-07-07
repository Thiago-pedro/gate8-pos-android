package br.com.gate8.pos.mercadopago.payment

import br.com.gate8.pos.core.network.ApiException
import br.com.gate8.pos.data.prefs.DeviceConfigStore
import br.com.gate8.pos.data.remote.api.PosApiService
import br.com.gate8.pos.data.remote.dto.ApiErrorDto
import br.com.gate8.pos.data.remote.dto.CreateMpOrderRequestDto
import br.com.gate8.pos.data.remote.dto.CreateMpOrderResponseDto
import br.com.gate8.pos.data.remote.dto.MpOrderStatusResponseDto
import br.com.gate8.pos.data.remote.dto.MpRefundRequestDto
import br.com.gate8.pos.domain.model.PaymentMethodApi
import br.com.gate8.pos.payment.PaymentCancelledException
import br.com.gate8.pos.payment.PaymentGateway
import br.com.gate8.pos.payment.PaymentResult
import br.com.gate8.pos.payment.PixExpiredException
import br.com.gate8.pos.payment.VoidResult
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import retrofit2.Response

/**
 * Pagamentos via Mercado Pago Point (proxy Gate8 → API de Orders MP).
 *
 * 1. [charge] cria order no backend (`POST /payments/mp/orders`).
 * 2. O terminal Point recebe a cobrança (operador em "Inserir valor" se necessário).
 * 3. Polling em `GET /payments/mp/orders/{id}` até `processed` ou falha.
 * 4. O caller registra a venda em `POST /sales` com `acquirer`.
 */
class MercadoPagoPaymentGateway(
    private val config: DeviceConfigStore,
    private val api: PosApiService,
) : PaymentGateway {

    @Volatile
    private var cancelRequested = false

    @Volatile
    private var currentOrderId: String? = null

    override suspend fun charge(
        amount: Double,
        method: PaymentMethodApi,
        clientReference: String?,
    ): PaymentResult {
        val terminalId = config.getMercadoPagoTerminalId()?.trim().orEmpty()
        if (terminalId.isBlank()) {
            throw IllegalStateException(
                "Configure o Terminal ID do Mercado Pago Point em Configurações.",
            )
        }
        val reference = clientReference?.trim().orEmpty()
        if (reference.isBlank()) {
            throw IllegalStateException("Referência da venda ausente.")
        }
        if (method == PaymentMethodApi.CASH) {
            return PaymentResult(
                method = method,
                nsu = "",
                authorization = "",
                brand = "",
                transactionId = reference,
            )
        }

        cancelRequested = false
        currentOrderId = null

        val created = requireCreateMpOrder(
            api.createMpOrder(
                CreateMpOrderRequestDto(
                    amount = amount,
                    terminalId = terminalId,
                    clientReference = reference,
                    paymentMethod = method.apiValue,
                ),
            ),
        )
        val orderId = created.mpOrderId
        currentOrderId = orderId

        val deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS
        try {
            while (System.currentTimeMillis() < deadline) {
                if (cancelRequested) {
                    runCatching { api.cancelMpOrder(orderId, idempotencyKey("cancel", orderId)) }
                    throw PaymentCancelledException()
                }
                delay(POLL_INTERVAL_MS)
                if (cancelRequested) {
                    runCatching { api.cancelMpOrder(orderId, idempotencyKey("cancel", orderId)) }
                    throw PaymentCancelledException()
                }

                val status = requireMpOrderStatus(api.getMpOrder(orderId))
                when (status.status.lowercase()) {
                    "processed" -> {
                        val acquirer = status.acquirer
                            ?: throw IllegalStateException("Pagamento aprovado sem dados da adquirente.")
                        return PaymentResult(
                            method = method,
                            nsu = acquirer.nsu,
                            authorization = acquirer.authorization,
                            brand = acquirer.brand.orEmpty(),
                            transactionId = acquirer.transactionId,
                        )
                    }
                    "failed", "rejected" -> {
                        val detail = status.statusDetail?.takeIf { it.isNotBlank() }
                        throw ApiException(
                            402,
                            detail ?: "Pagamento recusado na maquininha.",
                            errorCode = status.status,
                        )
                    }
                    "expired", "canceled", "cancelled" -> {
                        throw expiredOrCancelled(method)
                    }
                }
            }
            runCatching { api.cancelMpOrder(orderId, idempotencyKey("cancel", orderId)) }
            throw expiredOrCancelled(method, timedOut = true)
        } finally {
            currentOrderId = null
            cancelRequested = false
        }
    }

    override suspend fun voidTransaction(
        transactionId: String,
        nsu: String?,
        amount: Double,
        method: PaymentMethodApi,
    ): VoidResult {
        if (method == PaymentMethodApi.CASH) {
            return VoidResult(success = true, message = "Estorno em dinheiro — ajuste no caixa.")
        }
        val orderId = transactionId.trim()
        if (orderId.isBlank()) {
            return VoidResult(success = false, message = "ID da order MP ausente para estorno.")
        }
        val response = api.refundMpOrder(
            orderId,
            idempotencyKey("refund", orderId),
            MpRefundRequestDto(amount = amount),
        )
        if (!response.isSuccessful) {
            val message = parseErrorMessage(response)
            return VoidResult(success = false, message = message)
        }
        val body = response.body()
        return VoidResult(
            success = true,
            message = "Estorno MP OK · order ${body?.mpOrderId ?: orderId} · ${body?.status ?: "refunded"}",
        )
    }

    override fun cancelCurrentPayment() {
        cancelRequested = true
    }

    private fun expiredOrCancelled(method: PaymentMethodApi, timedOut: Boolean = false): Exception {
        if (method == PaymentMethodApi.PIX) {
            return PixExpiredException()
        }
        return if (timedOut) {
            Exception("Tempo esgotado aguardando pagamento na maquininha.")
        } else {
            PaymentCancelledException()
        }
    }

    private fun requireCreateMpOrder(response: Response<CreateMpOrderResponseDto>): CreateMpOrderResponseDto {
        if (response.isSuccessful) {
            return response.body()
                ?: throw IllegalStateException("Resposta vazia ao criar cobrança na Point.")
        }
        throw ApiException(
            response.code(),
            parseErrorMessage(response),
            errorCode = peekErrorCode(response.errorBody()?.string()),
        )
    }

    private fun requireMpOrderStatus(response: Response<MpOrderStatusResponseDto>): MpOrderStatusResponseDto {
        if (response.isSuccessful) {
            return response.body()
                ?: throw IllegalStateException("Resposta vazia ao consultar cobrança na Point.")
        }
        throw ApiException(
            response.code(),
            parseErrorMessage(response),
            errorCode = peekErrorCode(response.errorBody()?.string()),
        )
    }

    private fun parseErrorMessage(response: Response<*>): String {
        val raw = response.errorBody()?.string()
        val code = peekErrorCode(raw)
        val dto = raw?.let { decodeError(it) }
        return when {
            response.code() == 409 || code == "order_already_queued" ->
                "Já existe cobrança pendente na maquininha. Conclua ou cancele na Point."
            !dto?.mpMessage.isNullOrBlank() -> dto!!.mpMessage!!
            !dto?.error.isNullOrBlank() -> dto!!.error!!
            code != null -> "Erro MP ($code)"
            else -> "Erro na maquininha (${response.code()})"
        }
    }

    private fun peekErrorCode(raw: String?): String? =
        raw?.let { decodeError(it)?.code ?: decodeError(it)?.error }

    private fun decodeError(raw: String): ApiErrorDto? =
        runCatching { json.decodeFromString<ApiErrorDto>(raw) }.getOrNull()

    private fun idempotencyKey(action: String, orderId: String): String = "$action-$orderId"

    companion object {
        private const val POLL_INTERVAL_MS = 2_000L
        private const val POLL_TIMEOUT_MS = 10 * 60 * 1_000L

        private val json = Json { ignoreUnknownKeys = true }
    }
}
