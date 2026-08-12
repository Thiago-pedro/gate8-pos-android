package br.com.gate8.pos.cielo.printer

import android.util.Log
import br.com.gate8.pos.domain.model.CartLine
import br.com.gate8.pos.printer.CashierPrintPayload
import br.com.gate8.pos.printer.ReceiptPrinter
import br.com.gate8.pos.printer.ReportPrintPayload
import br.com.gate8.pos.printer.TicketPrintPayload

/** Impressão via Logcat até integrar `lio://print` no DX8000. */
class CieloReceiptPrinter : ReceiptPrinter {
    override fun printReceipt(
        lines: List<CartLine>,
        total: Double,
        paymentLabel: String,
        nsu: String?,
        authorization: String?,
        acquirerTransactionId: String?,
        isReprint: Boolean,
        saleDateMillis: Long?,
    ) {
        val sb = StringBuilder("=== GATE8 CUPOM (CIELO) ===\n")
        lines.forEach { l ->
            sb.append("${l.quantity}x ${l.description} R$ ${"%.2f".format(l.lineTotal)}\n")
        }
        sb.append("TOTAL R$ ${"%.2f".format(total)}\n")
        sb.append("Pagamento: $paymentLabel\n")
        if (isReprint) sb.append("*** REIMPRESSAO ***\n")
        if (nsu != null) sb.append("NSU: $nsu  Auth: $authorization\n")
        acquirerTransactionId?.let { sb.append("Order: $it\n") }
        Log.i(TAG, sb.toString())
    }

    override fun printVoidReceipt(
        lines: List<CartLine>,
        total: Double,
        paymentLabel: String,
        nsu: String?,
        authorization: String?,
    ) {
        Log.i(TAG, "=== GATE8 ESTORNO (CIELO) === total=$total method=$paymentLabel")
    }

    override fun printTicket(payload: TicketPrintPayload) {
        Log.i(TAG, "=== GATE8 INGRESSO (CIELO) === ${payload.eventName} code=${payload.validationCode}")
    }

    override fun printReportSummary(payload: ReportPrintPayload) {
        Log.i(TAG, "=== GATE8 RELATORIO (CIELO) === liquido=${payload.netTotal}")
    }

    override fun printCashierSummary(payload: CashierPrintPayload) {
        Log.i(TAG, "=== GATE8 CAIXA (CIELO) === esperado=${payload.expectedDrawer}")
    }

    override fun printCardCopy(
        transactionId: String?,
        nsu: String?,
        merchantCopy: Boolean,
        isReprint: Boolean,
    ) {
        val via = if (merchantCopy) "LOJISTA" else "CLIENTE"
        Log.i(TAG, "=== GATE8 VIA $via (CIELO) === tx=$transactionId nsu=$nsu")
    }

    override fun printSaleSummary(
        lines: List<CartLine>,
        total: Double,
        paymentLabel: String,
        nsu: String?,
        authorization: String?,
        isReprint: Boolean,
    ) {
        printReceipt(lines, total, paymentLabel, nsu, authorization, null, isReprint, null)
    }

    override fun printConvenienceTickets(
        lines: List<CartLine>,
        terminalName: String,
        authorization: String?,
    ) {
        Log.i(TAG, "=== GATE8 FICHA (CIELO) === terminal=$terminalName itens=${lines.size}")
    }

    companion object {
        private const val TAG = "Gate8CieloPrint"
    }
}
