package br.com.gate8.pos.stone.payment

import br.com.gate8.pos.domain.model.PaymentMethodApi
import br.com.gate8.pos.payment.PaymentGateway
import br.com.gate8.pos.payment.PaymentResult

/** Placeholder até integrar co.stone.posmobile.sdk no flavor stone. */
class StonePaymentGatewayStub : PaymentGateway {
    override suspend fun charge(amount: Double, method: PaymentMethodApi): PaymentResult {
        throw UnsupportedOperationException("Integrar SDK Stone no flavor stone")
    }
}
