package br.com.gate8.pos.stone.payment

import br.com.gate8.pos.domain.model.PaymentMethodApi
import br.com.gate8.pos.payment.PaymentGateway
import br.com.gate8.pos.payment.PaymentResult
import br.com.gate8.pos.payment.VoidResult
import br.com.gate8.pos.stone.sdk.StoneSdkBridge
import java.util.UUID

class StonePaymentGateway(
    private val bridge: StoneSdkBridge,
) : PaymentGateway {

    override suspend fun charge(amount: Double, method: PaymentMethodApi): PaymentResult {
        if (method == PaymentMethodApi.CASH) {
            return PaymentResult(
                method = method,
                nsu = "",
                authorization = "",
                brand = "Dinheiro",
                transactionId = "cash-${UUID.randomUUID()}",
            )
        }
        return bridge.charge(amount, method)
    }

    override suspend fun voidTransaction(
        transactionId: String,
        nsu: String?,
        amount: Double,
        method: PaymentMethodApi,
    ): VoidResult {
        if (method == PaymentMethodApi.CASH) {
            return VoidResult(success = true, message = "Estorno em dinheiro registrado localmente")
        }
        return bridge.voidTransaction(transactionId, nsu, amount, method)
    }
}
