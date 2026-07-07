package br.com.gate8.pos.mock.payment

import br.com.gate8.pos.data.remote.dto.MpSaleDraftDto
import br.com.gate8.pos.domain.model.PaymentMethodApi
import br.com.gate8.pos.payment.PaymentCancelledException
import br.com.gate8.pos.payment.PaymentGateway
import br.com.gate8.pos.payment.PaymentResult
import br.com.gate8.pos.payment.VoidResult
import kotlinx.coroutines.delay
import java.util.UUID
import kotlin.random.Random

class MockPaymentGateway : PaymentGateway {
    @Volatile private var cancelRequested = false

    override fun cancelCurrentPayment() {
        cancelRequested = true
    }

    override suspend fun charge(
        amount: Double,
        method: PaymentMethodApi,
        clientReference: String?,
        saleDraft: MpSaleDraftDto?,
    ): PaymentResult {
        cancelRequested = false
        repeat(16) {
            delay(50)
            if (cancelRequested) {
                cancelRequested = false
                throw PaymentCancelledException()
            }
        }
        val nsu = Random.nextInt(100000, 999999).toString()
        val auth = Random.nextInt(100000, 999999).toString()
        return PaymentResult(
            method = method,
            nsu = nsu,
            authorization = auth,
            brand = when (method) {
                PaymentMethodApi.CREDIT -> "Visa"
                PaymentMethodApi.DEBIT -> "Mastercard"
                PaymentMethodApi.PIX -> "Pix"
                else -> "MOCK"
            },
            transactionId = clientReference ?: "mock-mp-${UUID.randomUUID()}",
        )
    }

    override suspend fun voidTransaction(
        transactionId: String,
        nsu: String?,
        amount: Double,
        method: PaymentMethodApi,
    ): VoidResult {
        delay(600)
        return VoidResult(
            success = true,
            message = "Estorno mock OK · NSU $nsu · R$ ${"%.2f".format(amount)} ($method)",
        )
    }
}
