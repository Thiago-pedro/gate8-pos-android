package br.com.gate8.pos.kitchen

import br.com.gate8.pos.domain.model.KitchenOrder
import br.com.gate8.pos.domain.model.KitchenOrderItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class KitchenParseBatch(
    val orders: List<KitchenOrder>,
    val errors: List<String>,
)

/**
 * Lê a resposta do GET sem deixar um pedido malformado derrubar a leva inteira.
 * Itens que começam com "OBS:" viram observação (mesa), não produto.
 */
object KitchenOrderParser {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    private const val NOTE_PREFIX = "OBS:"
    private val MAX_SOLD_AT = 4_102_444_800_000L
    private val dayMonthFormats = listOf(
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"),
    )

    fun noteLine(note: String): String = "$NOTE_PREFIX $note"

    fun parseList(
        payload: String,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): KitchenParseBatch {
        if (payload.isBlank()) return KitchenParseBatch(emptyList(), emptyList())
        val root = try {
            json.parseToJsonElement(payload)
        } catch (e: Exception) {
            return KitchenParseBatch(emptyList(), listOf(e.message ?: "json inválido"))
        }
        val elements = when (root) {
            is JsonArray -> root
            is JsonObject -> buildList {
                (root["orders"] as? JsonArray)?.let { addAll(it) }
                (root["pending"] as? JsonArray)?.let { addAll(it) }
            }
            else -> return KitchenParseBatch(emptyList(), listOf("resposta inesperada"))
        }
        val errors = mutableListOf<String>()
        val orders = elements.mapNotNull { element ->
            try {
                if (element !is JsonObject) {
                    errors += "pedido com formato inválido"
                    return@mapNotNull null
                }
                parseOrder(element, now, zone, errors)
            } catch (e: Exception) {
                errors += e.message ?: "pedido inválido"
                null
            }
        }.distinctBy { it.id }
        return KitchenParseBatch(orders, errors)
    }

    fun parseSoldAtOrNull(value: String?, zone: ZoneId = ZoneId.systemDefault()): Long? {
        if (value.isNullOrBlank()) return null
        val trimmed = value.trim()
        trimmed.toLongOrNull()?.let { raw ->
            val millis = if (raw < 10_000_000_000L) raw * 1000 else raw
            return millis.coerceIn(0L, MAX_SOLD_AT)
        }
        trimmed.toDoubleOrNull()?.let { raw ->
            val whole = raw.toLong()
            val millis = if (whole < 10_000_000_000L) whole * 1000 else whole
            return millis.coerceIn(0L, MAX_SOLD_AT)
        }
        runCatching { Instant.parse(trimmed).toEpochMilli() }.getOrNull()?.let { return it }
        runCatching { OffsetDateTime.parse(trimmed).toInstant().toEpochMilli() }.getOrNull()?.let { return it }
        val local = trimmed.replace(' ', 'T').take(19)
        runCatching {
            LocalDateTime.parse(local).atZone(zone).toInstant().toEpochMilli()
        }.getOrNull()?.let { return it }
        for (format in dayMonthFormats) {
            val parsed = runCatching {
                LocalDateTime.parse(trimmed, format).atZone(zone).toInstant().toEpochMilli()
            }.getOrNull()
            if (parsed != null) return parsed
        }
        return null
    }

    private fun parseOrder(
        obj: JsonObject,
        now: Long,
        zone: ZoneId,
        errors: MutableList<String>,
    ): KitchenOrder? {
        val id = obj["id"].asText()?.trim().orEmpty()
            .ifEmpty { obj["client_reference"].asText()?.trim().orEmpty() }
        if (id.isEmpty()) {
            errors += "pedido sem id"
            return null
        }
        val rawItems = when (val items = obj["items"]) {
            is JsonArray -> items.mapNotNull { parseItem(it) }
            null, is JsonNull -> emptyList()
            else -> {
                errors += "itens inválidos no pedido $id"
                emptyList()
            }
        }
        val (food, noteFromItems) = splitNote(rawItems)
        val explicit = obj["note"].asText()?.trim()?.takeIf { it.isNotBlank() }
        val note = explicit ?: noteFromItems
        if (food.isEmpty() && note.isNullOrBlank()) {
            errors += "pedido $id sem itens imprimíveis"
            return null
        }
        val soldElement = obj["sold_at"] ?: obj["created_at"]
        val soldRaw = soldElement.asText()
        if (soldElement != null && soldElement !is JsonNull && soldRaw == null) {
            errors += "sold_at inválido no pedido $id"
        }
        val parsedSoldAt = parseSoldAtOrNull(soldRaw, zone)
        if (!soldRaw.isNullOrBlank() && parsedSoldAt == null) {
            errors += "sold_at inválido no pedido $id"
        }
        val terminal = obj["terminal_name"].asText()?.trim()?.takeIf { it.isNotBlank() } ?: "PDV"
        return KitchenOrder(
            id = id,
            orderNumber = obj["order_number"].asInt() ?: 0,
            terminalName = terminal,
            soldAtMillis = (parsedSoldAt ?: now).coerceIn(0L, MAX_SOLD_AT),
            items = food,
            note = note,
        )
    }

    private fun parseItem(element: JsonElement): KitchenOrderItem? {
        val obj = element as? JsonObject ?: return null
        val description = obj["description"].asText()?.trim().orEmpty()
        if (description.isEmpty()) return null
        val quantity = (obj["quantity"].asInt() ?: 1).coerceAtLeast(1)
        return KitchenOrderItem(description, quantity)
    }

    private fun splitNote(items: List<KitchenOrderItem>): Pair<List<KitchenOrderItem>, String?> {
        val notes = mutableListOf<String>()
        val food = mutableListOf<KitchenOrderItem>()
        for (item in items) {
            val description = item.description.trim()
            if (description.startsWith(NOTE_PREFIX, ignoreCase = true)) {
                description.substring(NOTE_PREFIX.length).trim().takeIf { it.isNotBlank() }?.let { notes += it }
            } else if (description.isNotBlank()) {
                food += item.copy(description = description, quantity = item.quantity.coerceAtLeast(1))
            }
        }
        return food to notes.joinToString(" · ").takeIf { it.isNotBlank() }
    }

    private fun JsonElement?.asText(): String? = when (this) {
        null, is JsonNull -> null
        is JsonPrimitive -> content
        else -> null
    }

    private fun JsonElement?.asInt(): Int? {
        val text = asText() ?: return null
        text.toIntOrNull()?.let { return it }
        return text.toDoubleOrNull()?.toInt()
    }
}
