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
