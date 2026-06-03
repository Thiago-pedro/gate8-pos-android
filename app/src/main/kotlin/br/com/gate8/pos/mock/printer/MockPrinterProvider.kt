package br.com.gate8.pos.mock.printer

import android.util.Log
import br.com.gate8.pos.domain.model.CartLine
import br.com.gate8.pos.printer.ReceiptPrinter

class MockPrinterProvider : ReceiptPrinter {
    override fun printReceipt(
        lines: List<CartLine>,
        total: Double,
        paymentLabel: String,
        nsu: String?,
        authorization: String?,
    ) {
        val sb = StringBuilder("=== GATE8 CUPOM (MOCK) ===\n")
        lines.forEach { l ->
            sb.append("${l.quantity}x ${l.description} R$ ${"%.2f".format(l.lineTotal)}\n")
        }
        sb.append("TOTAL R$ ${"%.2f".format(total)}\n")
        sb.append("Pagamento: $paymentLabel\n")
        if (nsu != null) sb.append("NSU: $nsu  Auth: $authorization\n")
        Log.i(TAG, sb.toString())
    }

    override fun printTicketQr(code: String, holder: String?, description: String) {
        Log.i(TAG, "QR INGRESSO (conteúdo=$code) $description holder=$holder")
    }

    companion object {
        private const val TAG = "Gate8PrinterMock"
    }
}
