package br.com.gate8.pos.printer

import br.com.gate8.pos.domain.model.CartLine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Gera o texto dos comprovantes da Gate8 em um layout único e padronizado para
 * todos os recibos (venda, reimpressão, estorno, ingresso, relatório e caixa).
 *
 * Largura fixa de 32 colunas (papel 58mm dos terminais Stone POS).
 */
object Gate8ReceiptTextBuilder {
    private const val WIDTH = 32
    private const val MERCHANT_NAME = "GATE8"

    private val brLocale = Locale("pt", "BR")
    private val timeFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", brLocale)

    // ----- Helpers de layout -----

    private fun divider(): String = "-".repeat(WIDTH)

    private fun center(text: String): String {
        if (text.length >= WIDTH) return text
        val left = (WIDTH - text.length) / 2
        return " ".repeat(left) + text
    }

    /** Rótulo à esquerda e valor alinhado à direita na mesma linha. */
    private fun row(left: String, right: String): String {
        val space = WIDTH - left.length - right.length
        if (space >= 1) return left + " ".repeat(space) + right
        // Não cabe: trunca o lado esquerdo para preservar o valor à direita.
        val maxLeft = (WIDTH - right.length - 1).coerceAtLeast(0)
        val trimmed = left.take(maxLeft)
        return trimmed + " " + right
    }

    /** Linha de item: descrição à esquerda, valor à direita (quebra se necessário). */
    private fun itemRows(quantity: Int, description: String, value: Double): List<String> {
        val left = "${quantity}x $description"
        val right = amount(value)
        return if (left.length + right.length + 1 <= WIDTH) {
            listOf(row(left, right))
        } else {
            listOf(left, row("", right))
        }
    }

    private fun money(value: Double): String = "R$ " + String.format(brLocale, "%,.2f", value)

    private fun amount(value: Double): String = String.format(brLocale, "%,.2f", value)

    // A logo grafica da Gate8 e impressa como bitmap no topo de todo recibo
    // (ver StonePosPrinterLive.printLines), por isso o cabecalho textual nao
    // repete o nome "GATE8" aqui.
    private fun header(title: String, subtitle: String? = null): List<String> = buildList {
        add(divider())
        add(center(title))
        subtitle?.let { add(center(it)) }
        add(divider())
    }

    private fun footer(): List<String> = buildList {
        add(divider())
        add(center("OBRIGADO PELA PREFERENCIA!"))
        add(center("SUPORTE: suporte@gate8.club"))
        add(divider())
    }

    private fun paymentBlock(
        target: MutableList<String>,
        paymentLabel: String,
        nsu: String?,
        authorization: String?,
        via: String = "CLIENTE",
    ) {
        target.add("PAGAMENTO: ${paymentLabel.uppercase(brLocale)}")
        if (!nsu.isNullOrBlank()) target.add("NSU: $nsu")
        if (!authorization.isNullOrBlank()) target.add("AUT.: $authorization")
        target.add("VIA: $via")
    }

    // ----- Comprovantes -----

    fun saleReceipt(
        lines: List<CartLine>,
        total: Double,
        paymentLabel: String,
        nsu: String?,
        authorization: String?,
        isReprint: Boolean,
    ): List<String> = buildList {
        addAll(
            header(
                title = "COMPROVANTE DE PAGAMENTO",
                subtitle = if (isReprint) "** REIMPRESSAO **" else null,
            ),
        )
        add(row("DATA:", timeFormat.format(Date())))
        add(row("TERMINAL:", MERCHANT_NAME))
        add(divider())
        add(row("DESCRICAO", "VALOR (R$)"))
        add("")
        lines.forEach { line ->
            addAll(itemRows(line.quantity, line.description, line.lineTotal))
        }
        add(divider())
        add(row("TOTAL", money(total)))
        add(divider())
        paymentBlock(this, paymentLabel, nsu, authorization)
        addAll(footer())
    }

    fun voidReceipt(
        lines: List<CartLine>,
        total: Double,
        paymentLabel: String,
        nsu: String?,
        authorization: String?,
    ): List<String> = buildList {
        addAll(header(title = "COMPROVANTE DE ESTORNO"))
        add(row("DATA:", timeFormat.format(Date())))
        add(row("TERMINAL:", MERCHANT_NAME))
        add(divider())
        add(row("DESCRICAO", "VALOR (R$)"))
        add("")
        lines.forEach { line ->
            addAll(itemRows(line.quantity, line.description, line.lineTotal))
        }
        add(divider())
        add(row("VALOR ESTORNADO", money(total)))
        add(divider())
        paymentBlock(this, paymentLabel, nsu, authorization)
        addAll(footer())
    }

    fun ticketBlock(code: String, holder: String?, description: String): List<String> = buildList {
        addAll(header(title = "INGRESSO"))
        add(description)
        holder?.takeIf { it.isNotBlank() }?.let { add(row("TITULAR:", it)) }
        add(divider())
        add(center("CODIGO"))
        add(center(code))
        addAll(footer())
    }

    fun reportSummary(payload: ReportPrintPayload): List<String> = buildList {
        addAll(header(title = "RELATORIO"))
        payload.producerName?.let { add(row("PRODUTOR:", it)) }
        payload.deviceName?.let { add(row("MAQUININHA:", it)) }
        add(row("PERIODO:", payload.periodLabel))
        add(divider())
        add(row("VENDAS:", payload.saleCount.toString()))
        add(row("ESTORNOS:", payload.voidCount.toString()))
        add(row("LIQUIDO:", money(payload.netTotal)))
        add(row("TICKET MEDIO:", money(payload.averageTicket)))
        add(divider())
        add(center("POR PAGAMENTO"))
        add("")
        if (payload.byPaymentMethod.isEmpty()) {
            add(center("Nenhuma venda"))
        } else {
            payload.byPaymentMethod.forEach { entry ->
                add(row("${entry.count}x ${entry.label}", amount(entry.total)))
            }
        }
        addAll(footer())
    }

    fun cashierSummary(payload: CashierPrintPayload): List<String> = buildList {
        addAll(header(title = "RESUMO DE CAIXA"))
        payload.producerName?.let { add(row("PRODUTOR:", it)) }
        payload.deviceName?.let { add(row("MAQUININHA:", it)) }
        payload.operatorName?.let { add(row("OPERADOR:", it)) }
        add(row("ABERTURA:", payload.openedAtLabel))
        payload.closedAtLabel?.let { add(row("FECHAMENTO:", it)) }
        add(divider())
        add(row("TROCO INICIAL:", money(payload.openingBalance)))
        add(row("VENDAS CASH:", money(payload.cashSales)))
        add(row("SANGRIAS:", money(payload.withdrawals)))
        add(row("DESPESAS:", money(payload.expenses)))
        add(row("ESPERADO:", money(payload.expectedDrawer)))
        payload.countedBalance?.let { add(row("CONTADO:", money(it))) }
        payload.difference?.let { add(row("DIFERENCA:", money(it))) }
        add(divider())
        add(row("VENDAS:", payload.saleCount.toString()))
        add(row("TOTAL:", money(payload.grandTotal)))
        addAll(footer())
    }
}
