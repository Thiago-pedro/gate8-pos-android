package br.com.gate8.pos.mercadopago.payment

import br.com.gate8.pos.core.network.ApiException
import br.com.gate8.pos.data.prefs.DeviceConfigStore
import br.com.gate8.pos.data.remote.api.PosApiService
import br.com.gate8.pos.data.remote.dto.ApiErrorDto
import br.com.gate8.pos.data.remote.dto.CreateMpOrderRequestDto
import br.com.gate8.pos.data.remote.dto.CreateMpOrderResponseDto
import br.com.gate8.pos.data.remote.dto.MpSaleDraftDto
import br.com.gate8.pos.domain.model.PaymentMethodApi
import br.com.gate8.pos.payment.PaymentGateway
import br.com.gate8.pos.payment.PaymentResult
import br.com.gate8.pos.payment.VoidResult
import kotlinx.serialization.json.Json
import retrofit2.Response

/**
 * Pagamentos via Mercado Pago Point (proxy Gate8 → API de Orders MP).
 */
class MercadoPagoPaymentGateway(
    private val config: DeviceConfigStore,
    private val api: PosApiService,
) : PaymentGateway {

    private val poller = MercadoPagoOrderPoller(api)

    @Volatile
    private var cancelRequested = false

    @Volatile
    private var currentOrderId: String? = null

    override suspend fun charge(
        amount: Double,
        method: PaymentMethodApi,
        clientReference: String?,
        saleDraft: MpSaleDraftDto?,
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
        if (method == PaymentMethodApi.CASH || method == PaymentMethodApi.CASHLESS) {
            return PaymentResult(
                method = method,
                nsu = "",
                authorization = "",
                brand = if (method == PaymentMethodApi.CASHLESS) "Cashless" else "",
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
                    saleDraft = saleDraft,
                ),
            ),
        )
        val orderId = created.mpOrderId
        currentOrderId = orderId

        try {
            return poller.waitForPayment(
                orderId = orderId,
                method = method,
                isCancelRequested = { cancelRequested },
                onCancelRequested = {
                    runCatching { api.cancelMpOrder(orderId, idempotencyKey("cancel", orderId)) }
                },
                mainDeadlineMs = System.currentTimeMillis() + MercadoPagoOrderPoller.POLL_TIMEOUT_MS,
                gracePeriodMs = MercadoPagoOrderPoller.GRACE_PERIOD_MS,
            )
        } finally {
            currentOrderId = null
            cancelRequested = false
        }
    }

    override suspend fun recoverOrder(
        mpOrderId: String,
        method: PaymentMethodApi,
    ): PaymentResult? = poller.recoverPayment(mpOrderId.trim(), method)

    override suspend fun voidTransaction(
        transactionId: String,
        nsu: String?,
        amount: Double,
        method: PaymentMethodApi,
        authorization: String?,
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
        )
        if (!response.isSuccessful) {
            return VoidResult(success = false, message = parseErrorMessage(response))
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
        private val json = Json { ignoreUnknownKeys = true }
    }
}
