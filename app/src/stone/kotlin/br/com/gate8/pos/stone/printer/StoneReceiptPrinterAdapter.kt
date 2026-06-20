package br.com.gate8.pos.stone.printer

import android.util.Log
import br.com.gate8.pos.data.prefs.DeviceConfigStore
import br.com.gate8.pos.domain.model.CartLine
import br.com.gate8.pos.printer.CashierPrintPayload
import br.com.gate8.pos.printer.Gate8ReceiptTextBuilder
import br.com.gate8.pos.printer.ReceiptPrinter
import br.com.gate8.pos.printer.ReportPrintPayload
import br.com.gate8.pos.printer.TicketPrintPayload
import br.com.gate8.pos.stone.StoneActivityHolder

class StoneReceiptPrinterAdapter(
    private val activityHolder: StoneActivityHolder,
    private val posPrinter: StonePosPrinter,
    private val configStore: DeviceConfigStore,
) : ReceiptPrinter {

    /** Nome do dispositivo exibido nos comprovantes (ex.: "CX 9"). */
    private fun deviceName(): String =
        configStore.getDeviceName()?.takeIf { it.isNotBlank() } ?: configStore.getDeviceShortId()

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

        val isCardLike = isCardPayment(paymentLabel) &&
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
            terminalName = deviceName(),
        )
        posPrinter.printLines(activity, textLines)
    }

    override fun printVoidReceipt(
        lines: List<CartLine>,
        total: Double,
        paymentLabel: String,
        nsu: String?,
        authorization: String?,
    ) {
        val activity = activityHolder.runCatching { requireActivity() }.getOrNull() ?: return
        if (!posPrinter.isAvailable) return
        posPrinter.printLines(
            activity,
            Gate8ReceiptTextBuilder.voidReceipt(
                lines = lines,
                total = total,
                paymentLabel = paymentLabel,
                nsu = nsu,
                authorization = authorization,
                terminalName = deviceName(),
            ),
        )
    }

    override fun printTicket(payload: TicketPrintPayload) {
        val activity = activityHolder.runCatching { requireActivity() }.getOrNull() ?: return
        if (!posPrinter.isAvailable) return
        posPrinter.printTicket(
            activity = activity,
            topLines = Gate8ReceiptTextBuilder.ticketTopLines(payload),
            qrContent = payload.validationCode,
            bottomLines = Gate8ReceiptTextBuilder.ticketBottomLines(payload),
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

    override fun printCardCopy(
        transactionId: String?,
        nsu: String?,
        merchantCopy: Boolean,
        isReprint: Boolean,
    ) {
        val activity = activityHolder.runCatching { requireActivity() }.getOrNull() ?: return
        if (!posPrinter.isAvailable) return
        if (isReprint && !transactionId.isNullOrBlank()) {
            posPrinter.reprintCardReceipt(activity, transactionId, merchantCopy)
        } else {
            posPrinter.printCardReceipt(activity, transactionId, nsu, merchantCopy)
        }
    }

    override fun printSaleSummary(
        lines: List<CartLine>,
        total: Double,
        paymentLabel: String,
        nsu: String?,
        authorization: String?,
        isReprint: Boolean,
    ) {
        val activity = activityHolder.runCatching { requireActivity() }.getOrNull() ?: return
        if (!posPrinter.isAvailable) return
        posPrinter.printLines(
            activity,
            Gate8ReceiptTextBuilder.saleReceipt(
                lines = lines,
                total = total,
                paymentLabel = paymentLabel,
                nsu = nsu,
                authorization = authorization,
                isReprint = isReprint,
                terminalName = deviceName(),
            ),
        )
    }

    override fun printConvenienceTickets(
        lines: List<CartLine>,
        terminalName: String,
        authorization: String?,
    ) {
        val activity = activityHolder.runCatching { requireActivity() }.getOrNull() ?: return
        if (!posPrinter.isAvailable) return
        lines.forEach { line ->
            repeat(line.quantity.coerceAtLeast(1)) {
                posPrinter.printLines(
                    activity,
                    Gate8ReceiptTextBuilder.convenienceTicket(
                        description = line.description,
                        unitPrice = line.unitPrice,
                        terminalName = terminalName,
                        authorization = authorization,
                        producerName = configStore.getProducerName(),
                    ),
                    logoScale = FICHA_LOGO_SCALE,
                )
            }
        }
    }

    companion object {
        private const val TAG = "Gate8StonePrinter"
        private const val FICHA_LOGO_SCALE = 0.5f

        /**
         * Detecta pagamento com via Stone (crédito/débito/pix) de forma robusta:
         * aceita tanto os valores de API ("credit"/"debit"/"pix") quanto os rótulos
         * de exibição com acento ("Crédito"/"Débito"/"Pix"). Sem isso, a reimpressão
         * (que usa o rótulo de exibição) não reimprimia as vias da Stone no crédito/débito.
         */
        private fun isCardPayment(label: String): Boolean {
            val normalized = java.text.Normalizer.normalize(label, java.text.Normalizer.Form.NFD)
                .replace("\\p{Mn}".toRegex(), "")
                .lowercase()
                .trim()
            return normalized.startsWith("cred") ||
                normalized.startsWith("deb") ||
                normalized.startsWith("pix")
        }
    }
}
