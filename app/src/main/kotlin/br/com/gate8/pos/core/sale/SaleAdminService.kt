package br.com.gate8.pos.core.sale

import br.com.gate8.pos.data.prefs.LastSaleStore
import br.com.gate8.pos.domain.model.CartLine
import br.com.gate8.pos.domain.model.ItemType
import br.com.gate8.pos.domain.model.LastSaleLineRecord
import br.com.gate8.pos.domain.model.LastSaleRecord
import br.com.gate8.pos.domain.model.PaymentMethodApi
import br.com.gate8.pos.data.repository.SaleRepository
import br.com.gate8.pos.payment.PaymentGateway
import br.com.gate8.pos.payment.PaymentResult
import br.com.gate8.pos.printer.ReceiptPrinter
import br.com.gate8.pos.printer.TicketPrintPayload

class SaleAdminService(
    private val lastSaleStore: LastSaleStore,
    private val paymentGateway: PaymentGateway,
    private val printer: ReceiptPrinter,
    private val saleRepository: SaleRepository,
) {
    fun loadLastSale(): LastSaleRecord? = lastSaleStore.get()

    fun loadRecentSales(): List<LastSaleRecord> = lastSaleStore.list()

    fun saveLastSale(record: LastSaleRecord) {
        lastSaleStore.save(record)
    }

    fun attachSaleIdIfMatches(clientReference: String, saleId: String) {
        lastSaleStore.updateSaleId(clientReference, saleId)
    }

    fun recordCheckout(
        saleId: String?,
        clientReference: String,
        cart: List<CartLine>,
        total: Double,
        method: PaymentMethodApi,
        payment: PaymentResult,
        ticketCodes: List<String> = emptyList(),
        cashlessUid: String? = null,
        cashlessCpfMasked: String? = null,
        cashlessBalanceAfter: Double? = null,
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
                cashlessUid = cashlessUid,
                cashlessCpfMasked = cashlessCpfMasked,
                cashlessBalanceAfter = cashlessBalanceAfter,
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
            acquirerTransactionId = sale.transactionId,
            isReprint = true,
            saleDateMillis = sale.createdAt,
            cashlessUid = sale.cashlessUid,
            cashlessCpfMasked = sale.cashlessCpfMasked,
            cashlessBalanceAfter = sale.cashlessBalanceAfter,
        )
        val ticketLine = sale.lines.firstOrNull()
        sale.ticketCodes.forEach { code ->
            printer.printTicket(
                TicketPrintPayload(
                    eventName = ticketLine?.description ?: "Ingresso",
                    batchName = "",
                    holderName = null,
                    price = ticketLine?.unitPrice ?: 0.0,
                    validationCode = code,
                ),
            )
        }
        return Result.success("Comprovante reimpresso")
    }

    suspend fun voidLastSale(): Result<String> {
        val last = lastSaleStore.get()
            ?: return Result.failure(IllegalStateException("Nenhuma venda recente"))
        return voidSale(last.clientReference)
    }

    /** Estorna uma venda específica da lista de vendas recentes. */
    suspend fun voidSale(clientReference: String): Result<String> {
        val sale = lastSaleStore.find(clientReference)
            ?: return Result.failure(IllegalStateException("Venda não encontrada"))
        if (sale.voided) {
            return Result.failure(IllegalStateException("Esta venda já foi estornada"))
        }
        if (sale.paymentMethod != PaymentMethodApi.CASH.apiValue) {
            val method = PaymentMethodApi.fromApiValue(sale.paymentMethod)
            val txId = sale.transactionId
                ?: return Result.failure(IllegalStateException("Sem ID de transação para estorno"))
            val void = paymentGateway.voidTransaction(
                txId,
                sale.nsu,
                sale.total,
                method,
                authorization = sale.authorization,
            )
            if (!void.success) {
                return Result.failure(IllegalStateException(void.message))
            }
        }
        lastSaleStore.markVoided(sale.clientReference)
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
        // Avisa o backend (painel Lovable) para registrar o estorno (status -> voided).
        // O pagamento já foi revertido e o comprovante impresso; uma falha aqui não
        // desfaz o estorno, apenas adia a atualização do painel.
        val syncNote = syncVoidToBackend(sale)
        return Result.success(
            "Estorno concluído · R$ ${"%.2f".format(sale.total)} · $label$syncNote",
        )
    }

    private suspend fun syncVoidToBackend(sale: LastSaleRecord): String {
        val saleId = sale.saleId
        if (saleId.isNullOrBlank()) {
            return " · painel não atualizado (venda sem ID; estorno só na maquininha)"
        }
        return runCatching {
            saleRepository.voidSale(
                saleId = saleId,
                clientReference = sale.clientReference,
                reason = "Estorno na maquininha",
            )
        }.fold(
            onSuccess = { "" },
            onFailure = { e ->
                android.util.Log.w("Gate8Void", "Falha ao sincronizar estorno (saleId=$saleId)", e)
                " · painel não atualizado (${e.message ?: "erro de conexão"})"
            },
        )
    }
}
