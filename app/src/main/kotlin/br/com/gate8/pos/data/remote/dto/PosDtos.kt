package br.com.gate8.pos.data.remote.dto

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNames

@Serializable
data class CatalogResponseDto(
    val device: DeviceDto,
    @SerialName("merchant_name") val merchantName: String? = null,
    val event: EventSummaryDto? = null,
    val events: List<EventCatalogDto> = emptyList(),
    val products: List<ProductDto> = emptyList(),
    @SerialName("ticket_batches") val ticketBatches: List<TicketBatchDto> = emptyList(),
    @SerialName("server_time") val serverTime: String,
)

@Serializable
data class DeviceDto(
    val id: String,
    val name: String,
    @SerialName("event_id") val eventId: String? = null,
)

@Serializable
data class EventSummaryDto(
    val id: String,
    val name: String,
    val slug: String? = null,
    @SerialName("event_date") val eventDate: String? = null,
    val status: String? = null,
)

@Serializable
data class EventCatalogDto(
    val id: String,
    val name: String,
    val slug: String? = null,
    @SerialName("event_date") val eventDate: String? = null,
    val status: String? = null,
    val location: String? = null,
    @SerialName("banner_url") val bannerUrl: String? = null,
    @SerialName("is_bound") val isBound: Boolean = false,
    @SerialName("ticket_batches") val ticketBatches: List<TicketBatchDto> = emptyList(),
)

@Serializable
data class TicketBatchDto(
    val id: String,
    @SerialName("event_id") val eventId: String,
    val name: String,
    val sector: String? = null,
    val gender: String? = null,
    val price: Double,
    val quantity: Int = 0,
    val sold: Int = 0,
    val available: Int = 0,
    @SerialName("valid_until") val validUntil: String? = null,
)

@Serializable
data class ProductDto(
    val id: String,
    val name: String,
    val description: String? = null,
    val sku: String? = null,
    val category: String? = null,
    val price: Double,
    /** `null` quando o painel desliga "Controlar estoque deste produto". */
    @SerialName("stock_quantity") val stockQuantity: Int? = null,
    @SerialName("track_stock") val trackStock: Boolean? = null,
    @SerialName("manage_stock") val manageStock: Boolean? = null,
    @SerialName("stock_control") val stockControl: Boolean? = null,
    @SerialName("track_inventory") val trackInventory: Boolean? = null,
    @SerialName("manage_inventory") val manageInventory: Boolean? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("event_id") val eventId: String? = null,
)

@Serializable
data class CreateSaleRequestDto(
    @SerialName("client_reference") val clientReference: String,
    @SerialName("operator_name") val operatorName: String,
    @SerialName("payment_method") val paymentMethod: String,
    @SerialName("total_amount") val totalAmount: Double,
    @SerialName("acquirer")
    val acquirer: AcquirerPaymentDto? = null,
    val items: List<SaleItemDto>,
)

@Serializable
data class AcquirerPaymentDto(
    val nsu: String,
    val authorization: String,
    val brand: String? = null,
    @SerialName("transaction_id") val transactionId: String,
)

@Serializable
data class SaleItemDto(
    @SerialName("item_type") val itemType: String,
    @SerialName("product_id") val productId: String? = null,
    @SerialName("batch_id") val batchId: String? = null,
    @SerialName("event_id") val eventId: String? = null,
    @SerialName("holder_name") val holderName: String? = null,
    @SerialName("holder_email") val holderEmail: String? = null,
    val description: String,
    val quantity: Int,
    @SerialName("unit_price") val unitPrice: Double,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class CreateSaleResponseDto(
    @SerialName("sale_id") val saleId: String? = null,
    val duplicated: Boolean = false,
    val tickets: List<SaleTicketGroupDto> = emptyList(),
    /** Código da compra (ex.: "GT8-BFNR6D"), no nível raiz — usado no ingresso impresso. */
    @SerialName("purchase_code")
    @JsonNames("purchase_code", "purchase_order_code", "order_code", "codigo_de_compra")
    val purchaseCode: String? = null,
    val error: String? = null,
    val available: Int? = null,
)

@Serializable
data class VoidSaleRequestDto(
    @SerialName("client_reference") val clientReference: String? = null,
    val reason: String? = null,
)

@Serializable
data class VoidSaleResponseDto(
    @SerialName("sale_id") val saleId: String? = null,
    val status: String? = null,
    val duplicated: Boolean = false,
    val error: String? = null,
)

@Serializable
data class SaleTicketGroupDto(
    @SerialName("item_index") val itemIndex: Int,
    val tickets: List<TicketCodeDto> = emptyList(),
)

@Serializable
data class TicketCodeDto(
    val id: String,
    val code: String,
)

@Serializable
data class CheckinRequestDto(val code: String)

@Serializable
data class CheckinResponseDto(
    val result: String,
    val ticket: CheckinTicketDto? = null,
)

@Serializable
data class CheckinTicketDto(
    val id: String,
    @SerialName("holder_name") val holderName: String? = null,
    @SerialName("event_id") val eventId: String? = null,
    val status: String? = null,
    @SerialName("checked_in_at") val checkedInAt: String? = null,
)

@Serializable
data class ApiErrorDto(
    val error: String? = null,
    @SerialName("product_id") val productId: String? = null,
    val available: Int? = null,
    val details: JsonElement? = null,
)

@Serializable
data class LoginRequestDto(
    val token: String,
    val fingerprint: String,
    val label: String? = null,
)

@Serializable
data class LoginResponseDto(
    val status: String,
    @SerialName("device_token") val deviceToken: String? = null,
    @SerialName("device_id") val deviceId: String? = null,
    @SerialName("device_name") val deviceName: String? = null,
    @SerialName("producer_name") val producerName: String? = null,
    @SerialName("merchant_name") val merchantName: String? = null,
    val error: String? = null,
)
