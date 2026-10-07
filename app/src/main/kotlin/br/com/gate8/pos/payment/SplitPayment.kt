package br.com.gate8.pos.payment

import br.com.gate8.pos.data.prefs.SplitPaymentStore
import br.com.gate8.pos.data.remote.dto.SalePaymentPartDto
import br.com.gate8.pos.domain.model.CartLine
import br.com.gate8.pos.domain.model.ItemType
import br.com.gate8.pos.domain.model.LastSalePaymentRecord
import br.com.gate8.pos.domain.model.PaymentMethodApi
import kotlinx.serialization.Serializable
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

enum class SplitStep {
    Summary,
    PickMethod,
    EnterAmount,
}

@Serializable
data class SplitCartLineRecord(
    val itemType: String,
    val productId: String? = null,
    val batchId: String? = null,
    val eventId: String? = null,
    val description: String,
    val quantity: Int,
    val unitPrice: Double,
    val holderName: String? = null,
    val holderEmail: String? = null,
    val category: String? = null,
)

@Serializable
data class SplitPartRecord(
    val id: String,
    val method: String,
    val amountCents: Long,
    val status: String,
    val nsu: String = "",
    val authorization: String = "",
    val brand: String = "",
    val transactionId: String = "",
)

@Serializable
data class SplitSessionRecord(
    val scope: String,
    val clientReference: String,
    val totalCents: Long,
    val parts: List<SplitPartRecord> = emptyList(),
    val cartLines: List<SplitCartLineRecord> = emptyList(),
    val kitchenNote: String? = null,
) {
    fun paidCents(): Long = parts.sumOf { it.amountCents }

    fun remainingCents(): Long = totalCents - paidCents()
}

data class SplitPaymentUi(
    val session: SplitSessionRecord,
    val step: SplitStep = SplitStep.Summary,
    val draftMethod: PaymentMethodApi? = null,
    val draftAmount: String = "",
    val error: String? = null,
    val confirmingCancel: Boolean = false,
    val cancelReport: String? = null,
)

data class PrepaidCheckout(
    val clientReference: String,
    val method: PaymentMethodApi,
    val pay: PaymentResult,
    val paymentLabel: String,
    val salePayments: List<SalePaymentPartDto>,
    val localPayments: List<LastSalePaymentRecord>,
    val kitchenNote: String? = null,
)

sealed class SplitCancelOutcome {
    data class Cleared(val message: String) : SplitCancelOutcome()
    data class StillOpen(val ui: SplitPaymentUi) : SplitCancelOutcome()
}

object SplitPayments {
    fun cartTotalCents(cart: List<CartLine>): Long =
        cart.sumOf { line -> MoneyCents.fromReais(line.unitPrice) * line.quantity.coerceAtLeast(0) }

    fun validatePart(session: SplitSessionRecord, amountCents: Long): String? = when {
        amountCents <= 0L -> "Informe um valor maior que zero."
        session.remainingCents() <= 0L -> "Essa venda já está quitada."
        amountCents > session.remainingCents() ->
            "O valor passa do restante de R$ ${MoneyCents.format(session.remainingCents())}."
        else -> null
    }

    fun receiptLabel(parts: List<SplitPartRecord>): String =
        parts.joinToString("\n") { part ->
            val method = PaymentMethodApi.fromApiValue(part.method)
            val value = "R$ ${MoneyCents.format(part.amountCents)}"
            val line = "${method.displayLabel()}: $value"
            if (part.nsu.isBlank()) line else "$line\nNSU: ${part.nsu}"
        }

    fun toPrepaid(session: SplitSessionRecord): PrepaidCheckout {
        val methods = session.parts.map { PaymentMethodApi.fromApiValue(it.method) }.distinct()
        val method = if (methods.size == 1) methods.first() else PaymentMethodApi.OTHER
        val electronic = session.parts.filter { part ->
            val kind = PaymentMethodApi.fromApiValue(part.method)
            kind != PaymentMethodApi.CASH && kind != PaymentMethodApi.CASHLESS
        }
        val onlyElectronic = electronic.singleOrNull()
        val pay = PaymentResult(
            method = method,
            nsu = onlyElectronic?.nsu.orEmpty(),
            authorization = onlyElectronic?.authorization.orEmpty(),
            brand = onlyElectronic?.brand.orEmpty(),
            transactionId = onlyElectronic?.transactionId.orEmpty(),
        )
        return PrepaidCheckout(
            clientReference = session.clientReference,
            method = method,
            pay = pay,
            paymentLabel = receiptLabel(session.parts),
            salePayments = session.parts.map { it.toDto() },
            localPayments = session.parts.map { it.toLocal() },
            kitchenNote = session.kitchenNote,
        )
    }

    fun statusLabel(part: SplitPartRecord): String = when (part.status) {
        "CONFIRMED" -> "Confirmado"
        else -> "Aprovado"
    }
}

private fun SplitPartRecord.toDto() = SalePaymentPartDto(
    method = method,
    amountCents = amountCents,
    amount = MoneyCents.toReais(amountCents),
    status = status,
    nsu = nsu.takeIf { it.isNotBlank() },
    authorization = authorization.takeIf { it.isNotBlank() },
    brand = brand.takeIf { it.isNotBlank() },
    transactionId = transactionId.takeIf { it.isNotBlank() },
)

private fun SplitPartRecord.toLocal() = LastSalePaymentRecord(
    method = method,
    amountCents = amountCents,
    nsu = nsu.takeIf { it.isNotBlank() },
    authorization = authorization.takeIf { it.isNotBlank() },
    transactionId = transactionId.takeIf { it.isNotBlank() },
    brand = brand.takeIf { it.isNotBlank() },
)

fun CartLine.toSplitRecord() = SplitCartLineRecord(
    itemType = itemType.apiValue,
    productId = productId,
    batchId = batchId,
    eventId = eventId,
    description = description,
    quantity = quantity,
    unitPrice = unitPrice,
    holderName = holderName,
    holderEmail = holderEmail,
    category = category,
)

fun SplitCartLineRecord.toCartLine() = CartLine(
    itemType = ItemType.entries.firstOrNull { it.apiValue == itemType } ?: ItemType.PRODUCT,
    productId = productId,
    batchId = batchId,
    eventId = eventId,
    description = description,
    quantity = quantity,
    unitPrice = unitPrice,
    holderName = holderName,
    holderEmail = holderEmail,
    category = category,
)

class SplitPaymentController(
    private val paymentGateway: PaymentGateway,
    private val store: SplitPaymentStore,
    private val scope: String,
) {
    private val charging = AtomicBoolean(false)

    fun restore(): SplitPaymentUi? {
        val saved = store.load(scope) ?: return null
        if (saved.parts.isEmpty()) {
            store.clear(scope)
            return null
        }
        return SplitPaymentUi(session = saved)
    }

    fun begin(
        cart: List<CartLine>,
        kitchenNote: String?,
        newClientReference: String,
    ): SplitPaymentUi {
        val saved = store.load(scope)
        val lines = when {
            cart.isNotEmpty() -> cart
            saved != null -> saved.cartLines.map { it.toCartLine() }
            else -> emptyList()
        }
        val session = if (saved != null && saved.parts.isNotEmpty()) {
            saved.copy(
                totalCents = SplitPayments.cartTotalCents(lines),
                cartLines = lines.map { it.toSplitRecord() },
                kitchenNote = kitchenNote ?: saved.kitchenNote,
            )
        } else {
            SplitSessionRecord(
                scope = scope,
                clientReference = newClientReference,
                totalCents = SplitPayments.cartTotalCents(lines),
                cartLines = lines.map { it.toSplitRecord() },
                kitchenNote = kitchenNote,
            )
        }
        store.save(session)
        return SplitPaymentUi(session = session)
    }

    fun pickMethod(ui: SplitPaymentUi, method: PaymentMethodApi): SplitPaymentUi {
        val remaining = ui.session.remainingCents()
        return ui.copy(
            step = SplitStep.EnterAmount,
            draftMethod = method,
            draftAmount = if (remaining > 0L) MoneyCents.format(remaining) else "",
            error = null,
        )
    }

    fun editAmount(ui: SplitPaymentUi, value: String): SplitPaymentUi =
        ui.copy(draftAmount = value.take(12), error = null)

    fun backToSummary(ui: SplitPaymentUi): SplitPaymentUi =
        ui.copy(step = SplitStep.Summary, draftMethod = null, error = null, confirmingCancel = false)

    suspend fun charge(ui: SplitPaymentUi): SplitPaymentUi {
        val method = ui.draftMethod ?: return ui.copy(error = "Escolha a forma de pagamento.")
        val cents = MoneyCents.parse(ui.draftAmount)
            ?: return ui.copy(error = "Informe um valor maior que zero.")
        val invalid = SplitPayments.validatePart(ui.session, cents)
        if (invalid != null) return ui.copy(error = invalid)
        if (!charging.compareAndSet(false, true)) {
            return ui.copy(error = "Já existe uma cobrança em andamento.")
        }
        try {
            val partId = UUID.randomUUID().toString()
            val result = if (method == PaymentMethodApi.CASH) {
                PaymentResult(
                    method = method,
                    nsu = "",
                    authorization = "",
                    brand = "",
                    transactionId = "",
                )
            } else {
                paymentGateway.chargeResilient(
                    MoneyCents.toReais(cents),
                    method,
                    "${ui.session.clientReference}:$partId",
                )
            }
            if (ui.session.parts.any { it.id == partId }) return ui
            val part = SplitPartRecord(
                id = partId,
                method = method.apiValue,
                amountCents = cents,
                status = if (method == PaymentMethodApi.CASH) "CONFIRMED" else "APPROVED",
                nsu = result.nsu,
                authorization = result.authorization,
                brand = result.brand,
                transactionId = result.transactionId,
            )
            val updated = ui.session.copy(parts = ui.session.parts + part)
            store.save(updated)
            return SplitPaymentUi(session = updated)
        } finally {
            charging.set(false)
        }
    }

    suspend fun cancel(ui: SplitPaymentUi): SplitCancelOutcome {
        val notes = mutableListOf<String>()
        val kept = mutableListOf<SplitPartRecord>()
        for (part in ui.session.parts) {
            val method = PaymentMethodApi.fromApiValue(part.method)
            val label = "${method.displayLabel()} R$ ${MoneyCents.format(part.amountCents)}"
            if (method == PaymentMethodApi.CASH || method == PaymentMethodApi.CASHLESS) {
                notes += "$label estava confirmado. Devolva esse valor ao cliente."
                continue
            }
            if (part.transactionId.isBlank()) {
                kept += part
                notes += "$label está aprovado, mas a Cielo não devolveu um ID para estorno. A cobrança continua ativa."
                continue
            }
            val voided = runCatching {
                paymentGateway.voidTransaction(
                    transactionId = part.transactionId,
                    nsu = part.nsu,
                    amount = MoneyCents.toReais(part.amountCents),
                    method = method,
                    authorization = part.authorization,
                )
            }.getOrElse { VoidResult(false, it.message ?: "Falha no estorno") }
            if (voided.success) {
                notes += "$label estornado."
            } else {
                kept += part
                notes += "$label continua aprovado. ${voided.message}"
            }
        }
        val message = notes.joinToString("\n")
        if (kept.isEmpty()) {
            store.clear(scope)
            return SplitCancelOutcome.Cleared(message)
        }
        val updated = ui.session.copy(parts = kept)
        store.save(updated)
        return SplitCancelOutcome.StillOpen(
            SplitPaymentUi(session = updated, cancelReport = message),
        )
    }

    fun clear() {
        store.clear(scope)
    }
}
