package br.com.gate8.pos.kitchen

import br.com.gate8.pos.domain.model.KitchenOrder

/**
 * Fila em memória da cozinha.
 * Várias maquininhas 1.0.8 mandam pedido; o número pode repetir entre caixas.
 * A identidade é o id do servidor. O mesmo caixa e o mesmo número não imprimem de novo.
 * O GET vem do mais novo para o mais antigo;
 * a impressão sai do mais antigo. Enquanto o id está sendo impresso ou ainda
 * espera o /printed, ele não entra de novo.
 */
class KitchenPrintQueue {
    private val lock = Any()
    private val waiting = ArrayDeque<KitchenOrder>()
    private val printing = LinkedHashSet<String>()
    private val printingTickets = LinkedHashMap<String, String>()
    private val spentTickets = LinkedHashSet<String>()
    private val spentIds = LinkedHashSet<String>()
    private val awaitingAck = LinkedHashMap<String, KitchenOrder>()
    private val ackedIds = LinkedHashSet<String>()

    fun offer(orders: List<KitchenOrder>): Boolean = synchronized(lock) {
        var added = false
        for (order in orders) {
            if (order.id.isBlank() || blocked(order)) continue
            waiting.add(order)
            added = true
        }
        if (added) {
            val sorted = waiting.sortedWith(
                compareBy<KitchenOrder> { it.soldAtMillis }
                    .thenBy { it.orderNumber }
                    .thenBy { it.id },
            )
            waiting.clear()
            waiting.addAll(sorted)
        }
        added
    }

    fun takeNext(): KitchenOrder? = synchronized(lock) {
        val order = waiting.removeFirstOrNull() ?: return null
        printing.add(order.id)
        ticketKey(order)?.let { printingTickets[order.id] = it }
        order
    }

    /** A impressora não chegou a imprimir. O mesmo pedido pode tentar de novo. */
    fun printFailed(id: String) = synchronized(lock) {
        printing.remove(id)
        printingTickets.remove(id)
    }

    /**
     * A ficha já foi para a impressora. Não imprime de novo, mesmo que o
     * callback da Cielo pareça erro ou o servidor mande o mesmo número outra vez.
     */
    fun finishPrint(order: KitchenOrder) = synchronized(lock) {
        printing.remove(order.id)
        spentIds.add(order.id)
        val ticket = printingTickets.remove(order.id) ?: ticketKey(order)
        if (ticket != null) spentTickets.add(ticket)
        trimSpent()
    }

    fun printSucceeded(order: KitchenOrder) = synchronized(lock) {
        finishPrint(order)
        awaitingAck[order.id] = order
    }

    fun ackSucceeded(id: String) = synchronized(lock) {
        awaitingAck.remove(id)
        printing.remove(id)
        ackedIds.add(id)
        trimAcked()
    }

    fun pendingAcks(): List<KitchenOrder> = synchronized(lock) {
        awaitingAck.values.toList()
    }

    /** Se o ciclo de impressão morreu no meio, o pedido volta a poder ser pego na próxima busca. */
    fun releasePrinting() = synchronized(lock) {
        printing.clear()
        printingTickets.clear()
    }

    private fun blocked(order: KitchenOrder): Boolean {
        if (order.id in printing || order.id in awaitingAck || order.id in ackedIds || order.id in spentIds) {
            return true
        }
        val ticket = ticketKey(order)
        if (ticket != null && (ticket in printingTickets.values || ticket in spentTickets)) return true
        return waiting.any { it.id == order.id || (ticket != null && ticketKey(it) == ticket) }
    }

    private fun ticketKey(order: KitchenOrder): String? {
        if (order.orderNumber <= 0) return null
        val terminal = order.terminalName.trim().lowercase()
        if (terminal.isEmpty()) return null
        return "$terminal#${order.orderNumber}"
    }

    private fun trimSpent() {
        trim(spentIds)
        trim(spentTickets)
    }

    private fun trim(ids: MutableSet<String>) {
        val iterator = ids.iterator()
        while (ids.size > 400 && iterator.hasNext()) {
            iterator.next()
            iterator.remove()
        }
    }

    private fun trimAcked() {
        val iterator = ackedIds.iterator()
        while (ackedIds.size > 400 && iterator.hasNext()) {
            iterator.next()
            iterator.remove()
        }
    }
}

fun kitchenPrinterWarning(message: String?): String? {
    if (!kitchenPrintShouldRetry(message)) return null
    val folded = foldPrinterMessage(message.orEmpty())
    return when {
        folded.contains("papel") || folded.contains("paper") || folded.contains("bobina") ->
            "Impressora sem papel"
        folded.contains("ocup") || folded.contains("busy") ->
            "Impressora ocupada"
        folded.contains("credenc") || folded.contains("abra o app") ->
            "Abra o app Gate8 na maquininha"
        else -> null
    }
}

/** Só tenta de novo quando a bobina não saiu. Timeout e erro genérico já podem ter impresso. */
fun kitchenPrintShouldRetry(message: String?): Boolean {
    val folded = foldPrinterMessage(message.orEmpty())
    if (folded.contains("timeout") || folded.contains("tempo esgotado")) return false
    return folded.contains("papel") || folded.contains("paper") || folded.contains("bobina") ||
        folded.contains("ocup") || folded.contains("busy") ||
        folded.contains("credenc") || folded.contains("abra o app")
}

private fun foldPrinterMessage(text: String): String =
    text.lowercase()
        .replace('á', 'a')
        .replace('à', 'a')
        .replace('ã', 'a')
        .replace('â', 'a')
        .replace('é', 'e')
        .replace('ê', 'e')
        .replace('í', 'i')
        .replace('ó', 'o')
        .replace('ô', 'o')
        .replace('õ', 'o')
        .replace('ú', 'u')
        .replace('ç', 'c')
