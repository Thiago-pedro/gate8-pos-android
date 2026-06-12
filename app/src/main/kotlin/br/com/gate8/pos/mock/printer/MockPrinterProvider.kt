package br.com.gate8.pos.mock.printer

import android.util.Log
import br.com.gate8.pos.domain.model.CartLine
import br.com.gate8.pos.printer.ReportPrintPayload
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

    override fun printReportSummary(payload: ReportPrintPayload) {
        val sb = StringBuilder("=== GATE8 RELATORIO (MOCK) ===\n")
        payload.producerName?.let { sb.append("Produtor: $it\n") }
        payload.deviceName?.let { sb.append("Maquininha: $it\n") }
        sb.append("Periodo: ${payload.periodLabel}\n")
        sb.append("--------------------------------\n")
        sb.append("RESUMO GERAL\n")
        sb.append("Vendas: ${payload.saleCount}\n")
        sb.append("Estornos: ${payload.voidCount}\n")
        sb.append("Bruto: R$ ${"%.2f".format(payload.grossTotal)}\n")
        sb.append("Estornos R$: R$ ${"%.2f".format(payload.voidTotal)}\n")
        sb.append("Liquido: R$ ${"%.2f".format(payload.netTotal)}\n")
        sb.append("Ticket medio: R$ ${"%.2f".format(payload.averageTicket)}\n")
        sb.append("--------------------------------\n")
        sb.append("POR PAGAMENTO\n")
        if (payload.byPaymentMethod.isEmpty()) {
            sb.append("Nenhuma venda\n")
        } else {
            payload.byPaymentMethod.forEach { row ->
                sb.append("${row.label}: ${row.count}x R$ ${"%.2f".format(row.total)}\n")
            }
        }
        sb.append("--------------------------------\n")
        sb.append("POR BANDEIRA (CARTAO)\n")
        if (payload.byBrand.isEmpty()) {
            sb.append("Sem cartao no periodo\n")
        } else {
            payload.byBrand.forEach { row ->
                sb.append("${row.label}: ${row.count}x R$ ${"%.2f".format(row.total)}\n")
            }
        }
        sb.append("================================\n")
        Log.i(TAG, sb.toString())
    }

    companion object {
        private const val TAG = "Gate8PrinterMock"
    }
}
