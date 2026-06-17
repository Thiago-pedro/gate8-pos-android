package br.com.gate8.pos.stone.printer

import android.util.Log
import br.com.gate8.pos.domain.model.CartLine
import br.com.gate8.pos.printer.CashierPrintPayload
import br.com.gate8.pos.printer.Gate8ReceiptTextBuilder
import br.com.gate8.pos.printer.ReceiptPrinter
import br.com.gate8.pos.printer.ReportPrintPayload
import br.com.gate8.pos.stone.StoneActivityHolder

class StoneReceiptPrinterAdapter(
    private val activityHolder: StoneActivityHolder,
    private val posPrinter: StonePosPrinter,
) : ReceiptPrinter {

    override fun printReceipt(
        lines: List<CartLine>,
        total: Double,
        paymentLabel: String,
        nsu: String?,
        authorization: String?,
        stoneTransactionId: String?,
        isReprint: Boolean,
    ) {
        val activity = activityHolder.runCatching { requireActivity() }.getOrNull()
        if (activity == null) {
            Log.w(TAG, "Activity indisponível para impressão")
            return
        }
        if (!posPrinter.isAvailable) {
            Log.i(TAG, "SDK Stone não vinculado — impressão ignorada")
            return
        }

        val isCardLike = paymentLabel.lowercase() in CARD_PAYMENT_LABELS &&
            (!stoneTransactionId.isNullOrBlank() || !nsu.isNullOrBlank())

        if (isCardLike) {
            if (isReprint && !stoneTransactionId.isNullOrBlank()) {
                posPrinter.reprintCardReceipt(activity, stoneTransactionId, merchantCopy = true)
                posPrinter.reprintCardReceipt(activity, stoneTransactionId, merchantCopy = false)
            } else {
                posPrinter.printCardReceipt(activity, stoneTransactionId, nsu, merchantCopy = true)
                posPrinter.printCardReceipt(activity, stoneTransactionId, nsu, merchantCopy = false)
            }
        }

        val textLines = Gate8ReceiptTextBuilder.saleReceipt(
            lines = lines,
            total = total,
            paymentLabel = paymentLabel,
            nsu = nsu,
            authorization = authorization,
            isReprint = isReprint,
        )
        posPrinter.printLines(activity, textLines)
    }

    override fun printTicketQr(code: String, holder: String?, description: String) {
        val activity = activityHolder.runCatching { requireActivity() }.getOrNull() ?: return
        if (!posPrinter.isAvailable) return
        posPrinter.printLines(
            activity,
            Gate8ReceiptTextBuilder.ticketBlock(code, holder, description),
        )
    }

    override fun printReportSummary(payload: ReportPrintPayload) {
        val activity = activityHolder.runCatching { requireActivity() }.getOrNull() ?: return
        if (!posPrinter.isAvailable) return
        posPrinter.printLines(activity, Gate8ReceiptTextBuilder.reportSummary(payload))
    }

    override fun printCashierSummary(payload: CashierPrintPayload) {
        val activity = activityHolder.runCatching { requireActivity() }.getOrNull() ?: return
        if (!posPrinter.isAvailable) return
        posPrinter.printLines(activity, Gate8ReceiptTextBuilder.cashierSummary(payload))
    }

    companion object {
        private const val TAG = "Gate8StonePrinter"
        private val CARD_PAYMENT_LABELS = setOf("credit", "debit", "pix")
    }
}
