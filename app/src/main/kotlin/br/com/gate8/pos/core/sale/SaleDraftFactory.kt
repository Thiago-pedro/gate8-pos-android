package br.com.gate8.pos.core.sale

import br.com.gate8.pos.data.remote.dto.MpSaleDraftDto
import br.com.gate8.pos.data.remote.dto.SaleItemDto
import br.com.gate8.pos.domain.model.CartLine
import br.com.gate8.pos.domain.model.PaymentMethodApi

object SaleDraftFactory {

    fun mpSaleDraft(
        cart: List<CartLine>,
        total: Double,
        method: PaymentMethodApi,
        operatorName: String,
    ): MpSaleDraftDto = MpSaleDraftDto(
        operatorName = operatorName,
        paymentMethod = method.apiValue,
        totalAmount = total,
        items = cart.map { line ->
            SaleItemDto(
                itemType = line.itemType.apiValue,
                productId = line.productId,
                batchId = line.batchId,
                eventId = line.eventId,
                holderName = line.holderName,
                holderEmail = line.holderEmail,
                description = line.description,
                quantity = line.quantity,
                unitPrice = line.unitPrice,
            )
        },
    )
}
