package br.com.gate8.pos.payment

import br.com.gate8.pos.domain.model.PaymentMethodApi

/** Cobra na Cielo Smart. */
suspend fun PaymentGateway.chargeResilient(
    amount: Double,
    method: PaymentMethodApi,
    clientReference: String,
): PaymentResult = charge(amount, method, clientReference)
