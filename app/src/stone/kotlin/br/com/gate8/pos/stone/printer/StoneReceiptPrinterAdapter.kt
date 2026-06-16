package br.com.gate8.pos.stone.printer

import android.util.Log
import br.com.gate8.pos.domain.model.CartLine
import br.com.gate8.pos.printer.CashierPrintPayload
import br.com.gate8.pos.printer.ReceiptPrinter
import br.com.gate8.pos.printer.ReportPrintPayload
import br.com.gate8.pos.stone.sdk.StoneSdkBridge

/**
 * Impressão customizada Gate8 — usar PosPrintProvider quando SDK estiver vinculado.
 * Comprovantes de transação: PosPrintReceiptProvider (após pagamento Stone).
 */
class StoneReceiptPrinterAdapter(
    private val bridge: StoneSdkBridge,
) : ReceiptPrinter {

    override fun printReceipt(
        lines: List<CartLine>,
        total: Double,
        paymentLabel: String,
        nsu: String?,
        authorization: String?,
    ) {
        if (!bridge.isLinked) {
            Log.i(TAG, "printReceipt (SDK não vinculado) total=$total nsu=$nsu")
            return
        }
        // TODO: PosPrintProvider — texto compacto (boas práticas bobina)
        Log.i(TAG, "printReceipt pendente PosPrintProvider total=$total")
    }

    override fun printTicketQr(code: String, holder: String?, description: String) {
        Log.i(TAG, "printTicketQr code=${code.take(8)}…")
    }

    override fun printReportSummary(payload: ReportPrintPayload) {
        Log.i(TAG, "printReportSummary ${payload.periodLabel}")
    }

    override fun printCashierSummary(payload: CashierPrintPayload) {
        Log.i(TAG, "printCashierSummary operador=${payload.operatorName}")
    }

    companion object {
        private const val TAG = "Gate8StonePrinter"
    }
}
