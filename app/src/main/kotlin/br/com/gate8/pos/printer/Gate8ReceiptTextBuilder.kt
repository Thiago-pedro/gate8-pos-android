package br.com.gate8.pos.printer

import br.com.gate8.pos.domain.model.CartLine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Gate8ReceiptTextBuilder {
    private val timeFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR"))

    fun saleReceipt(
        lines: List<CartLine>,
        total: Double,
        paymentLabel: String,
        nsu: String?,
        authorization: String?,
        isReprint: Boolean,
    ): List<String> = buildList {
        add("GATE8")
        add(if (isReprint) "*** REIMPRESSAO ***" else "COMPROVANTE")
        add(timeFormat.format(Date()))
        add("--------------------------------")
        lines.forEach { line ->
            add("${line.quantity}x ${line.description}")
            add("    R$ ${"%.2f".format(line.lineTotal)}")
        }
        add("--------------------------------")
        add("TOTAL R$ ${"%.2f".format(total)}")
        add("Pagamento: $paymentLabel")
        if (!nsu.isNullOrBlank()) add("NSU: $nsu")
        if (!authorization.isNullOrBlank()) add("Aut: $authorization")
        add("--------------------------------")
        add("suporte@gate8.club")
    }

    fun ticketBlock(code: String, holder: String?, description: String): List<String> = buildList {
        add("--- INGRESSO ---")
        add(description)
        holder?.takeIf { it.isNotBlank() }?.let { add("Titular: $it") }
        add("Codigo:")
        add(code)
        add("----------------")
    }

    fun reportSummary(payload: ReportPrintPayload): List<String> = buildList {
        add("GATE8 RELATORIO")
        payload.producerName?.let { add("Produtor: $it") }
        payload.deviceName?.let { add("Maquininha: $it") }
        add("Periodo: ${payload.periodLabel}")
        add("--------------------------------")
        add("Vendas: ${payload.saleCount}")
        add("Estornos: ${payload.voidCount}")
        add("Liquido: R$ ${"%.2f".format(payload.netTotal)}")
        add("Ticket medio: R$ ${"%.2f".format(payload.averageTicket)}")
        add("--------------------------------")
        add("POR PAGAMENTO")
        if (payload.byPaymentMethod.isEmpty()) {
            add("Nenhuma venda")
        } else {
            payload.byPaymentMethod.forEach { row ->
                add("${row.label}: ${row.count}x R$ ${"%.2f".format(row.total)}")
            }
        }
        add("================================")
    }

    fun cashierSummary(payload: CashierPrintPayload): List<String> = buildList {
        add("GATE8 CAIXA")
        payload.producerName?.let { add("Produtor: $it") }
        payload.deviceName?.let { add("Maquininha: $it") }
        payload.operatorName?.let { add("Operador: $it") }
        add("Abertura: ${payload.openedAtLabel}")
        payload.closedAtLabel?.let { add("Fechamento: $it") }
        add("--------------------------------")
        add("Troco inicial: R$ ${"%.2f".format(payload.openingBalance)}")
        add("Vendas cash: R$ ${"%.2f".format(payload.cashSales)}")
        add("Sangrias: R$ ${"%.2f".format(payload.withdrawals)}")
        add("Despesas: R$ ${"%.2f".format(payload.expenses)}")
        add("Esperado: R$ ${"%.2f".format(payload.expectedDrawer)}")
        payload.countedBalance?.let { add("Contado: R$ ${"%.2f".format(it)}") }
        payload.difference?.let { add("Diferenca: R$ ${"%.2f".format(it)}") }
        add("--------------------------------")
        add("Vendas: ${payload.saleCount}")
        add("Total: R$ ${"%.2f".format(payload.grandTotal)}")
        add("================================")
    }
}
