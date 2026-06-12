package br.com.gate8.pos.stone.payment

import br.com.gate8.pos.domain.model.PaymentMethodApi
import br.com.gate8.pos.payment.PaymentGateway
import br.com.gate8.pos.payment.PaymentResult
import br.com.gate8.pos.payment.VoidResult

/** Placeholder até integrar co.stone.posmobile.sdk no flavor stone. */
class StonePaymentGatewayStub : PaymentGateway {
    override suspend fun charge(amount: Double, method: PaymentMethodApi): PaymentResult {
        throw UnsupportedOperationException("Integrar SDK Stone no flavor stone")
    }

    override suspend fun voidTransaction(
        transactionId: String,
        nsu: String?,
        amount: Double,
        method: PaymentMethodApi,
    ): VoidResult {
        throw UnsupportedOperationException("Integrar estorno SDK Stone no flavor stone")
    }
}
