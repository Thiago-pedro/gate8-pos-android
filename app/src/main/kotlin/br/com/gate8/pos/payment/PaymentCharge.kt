package br.com.gate8.pos.payment

import br.com.gate8.pos.data.remote.dto.MpSaleDraftDto
import br.com.gate8.pos.domain.model.PaymentMethodApi

/**
 * Cobra na adquirente e, se o app perder a conexão enquanto o cliente paga na Point,
 * tenta reconciliar a order antes de falhar de vez.
 */
suspend fun PaymentGateway.chargeResilient(
    amount: Double,
    method: PaymentMethodApi,
    clientReference: String,
    saleDraft: MpSaleDraftDto? = null,
): PaymentResult {
    return runCatching { charge(amount, method, clientReference, saleDraft) }
        .recoverCatching { error ->
            val orderId = (error as? PaymentTimedOutException)?.mpOrderId ?: throw error
            recoverOrder(orderId, method) ?: throw error
        }
        .getOrThrow()
}
