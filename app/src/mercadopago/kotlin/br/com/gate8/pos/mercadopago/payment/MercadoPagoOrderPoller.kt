package br.com.gate8.pos.mercadopago.payment

import br.com.gate8.pos.core.network.ApiException
import br.com.gate8.pos.data.remote.api.PosApiService
import br.com.gate8.pos.data.remote.dto.MpOrderStatusResponseDto
import br.com.gate8.pos.domain.model.PaymentMethodApi
import br.com.gate8.pos.payment.PaymentCancelledException
import br.com.gate8.pos.payment.PaymentResult
import br.com.gate8.pos.payment.PaymentTimedOutException
import br.com.gate8.pos.payment.PixExpiredException
import kotlinx.coroutines.delay
import retrofit2.Response
import java.io.IOException

/**
 * Aguarda confirmação da order na Point com polling resiliente a falhas de rede.
 *
 * Não cancela a order ao estourar o prazo — o cliente pode ainda estar pagando na maquininha.
 */
internal class MercadoPagoOrderPoller(
    private val api: PosApiService,
) {

    suspend fun waitForPayment(
        orderId: String,
        method: PaymentMethodApi,
        isCancelRequested: () -> Boolean,
        onCancelRequested: suspend () -> Unit,
        mainDeadlineMs: Long,
        gracePeriodMs: Long,
    ): PaymentResult {
        val graceDeadline = mainDeadlineMs + gracePeriodMs
        while (System.currentTimeMillis() < graceDeadline) {
            if (isCancelRequested()) {
                onCancelRequested()
                throw PaymentCancelledException()
            }
            delay(POLL_INTERVAL_MS)

            val status = fetchOrderStatus(orderId) ?: continue
            resolveTerminalStatus(status, method, orderId)?.let { return it }
        }

        fetchOrderStatus(orderId)?.let { status ->
            resolveTerminalStatus(status, method, orderId)?.let { return it }
        }

        throw PaymentTimedOutException(orderId)
    }

    suspend fun recoverPayment(
        orderId: String,
        method: PaymentMethodApi,
        timeoutMs: Long = RECOVER_TIMEOUT_MS,
    ): PaymentResult? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            delay(POLL_INTERVAL_MS)
            val status = fetchOrderStatus(orderId) ?: continue
            resolveTerminalStatus(status, method, orderId)?.let { return it }
            when (status.status.lowercase()) {
                "failed", "rejected", "expired", "canceled", "cancelled" -> return null
            }
        }
        fetchOrderStatus(orderId)?.let { status ->
            return resolveTerminalStatus(status, method, orderId)
        }
        return null
    }

    private suspend fun fetchOrderStatus(orderId: String): MpOrderStatusResponseDto? {
        return try {
            requireMpOrderStatus(api.getMpOrder(orderId))
        } catch (_: IOException) {
            null
        } catch (e: ApiException) {
            if (e.httpCode >= 500) null else throw e
        }
    }

    private fun resolveTerminalStatus(
        status: MpOrderStatusResponseDto,
        method: PaymentMethodApi,
        orderId: String,
    ): PaymentResult? {
        return when (status.status.lowercase()) {
            "processed" -> toPaymentResult(status, method, orderId)
            "failed", "rejected" -> {
                val detail = status.statusDetail?.takeIf { it.isNotBlank() }
                throw ApiException(
                    402,
                    detail ?: "Pagamento recusado na maquininha.",
                    errorCode = status.status,
                )
            }
            "expired", "canceled", "cancelled" -> throw expiredOrCancelled(method)
            else -> null
        }
    }

    private fun toPaymentResult(
        status: MpOrderStatusResponseDto,
        method: PaymentMethodApi,
        orderId: String,
    ): PaymentResult {
        val acquirer = status.acquirer
            ?: throw IllegalStateException("Pagamento aprovado sem dados da adquirente.")
        return PaymentResult(
            method = method,
            nsu = acquirer.nsu,
            authorization = acquirer.authorization,
            brand = acquirer.brand.orEmpty(),
            transactionId = acquirer.transactionId,
            mpOrderId = orderId,
        )
    }

    private fun expiredOrCancelled(method: PaymentMethodApi): Exception {
        if (method == PaymentMethodApi.PIX) return PixExpiredException()
        return PaymentCancelledException()
    }

    private fun requireMpOrderStatus(response: Response<MpOrderStatusResponseDto>): MpOrderStatusResponseDto {
        if (response.isSuccessful) {
            return response.body()
                ?: throw IllegalStateException("Resposta vazia ao consultar cobrança na Point.")
        }
        throw ApiException(
            response.code(),
            response.errorBody()?.string() ?: "Erro ao consultar cobrança na Point.",
        )
    }

    companion object {
        const val POLL_INTERVAL_MS = 2_000L
        const val POLL_TIMEOUT_MS = 10 * 60 * 1_000L
        const val GRACE_PERIOD_MS = 3 * 60 * 1_000L
        const val RECOVER_TIMEOUT_MS = 5 * 60 * 1_000L
    }
}
