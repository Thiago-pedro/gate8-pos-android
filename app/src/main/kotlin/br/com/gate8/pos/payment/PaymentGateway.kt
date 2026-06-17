package br.com.gate8.pos.payment

import br.com.gate8.pos.domain.model.PaymentMethodApi

data class PaymentResult(
    val method: PaymentMethodApi,
    val nsu: String,
    val authorization: String,
    val brand: String,
    val transactionId: String,
)

data class VoidResult(
    val success: Boolean,
    val message: String,
)

interface PaymentGateway {
    suspend fun charge(
        amount: Double,
        method: PaymentMethodApi,
        clientReference: String? = null,
    ): PaymentResult

    /** Estorno/cancelamento na adquirente (Stone ou mock). */
    suspend fun voidTransaction(
        transactionId: String,
        nsu: String?,
        amount: Double,
        method: PaymentMethodApi,
    ): VoidResult
}
