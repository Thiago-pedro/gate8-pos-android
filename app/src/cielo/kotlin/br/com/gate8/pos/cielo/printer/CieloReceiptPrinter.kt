package br.com.gate8.pos.cielo.printer

import android.util.Log
import br.com.gate8.pos.data.prefs.DeviceConfigStore
import br.com.gate8.pos.domain.model.CartLine
import br.com.gate8.pos.printer.CashierPrintPayload
import br.com.gate8.pos.printer.Gate8ReceiptTextBuilder
import br.com.gate8.pos.printer.ReceiptPrinter
import br.com.gate8.pos.printer.ReportPrintPayload
import br.com.gate8.pos.printer.TicketPrintPayload
import java.util.Date

/** Impressão térmica na Cielo Smart via Deep Link `lio://print`. */
class CieloReceiptPrinter(
    private val configStore: DeviceConfigStore,
) : ReceiptPrinter {

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
        runCatching {
            CieloPrintClient.printLines(
                Gate8ReceiptTextBuilder.saleReceipt(
                    lines = lines,
                    total = total,
                    paymentLabel = paymentLabel,
                    nsu = nsu,
                    authorization = authorization,
                    isReprint = isReprint,
                    terminalName = terminalName(),
                    saleDate = saleDateMillis?.let { Date(it) },
                    establishmentName = configStore.getEstablishmentName(),
                ),
            )
        }.onFailure { Log.e(TAG, "printReceipt falhou", it) }
    }

    override fun printVoidReceipt(
        lines: List<CartLine>,
        total: Double,
        paymentLabel: String,
        nsu: String?,
        authorization: String?,
    ) {
        runCatching {
            CieloPrintClient.printLines(
                Gate8ReceiptTextBuilder.voidReceipt(
                    lines = lines,
                    total = total,
                    paymentLabel = paymentLabel,
                    nsu = nsu,
                    authorization = authorization,
                    terminalName = terminalName(),
                    establishmentName = configStore.getEstablishmentName(),
                ),
            )
        }.onFailure { Log.e(TAG, "printVoidReceipt falhou", it) }
    }

    override fun printTicket(payload: TicketPrintPayload) {
        runCatching {
            val lines = Gate8ReceiptTextBuilder.ticketTopLines(payload) +
                Gate8ReceiptTextBuilder.ticketBottomLines(payload)
            CieloPrintClient.printLines(lines)
        }.onFailure { Log.e(TAG, "printTicket falhou", it) }
    }

    override fun printReportSummary(payload: ReportPrintPayload) {
        runCatching {
            CieloPrintClient.printLines(Gate8ReceiptTextBuilder.reportSummary(payload))
        }.onFailure { Log.e(TAG, "printReportSummary falhou", it) }
    }

    override fun printCashierSummary(payload: CashierPrintPayload) {
        runCatching {
            CieloPrintClient.printLines(Gate8ReceiptTextBuilder.cashierSummary(payload))
        }.onFailure { Log.e(TAG, "printCashierSummary falhou", it) }
    }

    override fun printCardCopy(
        transactionId: String?,
        nsu: String?,
        merchantCopy: Boolean,
        isReprint: Boolean,
    ) {
        // Vias de cartão/Pix são impressas pela Cielo no fluxo de pagamento.
        Log.i(TAG, "Via ${if (merchantCopy) "LOJISTA" else "CLIENTE"} — impressa pela Cielo (tx=$transactionId)")
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
        runCatching {
            val producer = configStore.getEstablishmentName()
            lines.forEach { line ->
                repeat(line.quantity.coerceAtLeast(1)) {
                    CieloPrintClient.printLines(
                        Gate8ReceiptTextBuilder.convenienceTicket(
                            description = line.description,
                            unitPrice = line.unitPrice,
                            terminalName = terminalName,
                            authorization = authorization,
                            producerName = producer,
                        ),
                    )
                }
            }
        }.onFailure { Log.e(TAG, "printConvenienceTickets falhou", it) }
    }

    private fun terminalName(): String =
        configStore.getDeviceName()?.takeIf { it.isNotBlank() }
            ?: configStore.getDeviceShortId()

    companion object {
        private const val TAG = "Gate8CieloPrint"
    }
}
