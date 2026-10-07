package br.com.gate8.pos.data.repository

import android.util.Log
import br.com.gate8.pos.data.prefs.DeviceConfigStore
import br.com.gate8.pos.data.remote.api.PosApiService
import br.com.gate8.pos.data.remote.dto.KitchenOrderDto
import br.com.gate8.pos.data.remote.dto.KitchenOrderItemDto
import br.com.gate8.pos.data.remote.dto.SubmitKitchenOrderRequestDto
import br.com.gate8.pos.domain.model.CartLine
import br.com.gate8.pos.domain.model.KitchenCategory
import br.com.gate8.pos.domain.model.KitchenOrder
import br.com.gate8.pos.domain.model.KitchenOrderItem
import br.com.gate8.pos.domain.model.KitchenPollResult
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class KitchenRepository(
    private val api: PosApiService,
    private val configStore: DeviceConfigStore,
    private val json: Json,
) {
    fun foodItemsFrom(cart: List<CartLine>): List<KitchenOrderItem> =
        cart.filter { KitchenCategory.matches(it.category) }
            .map { KitchenOrderItem(it.description.trim(), it.quantity.coerceAtLeast(1)) }
            .filter { it.description.isNotBlank() }

    suspend fun submitFromSale(
        saleId: String?,
        clientReference: String,
        cart: List<CartLine>,
        note: String? = null,
    ): Int? {
        val items = foodItemsFrom(cart)
        if (items.isEmpty()) return null
        val terminal = configStore.getDeviceName()?.takeIf { it.isNotBlank() }
            ?: configStore.getDeviceShortId()
        return submit(saleId, clientReference, terminal, items, note)
    }

    suspend fun submit(
        saleId: String?,
        clientReference: String,
        terminalName: String,
        items: List<KitchenOrderItem>,
        note: String? = null,
    ): Int {
        if (items.isEmpty()) return 0
        val existing = loadPending()
        if (existing.any { it.id == clientReference }) {
            return existing.first { it.id == clientReference }.orderNumber
        }

        val trimmedNote = note?.trim()?.take(80)?.takeIf { it.isNotBlank() }
        val orderNumber = configStore.nextKitchenOrderNumber()
        var order = KitchenOrder(
            id = clientReference,
            orderNumber = orderNumber,
            terminalName = terminalName,
            soldAtMillis = System.currentTimeMillis(),
            items = items,
            note = trimmedNote,
        )
        val wireItems = if (trimmedNote == null) {
            items
        } else {
            items + KitchenOrderItem(noteLine(trimmedNote), 1)
        }

        runCatching {
            val response = api.submitKitchenOrder(
                SubmitKitchenOrderRequestDto(
                    saleId = saleId,
                    clientReference = clientReference,
                    terminalName = terminalName,
                    note = trimmedNote,
                    items = wireItems.map { KitchenOrderItemDto(it.description, it.quantity) },
                ),
            )
            if (response.isSuccessful) {
                val body = response.body()
                val serverNumber = body?.orderNumber
                val serverId = body?.id?.takeIf { it.isNotBlank() }
                if (serverNumber != null || serverId != null) {
                    order = order.copy(
                        id = serverId ?: order.id,
                        orderNumber = serverNumber ?: order.orderNumber,
                    )
                }
            } else {
                Log.i(TAG, "POST kitchen/orders ${response.code()} — fila local neste aparelho")
            }
        }.onFailure { Log.i(TAG, "POST kitchen/orders indisponível — fila local", it) }

        savePending(existing + order)
        return order.orderNumber
    }

    suspend fun pollPending(): KitchenPollResult {
        var apiAvailable = false
        val remote = runCatching {
            val response = api.getKitchenOrders()
            when {
                response.isSuccessful -> {
                    apiAvailable = true
                    val body = response.body()
                    (body?.orders.orEmpty() + body?.pending.orEmpty())
                        .distinctBy { it.id }
                        .map { it.toDomain() }
                }
                response.code() == 404 -> emptyList()
                else -> {
                    Log.w(TAG, "GET kitchen/orders ${response.code()}")
                    emptyList()
                }
            }
        }.onFailure {
            Log.i(TAG, "GET kitchen/orders indisponível — fila local", it)
        }.getOrDefault(emptyList())

        val printed = configStore.getKitchenPrintedIds()
        val local = loadPending()
        val merged = (remote + local)
            .distinctBy { it.id }
            .filter { it.id !in printed && it.items.isNotEmpty() }
        return KitchenPollResult(merged, apiAvailable)
    }

    suspend fun markPrinted(order: KitchenOrder) {
        configStore.addKitchenPrintedId(order.id)
        savePending(loadPending().filterNot { it.id == order.id })
        runCatching {
            val response = api.markKitchenOrderPrinted(order.id)
            if (!response.isSuccessful) {
                Log.i(TAG, "POST kitchen/orders/${order.id}/printed ${response.code()}")
            }
        }.onFailure {
            Log.i(TAG, "POST kitchen/orders/${order.id}/printed indisponível", it)
        }
    }

    fun nextLocalOrderNumber(): Int = configStore.nextKitchenOrderNumber()

    fun clearQueue() {
        configStore.clearKitchenQueue()
    }

    private fun loadPending(): List<KitchenOrder> {
        val raw = configStore.getKitchenPendingJson() ?: return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(KitchenOrder.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    private fun savePending(orders: List<KitchenOrder>) {
        configStore.setKitchenPendingJson(
            json.encodeToString(ListSerializer(KitchenOrder.serializer()), orders),
        )
    }

    private fun KitchenOrderDto.toDomain(): KitchenOrder {
        val parsed = items.map { KitchenOrderItem(it.description, it.quantity.coerceAtLeast(1)) }
        val (food, embeddedNote) = splitKitchenNote(parsed)
        val note = note?.trim()?.takeIf { it.isNotBlank() } ?: embeddedNote
        return KitchenOrder(
            id = id,
            orderNumber = orderNumber ?: 0,
            terminalName = terminalName?.takeIf { it.isNotBlank() } ?: "PDV",
            soldAtMillis = parseSoldAt(soldAt),
            items = food,
            note = note,
        )
    }

    private fun splitKitchenNote(
        items: List<KitchenOrderItem>,
    ): Pair<List<KitchenOrderItem>, String?> {
        val note = items.firstOrNull { it.description.startsWith(NOTE_PREFIX) }
            ?.description
            ?.removePrefix(NOTE_PREFIX)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        return items.filterNot { it.description.startsWith(NOTE_PREFIX) } to note
    }

    private fun parseSoldAt(value: String?): Long {
        if (value.isNullOrBlank()) return System.currentTimeMillis()
        value.toLongOrNull()?.let { millis ->
            return if (millis < 10_000_000_000L) millis * 1000 else millis
        }
        return System.currentTimeMillis()
    }

    companion object {
        private const val TAG = "Gate8Kitchen"
        private const val NOTE_PREFIX = "OBS: "

        private fun noteLine(note: String) = NOTE_PREFIX + note
    }
}
