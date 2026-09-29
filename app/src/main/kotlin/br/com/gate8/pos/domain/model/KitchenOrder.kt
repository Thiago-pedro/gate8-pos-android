package br.com.gate8.pos.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class KitchenOrder(
    val id: String,
    val orderNumber: Int,
    val terminalName: String,
    val soldAtMillis: Long,
    val items: List<KitchenOrderItem>,
)

@Serializable
data class KitchenOrderItem(
    val description: String,
    val quantity: Int,
)

data class KitchenPollResult(
    val orders: List<KitchenOrder>,
    val apiAvailable: Boolean,
)
