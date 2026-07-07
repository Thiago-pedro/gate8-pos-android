package br.com.gate8.pos.payment

import br.com.gate8.pos.data.remote.api.PosApiService
import br.com.gate8.pos.data.remote.dto.ReconcileMpOrderRequestDto
import br.com.gate8.pos.data.remote.dto.ReconcileMpOrderResponseDto
import br.com.gate8.pos.domain.model.PaymentMethodApi
import br.com.gate8.pos.domain.model.SaleSuccess
import br.com.gate8.pos.domain.model.SaleTicketGroup

/**
 * Tenta registrar venda no Gate8 quando o pagamento foi aprovado na Point
 * mas o app perdeu a conexão antes de concluir o fluxo local.
 */
class MpOrderReconciliation(
    private val api: PosApiService,
) {

    suspend fun reconcileAfterTimeout(
        mpOrderId: String,
        method: PaymentMethodApi,
    ): RecoveredCheckout? {
        val response = api.reconcileMpOrder(mpOrderId, ReconcileMpOrderRequestDto())
        if (!response.isSuccessful) return null
        val body = response.body() ?: return null
        val saleId = body.saleId?.takeIf { it.isNotBlank() } ?: return null
        val pay = paymentResultFromOrder(mpOrderId, method) ?: PaymentResult(
            method = method,
            nsu = "",
            authorization = "",
            brand = "",
            transactionId = mpOrderId,
            mpOrderId = mpOrderId,
        )
        return RecoveredCheckout(
            saleSuccess = body.toSaleSuccess(saleId),
            payment = pay,
        )
    }

    private suspend fun paymentResultFromOrder(
        mpOrderId: String,
        method: PaymentMethodApi,
    ): PaymentResult? {
        val status = api.getMpOrder(mpOrderId).body() ?: return null
        val acquirer = status.acquirer ?: return null
        return PaymentResult(
            method = method,
            nsu = acquirer.nsu,
            authorization = acquirer.authorization,
            brand = acquirer.brand.orEmpty(),
            transactionId = acquirer.transactionId,
            mpOrderId = mpOrderId,
        )
    }

    private fun ReconcileMpOrderResponseDto.toSaleSuccess(saleId: String): SaleSuccess {
        val groups = tickets.map { g ->
            SaleTicketGroup(itemIndex = g.itemIndex, codes = g.tickets.map { it.code })
        }
        return SaleSuccess(
            saleId = saleId,
            duplicated = duplicated,
            ticketGroups = groups,
            purchaseCode = purchaseCode,
        )
    }

    data class RecoveredCheckout(
        val saleSuccess: SaleSuccess,
        val payment: PaymentResult,
    )
}

suspend fun tryReconcileAfterPaymentFailure(
    reconciliation: MpOrderReconciliation,
    error: Throwable?,
    method: PaymentMethodApi,
): MpOrderReconciliation.RecoveredCheckout? {
    val orderId = (error as? PaymentTimedOutException)?.mpOrderId ?: return null
    return runCatching { reconciliation.reconcileAfterTimeout(orderId, method) }.getOrNull()
}
