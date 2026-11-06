package br.com.gate8.pos.printer

data class KitchenOrderPayload(
    val orderNumber: Int,
    val terminalName: String,
    val soldAtMillis: Long,
    val items: List<KitchenOrderLine>,
    val note: String? = null,
)

data class KitchenOrderLine(
    val description: String,
    val quantity: Int,
)

/** Resultado de uma tentativa de ficha. message explica falha de papel, ocupada ou tempo. */
data class KitchenPrintOutcome(
    val ok: Boolean,
    val message: String? = null,
)
