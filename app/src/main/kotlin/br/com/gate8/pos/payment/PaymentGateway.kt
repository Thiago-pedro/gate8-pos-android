package br.com.gate8.pos.payment

import br.com.gate8.pos.domain.model.PaymentMethodApi

data class PaymentResult(
    val method: PaymentMethodApi,
    val nsu: String,
    val authorization: String,
    val brand: String,
    val transactionId: String,
)

interface PaymentGateway {
    suspend fun charge(amount: Double, method: PaymentMethodApi): PaymentResult
}
