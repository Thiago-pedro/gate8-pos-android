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

    /** Imprime um ingresso completo (logo, evento/lote/data/local, portador, preço, QR e código). */
    fun printTicket(payload: TicketPrintPayload)

    fun printReportSummary(payload: ReportPrintPayload)

    fun printCashierSummary(payload: CashierPrintPayload)

    /**
     * Imprime uma única via do comprovante de cartão/Pix da Stone (lojista ou cliente).
     * Permite controlar a ordem e perguntar ao operador se deve sair a via do cliente.
     */
    fun printCardCopy(
        transactionId: String?,
        nsu: String?,
        merchantCopy: Boolean,
        isReprint: Boolean = false,
    )

    /**
     * Imprime apenas o comprovante textual da Gate8 (sem as vias de cartão da Stone).
     */
    fun printSaleSummary(
        lines: List<CartLine>,
        total: Double,
        paymentLabel: String,
        nsu: String?,
        authorization: String?,
        isReprint: Boolean = false,
    )

    /**
     * Modo ficha: imprime uma ficha separada para cada unidade de cada item
     * (ex.: 2 copões = 2 fichas), cada uma com a logo Gate8, data, terminal,
     * descrição, preço e o AUT (mesma autorização do comprovante) no rodapé.
     */
    fun printConvenienceTickets(
        lines: List<CartLine>,
        terminalName: String,
        authorization: String?,
    )
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

    override fun printTicket(payload: TicketPrintPayload) = Unit

    override fun printReportSummary(payload: ReportPrintPayload) = Unit

    override fun printCashierSummary(payload: CashierPrintPayload) = Unit

    override fun printCardCopy(
        transactionId: String?,
        nsu: String?,
        merchantCopy: Boolean,
        isReprint: Boolean,
    ) = Unit

    override fun printSaleSummary(
        lines: List<CartLine>,
        total: Double,
        paymentLabel: String,
        nsu: String?,
        authorization: String?,
        isReprint: Boolean,
    ) = Unit

    override fun printConvenienceTickets(
        lines: List<CartLine>,
        terminalName: String,
        authorization: String?,
    ) = Unit
}
