package br.com.gate8.pos.core.sale

import br.com.gate8.pos.data.prefs.LastSaleStore
import br.com.gate8.pos.domain.model.CartLine
import br.com.gate8.pos.domain.model.ItemType
import br.com.gate8.pos.domain.model.LastSaleLineRecord
import br.com.gate8.pos.domain.model.LastSaleRecord
import br.com.gate8.pos.domain.model.PaymentMethodApi
import br.com.gate8.pos.payment.PaymentGateway
import br.com.gate8.pos.payment.PaymentResult
import br.com.gate8.pos.printer.ReceiptPrinter

class SaleAdminService(
    private val lastSaleStore: LastSaleStore,
    private val paymentGateway: PaymentGateway,
    private val printer: ReceiptPrinter,
) {
    fun loadLastSale(): LastSaleRecord? = lastSaleStore.get()

    fun saveLastSale(record: LastSaleRecord) {
        lastSaleStore.save(record)
    }

    fun attachSaleIdIfMatches(clientReference: String, saleId: String) {
        val current = lastSaleStore.get() ?: return
        if (current.clientReference == clientReference && current.saleId.isNullOrBlank()) {
            lastSaleStore.save(current.copy(saleId = saleId))
        }
    }

    fun recordCheckout(
        saleId: String?,
        clientReference: String,
        cart: List<CartLine>,
        total: Double,
        method: PaymentMethodApi,
        payment: PaymentResult,
        ticketCodes: List<String> = emptyList(),
    ) {
        lastSaleStore.save(
            LastSaleRecord(
                saleId = saleId,
                clientReference = clientReference,
                total = total,
                paymentMethod = method.apiValue,
                paymentLabel = method.displayLabel(),
                nsu = payment.nsu,
                authorization = payment.authorization,
                transactionId = payment.transactionId,
                brand = payment.brand,
                lines = cart.map { line ->
                    LastSaleLineRecord(
                        description = line.description,
                        quantity = line.quantity,
                        unitPrice = line.unitPrice,
                    )
                },
                ticketCodes = ticketCodes,
            ),
        )
    }

    fun reprintLast(): Result<String> {
        val sale = lastSaleStore.get()
            ?: return Result.failure(IllegalStateException("Nenhuma venda para reimprimir"))
        val cartLines = sale.lines.map { line ->
            CartLine(
                itemType = ItemType.PRODUCT,
                description = line.description,
                quantity = line.quantity,
                unitPrice = line.unitPrice,
            )
        }
        printer.printReceipt(
            cartLines,
            sale.total,
            sale.paymentLabel,
            sale.nsu,
            sale.authorization,
            stoneTransactionId = sale.transactionId,
            isReprint = true,
        )
        sale.ticketCodes.forEach { code ->
            printer.printTicketQr(code, null, "Ingresso")
        }
        return Result.success("Comprovante reimpresso")
    }

    suspend fun voidLastSale(): Result<String> {
        val sale = lastSaleStore.get()
            ?: return Result.failure(IllegalStateException("Nenhuma venda recente"))
        if (sale.voided) {
            return Result.failure(IllegalStateException("Esta venda já foi estornada"))
        }
        if (sale.paymentMethod != PaymentMethodApi.CASH.apiValue) {
            val method = PaymentMethodApi.fromApiValue(sale.paymentMethod)
            val txId = sale.transactionId
                ?: return Result.failure(IllegalStateException("Sem ID de transação para estorno"))
            val void = paymentGateway.voidTransaction(txId, sale.nsu, sale.total, method)
            if (!void.success) {
                return Result.failure(IllegalStateException(void.message))
            }
        }
        lastSaleStore.markVoided()
        val label = PaymentMethodApi.fromApiValue(sale.paymentMethod).displayLabel()
        runCatching {
            val cartLines = sale.lines.map { line ->
                CartLine(
                    itemType = ItemType.PRODUCT,
                    description = line.description,
                    quantity = line.quantity,
                    unitPrice = line.unitPrice,
                )
            }
            printer.printVoidReceipt(
                lines = cartLines,
                total = sale.total,
                paymentLabel = label,
                nsu = sale.nsu,
                authorization = sale.authorization,
            )
        }
        return Result.success(
            "Estorno concluído · R$ ${"%.2f".format(sale.total)} · $label",
        )
    }
}
