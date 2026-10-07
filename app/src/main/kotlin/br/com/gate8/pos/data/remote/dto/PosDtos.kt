package br.com.gate8.pos.data.remote.dto

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.contentOrNull

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
    /** `null` = o servidor não informou teto (lote sem controle de estoque). */
    val available: Int? = null,
    @SerialName("valid_until") val validUntil: String? = null,
)

val TicketBatchDto.remaining: Int
    get() = available ?: Int.MAX_VALUE

val TicketBatchDto.isSoldOut: Boolean
    get() = available != null && available <= 0

fun TicketBatchDto.canAdd(inCart: Int): Boolean = !isSoldOut && inCart < remaining

/** Aceita categoria como texto ou como objeto `{ "name": "Cozinha" }`. */
object ProductCategorySerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("ProductCategory", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: String?) {
        if (value == null) encoder.encodeNull() else encoder.encodeString(value)
    }

    override fun deserialize(decoder: Decoder): String? {
        val jsonDecoder = decoder as? JsonDecoder
            ?: return runCatching { decoder.decodeString() }.getOrNull()
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonNull -> null
            is JsonPrimitive -> element.contentOrNull?.takeIf { it.isNotBlank() }
            is JsonObject -> sequenceOf("name", "nome", "label", "title")
                .mapNotNull { key -> (element[key] as? JsonPrimitive)?.contentOrNull }
                .firstOrNull { it.isNotBlank() }
            else -> null
        }
    }
}

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class ProductDto(
    val id: String,
    val name: String,
    val description: String? = null,
    val sku: String? = null,
    @Serializable(with = ProductCategorySerializer::class)
    @JsonNames("category", "categoria", "category_name")
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
    /** Legado Lovable — mesmas colunas `stone_*` usadas em `by_brand`. */
    val stone: AcquirerPaymentDto? = null,
    val items: List<SaleItemDto>,
    /** UID do chip Mifare — obrigatório quando `payment_method` = `cashless`. */
    @SerialName("card_uid") val cardUid: String? = null,
    /**
     * Partes de um pagamento dividido. Omitido na venda de uma forma só,
     * para o payload antigo continuar igual.
     */
    @OptIn(ExperimentalSerializationApi::class)
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val payments: List<SalePaymentPartDto>? = null,
)

@Serializable
data class SalePaymentPartDto(
    val method: String,
    @SerialName("amount_cents") val amountCents: Long,
    val amount: Double,
    val status: String,
    val nsu: String? = null,
    val authorization: String? = null,
    val brand: String? = null,
    @SerialName("transaction_id") val transactionId: String? = null,
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
    @SerialName("holder_email")     val holderEmail: String? = null,
    val description: String,
    val quantity: Int,
    @SerialName("unit_price") val unitPrice: Double,
    val category: String? = null,
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
    /** Conteúdo exato do QR (= code no contrato atual Gate8). */
    @SerialName("qr_payload") val qrPayload: String? = null,
    /** Código curto de portaria (8 chars); impressão espaçada no app. */
    @SerialName("manual_code") val manualCode: String? = null,
    @SerialName("holder_name") val holderName: String? = null,
    @SerialName("event_name") val eventName: String? = null,
    @SerialName("batch_name") val batchName: String? = null,
    @SerialName("event_date") val eventDate: String? = null,
    val venue: String? = null,
    val price: Double? = null,
    val status: String? = null,
    @SerialName("status_label") val statusLabel: String? = null,
    @SerialName("issued_at") val issuedAt: String? = null,
    @SerialName("purchase_code") val purchaseCode: String? = null,
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
    val code: String? = null,
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

// --- Cashless (UID ↔ CPF/telefone) ---

@Serializable
data class CashlessCardDto(
    @SerialName("uid_hex") val uidHex: String,
    val name: String = "",
    val cpf: String,
    val phone: String,
    val blocked: Boolean = false,
    @SerialName("balance_cents") val balanceCents: Int = 0,
    @SerialName("balance_reais") val balanceReais: Double? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class CashlessCardLookupDto(
    val found: Boolean = false,
    val card: CashlessCardDto? = null,
)

@Serializable
data class CashlessCardResponseDto(
    val card: CashlessCardDto,
)

@Serializable
data class CashlessRegisterRequestDto(
    @SerialName("uid_hex") val uidHex: String,
    val name: String = "",
    val cpf: String,
    val phone: String,
    @SerialName("balance_cents") val balanceCents: Int = 0,
)

@Serializable
data class CashlessPatchRequestDto(
    @SerialName("balance_cents") val balanceCents: Int? = null,
    val blocked: Boolean? = null,
    /** Fallback de venda cashless quando o POST /sales falhou. */
    @SerialName("operator_name") val operatorName: String? = null,
    val items: List<SaleItemDto>? = null,
)

@Serializable
data class CashlessBlockByCpfRequestDto(
    val cpf: String,
)

@Serializable
data class CashlessReassignRequestDto(
    @SerialName("old_uid_hex") val oldUidHex: String,
    @SerialName("new_uid_hex") val newUidHex: String,
    @SerialName("balance_cents") val balanceCents: Int,
)

@Serializable
data class CashlessCloseResponseDto(
    val ok: Boolean = true,
    @SerialName("uid_hex") val uidHex: String? = null,
    val message: String? = null,
)
