package br.com.gate8.pos.domain.model

enum class PaymentMethodApi(val apiValue: String) {
    CREDIT("credit"),
    DEBIT("debit"),
    PIX("pix"),
    CASH("cash"),
    CASHLESS("cashless"),
    OTHER("other"),
    ;

    fun displayLabel(): String = when (this) {
        CREDIT -> "Crédito"
        DEBIT -> "Débito"
        PIX -> "Pix"
        CASH -> "Dinheiro"
        CASHLESS -> "Cashless"
        OTHER -> "Outro"
    }

    companion object {
        fun fromApiValue(value: String): PaymentMethodApi =
            entries.firstOrNull { it.apiValue == value } ?: OTHER
    }
}

enum class ItemType(val apiValue: String) {
    PRODUCT("product"),
    TICKET("ticket"),
    INVITE("invite"),
    CUSTOM("custom"),
}

data class CartLine(
    val itemType: ItemType,
    val productId: String? = null,
    val batchId: String? = null,
    val eventId: String? = null,
    val description: String,
    val quantity: Int,
    val unitPrice: Double,
    val holderName: String? = null,
    val holderEmail: String? = null,
) {
    val lineTotal: Double get() = quantity * unitPrice
}

enum class CheckinOutcome {
    Ok,
    Invalid,
    AlreadyUsed,
    WrongEvent,
    Unknown,
}

data class CheckinResult(
    val outcome: CheckinOutcome,
    val message: String,
    val holderName: String? = null,
)

/** Um ingresso emitido pelo backend, pronto para impressão térmica. */
data class IssuedTicket(
    val id: String,
    val code: String,
    val qrPayload: String,
    val manualCode: String,
    val holderName: String? = null,
    val eventName: String? = null,
    val batchName: String? = null,
    val eventDate: String? = null,
    val venue: String? = null,
    val price: Double? = null,
    val statusLabel: String? = null,
    val issuedAt: String? = null,
    val purchaseCode: String? = null,
)

/** Ingressos emitidos para um item da venda (item_index → tickets). */
data class SaleTicketGroup(
    val itemIndex: Int,
    val tickets: List<IssuedTicket>,
) {
    /** Códigos hex (compatibilidade). */
    val codes: List<String> get() = tickets.map { it.code }
}

data class SaleSuccess(
    val saleId: String,
    val duplicated: Boolean,
    val ticketGroups: List<SaleTicketGroup>,
    val purchaseCode: String? = null,
) {
    /** Lista achatada dos códigos (compatibilidade com quem só precisa dos códigos). */
    val ticketCodes: List<String> get() = ticketGroups.flatMap { it.codes }
}
