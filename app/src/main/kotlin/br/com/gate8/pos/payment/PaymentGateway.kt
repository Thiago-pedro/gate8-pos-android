package br.com.gate8.pos.payment

import br.com.gate8.pos.data.remote.dto.MpSaleDraftDto
import br.com.gate8.pos.domain.model.PaymentMethodApi

data class PaymentResult(
    val method: PaymentMethodApi,
    val nsu: String,
    val authorization: String,
    val brand: String,
    /** ID usado no estorno (payment id ou order id conforme adquirente). */
    val transactionId: String,
    /** Order MP (`ORD…`) quando disponível — usada para reconciliação. */
    val mpOrderId: String? = null,
)

data class VoidResult(
    val success: Boolean,
    val message: String,
)

/** Lançada quando o operador cancela manualmente um pagamento em andamento. */
class PaymentCancelledException : Exception("Pagamento cancelado")

/** Lançada quando o QR Code Pix expira sem pagamento. */
class PixExpiredException : Exception("QR Code Pix expirado")

/**
 * Pagamento pode ter sido concluído na Point após falha de rede/timeout no app.
 * Use [recoverOrder] com [mpOrderId] antes de desistir.
 */
class PaymentTimedOutException(
    val mpOrderId: String,
    cause: Throwable? = null,
) : Exception("Tempo esgotado aguardando confirmação na maquininha.", cause)

interface PaymentGateway {
    suspend fun charge(
        amount: Double,
        method: PaymentMethodApi,
        clientReference: String? = null,
        saleDraft: MpSaleDraftDto? = null,
    ): PaymentResult

    /**
     * Tenta recuperar pagamento já cobrado na Point quando [charge] falhou por timeout/rede.
     * Retorna null se a order ainda não foi processada ou foi cancelada/recusada.
     */
    suspend fun recoverOrder(
        mpOrderId: String,
        method: PaymentMethodApi,
    ): PaymentResult? = null

    /** Estorno/cancelamento na adquirente (Cielo / Mercado Pago / mock). */
    suspend fun voidTransaction(
        transactionId: String,
        nsu: String?,
        amount: Double,
        method: PaymentMethodApi,
        authorization: String? = null,
    ): VoidResult

    /**
     * Aborta o pagamento em andamento (cartão/Pix) na maquininha.
     * Faz a [charge] em curso lançar [PaymentCancelledException]. No-op se nada está rodando.
     */
    fun cancelCurrentPayment() {}
}
