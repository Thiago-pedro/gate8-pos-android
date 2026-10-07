package br.com.gate8.pos.payment

import br.com.gate8.pos.domain.model.PaymentMethodApi

data class PaymentResult(
    val method: PaymentMethodApi,
    val nsu: String,
    val authorization: String,
    val brand: String,
    /** ID da ordem Cielo, usado no estorno. */
    val transactionId: String,
)

data class VoidResult(
    val success: Boolean,
    val message: String,
)

/** Lançada quando o operador cancela manualmente um pagamento em andamento. */
class PaymentCancelledException : Exception("Pagamento cancelado")

/** Lançada quando o QR Code Pix expira sem pagamento. */
class PixExpiredException : Exception("QR Code Pix expirado")

interface PaymentGateway {
    suspend fun charge(
        amount: Double,
        method: PaymentMethodApi,
        clientReference: String? = null,
    ): PaymentResult

    /** Estorno/cancelamento na Cielo. */
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
