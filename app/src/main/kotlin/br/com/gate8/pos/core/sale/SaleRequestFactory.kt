package br.com.gate8.pos.core.sale

import br.com.gate8.pos.data.remote.dto.AcquirerPaymentDto
import br.com.gate8.pos.data.remote.dto.CreateSaleRequestDto
import br.com.gate8.pos.data.remote.dto.SaleItemDto
import br.com.gate8.pos.domain.model.CartLine
import br.com.gate8.pos.domain.model.PaymentMethodApi
import br.com.gate8.pos.payment.CardBrandNormalizer
import br.com.gate8.pos.payment.PaymentResult

object SaleRequestFactory {

    fun create(
        clientReference: String,
        operatorName: String,
        method: PaymentMethodApi,
        total: Double,
        payment: PaymentResult,
        cart: List<CartLine>,
    ): CreateSaleRequestDto {
        val acquirer = if (method == PaymentMethodApi.CASH || method == PaymentMethodApi.CASHLESS) {
            null
        } else {
            AcquirerPaymentDto(
                nsu = payment.nsu,
                authorization = payment.authorization,
                brand = CardBrandNormalizer.normalize(payment.brand),
                transactionId = payment.transactionId,
            )
        }
        return CreateSaleRequestDto(
            clientReference = clientReference,
            operatorName = operatorName,
            // Lovable hoje aceita credit|debit|pix|cash|other — cashless vai como other.
            paymentMethod = when (method) {
                PaymentMethodApi.CASHLESS -> "other"
                else -> method.apiValue
            },
            totalAmount = total,
            acquirer = acquirer,
            // Lovable ainda agrega bandeira/NSU em colunas `stone_*`.
            stone = acquirer,
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
}
