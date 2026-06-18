package br.com.gate8.pos.printer

import br.com.gate8.pos.domain.model.CartLine

interface ReceiptPrinter {
    fun printReceipt(
        lines: List<CartLine>,
        total: Double,
        paymentLabel: String,
        nsu: String?,
        authorization: String?,
        stoneTransactionId: String? = null,
        isReprint: Boolean = false,
    )

    fun printVoidReceipt(
        lines: List<CartLine>,
        total: Double,
        paymentLabel: String,
        nsu: String?,
        authorization: String?,
    )

    fun printTicketQr(code: String, holder: String?, description: String)

    fun printReportSummary(payload: ReportPrintPayload)

    fun printCashierSummary(payload: CashierPrintPayload)
}

class NoOpReceiptPrinter : ReceiptPrinter {
    override fun printReceipt(
        lines: List<CartLine>,
        total: Double,
        paymentLabel: String,
        nsu: String?,
        authorization: String?,
        stoneTransactionId: String?,
        isReprint: Boolean,
    ) = Unit

    override fun printVoidReceipt(
        lines: List<CartLine>,
        total: Double,
        paymentLabel: String,
        nsu: String?,
        authorization: String?,
    ) = Unit

    override fun printTicketQr(code: String, holder: String?, description: String) = Unit

    override fun printReportSummary(payload: ReportPrintPayload) = Unit

    override fun printCashierSummary(payload: CashierPrintPayload) = Unit
}
