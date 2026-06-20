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
    // A fonte da impressora Stone POS é PROPORCIONAL: pontos/espaços são estreitos
    // e letras/números são largos. Por isso contar caracteres não funciona (linhas
    // com mesma contagem cabem ou quebram dependendo de quantas letras têm). Usamos
    // um orçamento em "unidades": ' '/'.' = 1 unidade, demais = 2 unidades. A bobina
    // 57mm comporta ~62 unidades; usamos 56 (com margem) para garantir UMA linha só.
    private const val UNIT_BUDGET = 56
    private const val MERCHANT_NAME = "GATE8"

    // Altura (em linhas) do corpo de cada ficha, para centralizar o conteúdo na vertical.
    private const val FICHA_BODY_LINES = 11

    private val brLocale = Locale("pt", "BR")
    private val timeFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", brLocale)

    // ----- Helpers de layout -----

    /** Largura aproximada do texto em unidades: ' ' e '.' contam 1; demais contam 2. */
    private fun units(text: String): Int {
        var total = 0
        for (c in text) total += if (c == ' ' || c == '.') 1 else 2
        return total
    }

    /** Abrevia o texto para caber em [maxUnits], terminando em "..." quando cortado. */
    private fun ellipsize(text: String, maxUnits: Int): String {
        if (maxUnits <= 0) return ""
        if (units(text) <= maxUnits) return text
        var t = text
        while (t.isNotEmpty() && units(t.trimEnd() + "...") > maxUnits) {
            t = t.dropLast(1)
        }
        val trimmed = t.trimEnd()
        return if (trimmed.isEmpty()) "" else "$trimmed..."
    }

    private fun divider(): String = "-".repeat(UNIT_BUDGET)

    private fun dottedDivider(): String = ".".repeat(UNIT_BUDGET)

    private fun center(text: String): String {
        val u = units(text)
        if (u >= UNIT_BUDGET) return text
        return " ".repeat((UNIT_BUDGET - u) / 2) + text
    }

    /**
     * Rótulo à esquerda e valor à direita, com o vão preenchido por pontos
     * (ex.: "VENDAS . . . . . 23"). A quantidade de pontos se ajusta à largura
     * real (fonte proporcional) e o rótulo é abreviado com "..." se necessário,
     * garantindo SEMPRE uma única linha.
     */
    private fun dotLeaderRow(left: String, right: String): String {
        val rightU = units(right)
        val maxLeftU = (UNIT_BUDGET - rightU - 2).coerceAtLeast(0)
        val l = ellipsize(left, maxLeftU)
        val gap = (UNIT_BUDGET - units(l) - rightU).coerceAtLeast(1)
        val leader = CharArray(gap) { if (it % 2 == 0) ' ' else '.' }
        leader[gap - 1] = ' '
        return l + String(leader) + right
    }

    /** Rótulo à esquerda e valor alinhado à direita, sempre em uma única linha. */
    private fun row(left: String, right: String): String {
        val rightU = units(right)
        val l = ellipsize(left, (UNIT_BUDGET - rightU - 1).coerceAtLeast(0))
        val space = (UNIT_BUDGET - units(l) - rightU).coerceAtLeast(1)
        return l + " ".repeat(space) + right
    }

    /**
     * Linha de item: "{qtd} x {unitario} {descricao}" à esquerda e o total da linha
     * à direita, SEMPRE em uma única linha. Se não couber, a descrição é abreviada
     * com "..." para preservar quantidade, valor unitário e total.
     */
    private fun itemRows(
        quantity: Int,
        unitPrice: Double,
        description: String,
        value: Double,
    ): List<String> {
        val prefix = "$quantity x ${amount(unitPrice)} "
        val right = amount(value)
        val maxLeftU = (UNIT_BUDGET - units(right) - 1).coerceAtLeast(0)
        val maxDescU = (maxLeftU - units(prefix)).coerceAtLeast(0)
        val left = prefix + ellipsize(description, maxDescU)
        return listOf(row(left, right))
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
        add(center("suporte@gate8.club"))
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
        terminalName: String = MERCHANT_NAME,
    ): List<String> = buildList {
        addAll(
            header(
                title = "COMPROVANTE DE PAGAMENTO",
                subtitle = if (isReprint) "** REIMPRESSAO **" else null,
            ),
        )
        add(row("DATA:", timeFormat.format(Date())))
        add(row("DISPOSITIVO:", terminalName))
        add(divider())
        add(row("DESCRICAO", "VALOR (R$)"))
        add("")
        lines.forEach { line ->
            addAll(itemRows(line.quantity, line.unitPrice, line.description, line.lineTotal))
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
        terminalName: String = MERCHANT_NAME,
    ): List<String> = buildList {
        addAll(header(title = "COMPROVANTE DE ESTORNO"))
        add(row("DATA:", timeFormat.format(Date())))
        add(row("DISPOSITIVO:", terminalName))
        add(divider())
        add(row("DESCRICAO", "VALOR (R$)"))
        add("")
        lines.forEach { line ->
            addAll(itemRows(line.quantity, line.unitPrice, line.description, line.lineTotal))
        }
        add(divider())
        add(row("VALOR ESTORNADO", money(total)))
        add(divider())
        paymentBlock(this, paymentLabel, nsu, authorization)
        addAll(footer())
    }

    /**
     * Ficha individual da conveniência (modo ficha). A logo Gate8 (reduzida) é
     * impressa como bitmap no topo (ver StonePosPrinterLive.printLines). Abaixo vão:
     * data/hora, terminal (nome do dispositivo), nome do item e preço unitário,
     * todos centralizados. Sem nome do estabelecimento e sem CNPJ.
     */
    fun convenienceTicket(
        description: String,
        unitPrice: Double,
        terminalName: String,
        authorization: String?,
        producerName: String? = null,
    ): List<String> {
        val main = buildList {
            // Nome do produtor logo abaixo da logo Gate8, sem rótulo.
            producerName?.takeIf { it.isNotBlank() }?.let {
                add(center(it))
                add("")
            }
            add(center(timeFormat.format(Date())))
            add(center(terminalName))
            add("")
            add(center(description.uppercase(brLocale)))
            add(center(money(unitPrice)))
        }
        // AUT vai no rodapé da ficha (última informação), com um espaço antes.
        val footer = if (!authorization.isNullOrBlank()) {
            listOf("", center("AUT.: $authorization"))
        } else {
            emptyList()
        }
        // Centraliza o conteúdo principal na vertical dentro do "quadrado" da ficha,
        // reservando o rodapé para o AUT. O pontilhado fecha o quadrado.
        val padding = (FICHA_BODY_LINES - main.size - footer.size).coerceAtLeast(2)
        val padTop = padding / 2
        val padBottom = padding - padTop
        return buildList {
            repeat(padTop) { add("") }
            addAll(main)
            repeat(padBottom) { add("") }
            addAll(footer)
            add(dottedDivider())
        }
    }

    /** Quebra um texto longo (ex.: endereço) em várias linhas centralizadas. */
    private fun centerWrap(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        val words = text.trim().split(Regex("\\s+"))
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (word in words) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (units(candidate) > UNIT_BUDGET && current.isNotEmpty()) {
                lines.add(center(current.toString()))
                current = StringBuilder(word)
            } else {
                current = StringBuilder(candidate)
            }
        }
        if (current.isNotEmpty()) lines.add(center(current.toString()))
        return lines
    }

    /**
     * Parte de cima do ingresso (acima do QR): evento, lote, data, local, portador e preço.
     * A logo Gate8 é impressa como bitmap no topo (ver StonePosPrinterLive.printTicket).
     */
    fun ticketTopLines(p: TicketPrintPayload): List<String> = buildList {
        add(divider())
        add(center(p.eventName.uppercase(brLocale)))
        add(divider())
        if (p.batchName.isNotBlank()) add(center(p.batchName.uppercase(brLocale)))
        p.eventDateLabel?.takeIf { it.isNotBlank() }?.let { add(center(it)) }
        p.venue?.takeIf { it.isNotBlank() }?.let {
            add("")
            addAll(centerWrap(it))
        }
        add(divider())
        p.terminalName?.takeIf { it.isNotBlank() }?.let {
            add(center("DISPOSITIVO: $it"))
            add("")
        }
        add(center(money(p.price)))
        add(divider())
        add("")
    }

    /** Parte de baixo do ingresso (abaixo do QR): código manual, compra, validade e emissão. */
    fun ticketBottomLines(p: TicketPrintPayload): List<String> = buildList {
        // Código curto de validação manual = 8 primeiros alfanuméricos da hash (igual ao site).
        val manualCode = p.validationCode.filter { it.isLetterOrDigit() }.take(8).uppercase(brLocale)
        add("")
        add(center(manualCode))
        add(center("Codigo para validacao manual"))
        p.purchaseCode?.takeIf { it.isNotBlank() }?.let { add(center("Compra $it")) }
        add("")
        add(center("** VALIDO **"))
        add(center("Emitido: ${timeFormat.format(Date())}"))
        add("")
        addAll(
            centerWrap(
                "A criterio da organizacao, podera ser solicitado documento " +
                    "original com foto para acesso ao evento.",
            ),
        )
        addAll(footer())
    }

    fun reportSummary(payload: ReportPrintPayload): List<String> = buildList {
        addAll(header(title = "RELATORIO"))
        payload.producerName?.let { add(dotLeaderRow("PRODUTOR:", it)) }
        payload.deviceName?.let { add(dotLeaderRow("MAQUININHA:", it)) }
        add(dotLeaderRow("PERIODO:", payload.periodLabel))
        add(divider())
        add(dotLeaderRow("VENDAS:", payload.saleCount.toString()))
        add(dotLeaderRow("ESTORNOS:", payload.voidCount.toString()))
        add(dotLeaderRow("BRUTO:", money(payload.grossTotal)))
        add(dotLeaderRow("ESTORNOS R$:", money(payload.voidTotal)))
        add(dotLeaderRow("LIQUIDO:", money(payload.netTotal)))
        add(dotLeaderRow("TICKET MEDIO:", money(payload.averageTicket)))
        add(divider())
        add(center("POR PAGAMENTO"))
        add("")
        if (payload.byPaymentMethod.isEmpty()) {
            add(center("Nenhuma venda"))
        } else {
            payload.byPaymentMethod.forEach { entry ->
                add(dotLeaderRow("${entry.count}x ${entry.label}", amount(entry.total)))
            }
        }
        add(divider())
        add(center("POR BANDEIRA"))
        add("")
        if (payload.byBrand.isEmpty()) {
            add(center("Sem cartao no periodo"))
        } else {
            payload.byBrand.forEach { entry ->
                add(dotLeaderRow("${entry.count}x ${entry.label}", amount(entry.total)))
            }
        }
        add(divider())
        add(center("MAIS VENDIDOS"))
        add("")
        if (payload.topItems.isEmpty()) {
            add(center("Nenhum item"))
        } else {
            payload.topItems.forEach { item ->
                add(dotLeaderRow("${item.quantity}x ${item.name}", amount(item.total)))
            }
        }
        addAll(cashierBlock(payload.cashier))
        addAll(footer())
    }

    /** Bloco do caixa no relatório: saldo + "ainda aberto" se aberto, ou resumo se fechado. */
    private fun cashierBlock(cashier: ReportCashierInfo?): List<String> = buildList {
        add(divider())
        add(center("CAIXA"))
        add("")
        if (cashier == null) {
            add(center("Sem dados de caixa"))
            return@buildList
        }
        if (cashier.open) {
            add(dotLeaderRow("STATUS:", "ABERTO"))
            cashier.operatorName?.takeIf { it.isNotBlank() }?.let { add(dotLeaderRow("OPERADOR:", it)) }
            add(dotLeaderRow("TROCO INICIAL:", amount(cashier.openingBalance)))
            add(dotLeaderRow("VENDAS DINHEIRO:", amount(cashier.cashSales)))
            if (cashier.withdrawals > 0.0) add(dotLeaderRow("SANGRIAS:", amount(cashier.withdrawals)))
            if (cashier.expenses > 0.0) add(dotLeaderRow("DESPESAS:", amount(cashier.expenses)))
            add(dotLeaderRow("SALDO NA GAVETA:", amount(cashier.expectedDrawer)))
            add("")
            add(center("** AINDA ABERTO **"))
        } else {
            add(dotLeaderRow("STATUS:", "FECHADO"))
            cashier.operatorName?.takeIf { it.isNotBlank() }?.let { add(dotLeaderRow("OPERADOR:", it)) }
            add(dotLeaderRow("TROCO INICIAL:", amount(cashier.openingBalance)))
            add(dotLeaderRow("VENDAS DINHEIRO:", amount(cashier.cashSales)))
            if (cashier.withdrawals > 0.0) add(dotLeaderRow("SANGRIAS:", amount(cashier.withdrawals)))
            if (cashier.expenses > 0.0) add(dotLeaderRow("DESPESAS:", amount(cashier.expenses)))
            add(dotLeaderRow("ESPERADO:", amount(cashier.expectedDrawer)))
            cashier.countedBalance?.let { add(dotLeaderRow("CONTADO:", amount(it))) }
            cashier.difference?.let { add(dotLeaderRow("DIFERENCA:", amount(it))) }
        }
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
