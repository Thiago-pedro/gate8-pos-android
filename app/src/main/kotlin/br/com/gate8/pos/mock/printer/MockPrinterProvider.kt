package br.com.gate8.pos.mock.printer

import android.util.Log
import br.com.gate8.pos.domain.model.CartLine
import br.com.gate8.pos.printer.CashierPrintPayload
import br.com.gate8.pos.printer.ReportPrintPayload
import br.com.gate8.pos.printer.ReceiptPrinter

class MockPrinterProvider : ReceiptPrinter {
    override fun printReceipt(
        lines: List<CartLine>,
        total: Double,
        paymentLabel: String,
        nsu: String?,
        authorization: String?,
        stoneTransactionId: String?,
        isReprint: Boolean,
    ) {
        val sb = StringBuilder("=== GATE8 CUPOM (MOCK) ===\n")
        lines.forEach { l ->
            sb.append("${l.quantity}x ${l.description} R$ ${"%.2f".format(l.lineTotal)}\n")
        }
        sb.append("TOTAL R$ ${"%.2f".format(total)}\n")
        sb.append("Pagamento: $paymentLabel\n")
        if (isReprint) sb.append("*** REIMPRESSAO ***\n")
        if (nsu != null) sb.append("NSU: $nsu  Auth: $authorization\n")
        Log.i(TAG, sb.toString())
    }

    override fun printVoidReceipt(
        lines: List<CartLine>,
        total: Double,
        paymentLabel: String,
        nsu: String?,
        authorization: String?,
    ) {
        val sb = StringBuilder("=== GATE8 ESTORNO (MOCK) ===\n")
        lines.forEach { l ->
            sb.append("${l.quantity}x ${l.description} R$ ${"%.2f".format(l.lineTotal)}\n")
        }
        sb.append("VALOR ESTORNADO R$ ${"%.2f".format(total)}\n")
        sb.append("Pagamento: $paymentLabel\n")
        if (nsu != null) sb.append("NSU: $nsu  Auth: $authorization\n")
        Log.i(TAG, sb.toString())
    }

    override fun printTicketQr(code: String, holder: String?, description: String) {
        Log.i(TAG, "QR INGRESSO (conteúdo=$code) $description holder=$holder")
    }

    override fun printReportSummary(payload: ReportPrintPayload) {
        val sb = StringBuilder("=== GATE8 RELATORIO (MOCK) ===\n")
        payload.producerName?.let { sb.append("Produtor: $it\n") }
        payload.deviceName?.let { sb.append("Maquininha: $it\n") }
        sb.append("Periodo: ${payload.periodLabel}\n")
        sb.append("--------------------------------\n")
        sb.append("RESUMO GERAL\n")
        sb.append("Vendas: ${payload.saleCount}\n")
        sb.append("Estornos: ${payload.voidCount}\n")
        sb.append("Bruto: R$ ${"%.2f".format(payload.grossTotal)}\n")
        sb.append("Estornos R$: R$ ${"%.2f".format(payload.voidTotal)}\n")
        sb.append("Liquido: R$ ${"%.2f".format(payload.netTotal)}\n")
        sb.append("Ticket medio: R$ ${"%.2f".format(payload.averageTicket)}\n")
        sb.append("--------------------------------\n")
        sb.append("POR PAGAMENTO\n")
        if (payload.byPaymentMethod.isEmpty()) {
            sb.append("Nenhuma venda\n")
        } else {
            payload.byPaymentMethod.forEach { row ->
                sb.append("${row.label}: ${row.count}x R$ ${"%.2f".format(row.total)}\n")
            }
        }
        sb.append("--------------------------------\n")
        sb.append("POR BANDEIRA (CARTAO)\n")
        if (payload.byBrand.isEmpty()) {
            sb.append("Sem cartao no periodo\n")
        } else {
            payload.byBrand.forEach { row ->
                sb.append("${row.label}: ${row.count}x R$ ${"%.2f".format(row.total)}\n")
            }
        }
        sb.append("--------------------------------\n")
        sb.append("MAIS VENDIDOS\n")
        if (payload.topItems.isEmpty()) {
            sb.append("Nenhum item\n")
        } else {
            payload.topItems.forEach { item ->
                sb.append("${item.quantity}x ${item.name}: R$ ${"%.2f".format(item.total)}\n")
            }
        }
        sb.append("================================\n")
        Log.i(TAG, sb.toString())
    }

    override fun printCashierSummary(payload: CashierPrintPayload) {
        val sb = StringBuilder("=== GATE8 CAIXA (MOCK) ===\n")
        payload.producerName?.let { sb.append("Produtor: $it\n") }
        payload.deviceName?.let { sb.append("Maquininha: $it\n") }
        payload.operatorName?.let { sb.append("Operador: $it\n") }
        sb.append("Abertura: ${payload.openedAtLabel}\n")
        payload.closedAtLabel?.let { sb.append("Fechamento: $it\n") }
        sb.append("--------------------------------\n")
        sb.append("Troco inicial: R$ ${"%.2f".format(payload.openingBalance)}\n")
        sb.append("Vendas dinheiro: R$ ${"%.2f".format(payload.cashSales)}\n")
        sb.append("Sangrias: R$ ${"%.2f".format(payload.withdrawals)}\n")
        sb.append("Despesas: R$ ${"%.2f".format(payload.expenses)}\n")
        sb.append("Esperado gaveta: R$ ${"%.2f".format(payload.expectedDrawer)}\n")
        payload.countedBalance?.let { sb.append("Contado: R$ ${"%.2f".format(it)}\n") }
        payload.difference?.let { sb.append("Diferenca: R$ ${"%.2f".format(it)}\n") }
        sb.append("--------------------------------\n")
        sb.append("Vendas turno: ${payload.saleCount}\n")
        sb.append("Total vendido: R$ ${"%.2f".format(payload.grandTotal)}\n")
        sb.append("POR PAGAMENTO\n")
        payload.byPaymentMethod.forEach { row ->
            sb.append("${row.label}: ${row.count}x R$ ${"%.2f".format(row.total)}\n")
        }
        if (payload.movements.isNotEmpty()) {
            sb.append("--------------------------------\n")
            sb.append("MOVIMENTOS\n")
            payload.movements.forEach { m ->
                sb.append("${m.typeLabel} R$ ${"%.2f".format(m.amount)} — ${m.description ?: ""}\n")
            }
        }
        sb.append("================================\n")
        Log.i(TAG, sb.toString())
    }

    override fun printCardCopy(
        transactionId: String?,
        nsu: String?,
        merchantCopy: Boolean,
        isReprint: Boolean,
    ) {
        val via = if (merchantCopy) "LOJISTA" else "CLIENTE"
        Log.i(TAG, "=== GATE8 VIA $via (MOCK) === itk=$transactionId nsu=$nsu reprint=$isReprint")
    }

    override fun printSaleSummary(
        lines: List<CartLine>,
        total: Double,
        paymentLabel: String,
        nsu: String?,
        authorization: String?,
        isReprint: Boolean,
    ) {
        val sb = StringBuilder("=== GATE8 COMPROVANTE (MOCK) ===\n")
        lines.forEach { l ->
            sb.append("${l.quantity}x ${l.description} R$ ${"%.2f".format(l.lineTotal)}\n")
        }
        sb.append("TOTAL R$ ${"%.2f".format(total)}\n")
        sb.append("Pagamento: $paymentLabel\n")
        if (isReprint) sb.append("*** REIMPRESSAO ***\n")
        if (nsu != null) sb.append("NSU: $nsu  Auth: $authorization\n")
        Log.i(TAG, sb.toString())
    }

    override fun printConvenienceTickets(
        lines: List<CartLine>,
        terminalName: String,
        authorization: String?,
    ) {
        val autLine = if (!authorization.isNullOrBlank()) "\nAUT.: $authorization" else ""
        lines.forEach { line ->
            repeat(line.quantity.coerceAtLeast(1)) {
                Log.i(
                    TAG,
                    "=== GATE8 FICHA (MOCK) ===\n$terminalName\n${line.description.uppercase()}\n" +
                        "R$ ${"%.2f".format(line.unitPrice)}$autLine\n..........................",
                )
            }
        }
    }

    companion object {
        private const val TAG = "Gate8PrinterMock"
    }
}
