package br.com.gate8.pos.printer

import br.com.gate8.pos.domain.model.CartLine

interface ReceiptPrinter {
    fun printReceipt(
        lines: List<CartLine>,
        total: Double,
        paymentLabel: String,
        nsu: String?,
        authorization: String?,
    )

    fun printTicketQr(code: String, holder: String?, description: String)
}

class NoOpReceiptPrinter : ReceiptPrinter {
    override fun printReceipt(
        lines: List<CartLine>,
        total: Double,
        paymentLabel: String,
        nsu: String?,
        authorization: String?,
    ) = Unit

    override fun printTicketQr(code: String, holder: String?, description: String) = Unit
}
