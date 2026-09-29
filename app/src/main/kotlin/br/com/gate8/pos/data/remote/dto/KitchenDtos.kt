package br.com.gate8.pos.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class KitchenOrdersResponseDto(
    val orders: List<KitchenOrderDto> = emptyList(),
    val pending: List<KitchenOrderDto> = emptyList(),
)

@Serializable
data class KitchenOrderDto(
    val id: String,
    @SerialName("order_number") val orderNumber: Int? = null,
    @SerialName("terminal_name") val terminalName: String? = null,
    @SerialName("sold_at") val soldAt: String? = null,
    val items: List<KitchenOrderItemDto> = emptyList(),
)

@Serializable
data class KitchenOrderItemDto(
    val description: String,
    val quantity: Int = 1,
)

@Serializable
data class SubmitKitchenOrderRequestDto(
    @SerialName("sale_id") val saleId: String? = null,
    @SerialName("client_reference") val clientReference: String,
    @SerialName("terminal_name") val terminalName: String,
    val items: List<KitchenOrderItemDto>,
)

@Serializable
data class SubmitKitchenOrderResponseDto(
    val id: String? = null,
    @SerialName("order_number") val orderNumber: Int? = null,
)

@Serializable
data class KitchenPrintedResponseDto(
    val ok: Boolean = true,
)
