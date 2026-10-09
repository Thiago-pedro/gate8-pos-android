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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class KitchenRepository(
    private val api: PosApiService,
    private val configStore: DeviceConfigStore,
    private val json: Json,
) {
    private val queueLock = Mutex()
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
    ): Int = queueLock.withLock {
        if (items.isEmpty()) return@withLock 0
        val existing = loadPending()
        val already = existing.firstOrNull { it.id == clientReference }
        if (already != null) {
            if (already.synced) return@withLock already.orderNumber
            val uploaded = upload(already, saleId, adoptServerNumber = true)
            if (uploaded != null) {
                savePending(existing.map { if (it.id == clientReference) uploaded else it })
                return@withLock uploaded.orderNumber
            }
            return@withLock already.orderNumber
        }

        val trimmedNote = note?.trim()?.take(80)?.takeIf { it.isNotBlank() }
        val order = KitchenOrder(
            id = clientReference,
            orderNumber = configStore.nextKitchenOrderNumber(),
            terminalName = terminalName,
            soldAtMillis = System.currentTimeMillis(),
            items = items,
            note = trimmedNote,
            synced = false,
        )
        val saved = upload(order, saleId, adoptServerNumber = true) ?: order
        if (!saved.synced) {
            Log.w(TAG, "Pedido ${saved.orderNumber} ficou na fila local. Nova tentativa em seguida.")
        }
        savePending(existing + saved)
        saved.orderNumber
    }

    /** Reenvia pedidos que a conveniência não conseguiu entregar ao painel. */
    suspend fun flushUnsynced() {
        queueLock.withLock {
            val pending = loadPending()
            if (pending.none { !it.synced }) return@withLock
            var changed = false
            val next = pending.map { order ->
                if (order.synced) {
                    order
                } else {
                    val uploaded = upload(order, saleId = null, adoptServerNumber = false)
                    if (uploaded != null) {
                        changed = true
                        Log.i(TAG, "Pedido ${uploaded.orderNumber} enviado ao painel na nova tentativa")
                        uploaded
                    } else {
                        order
                    }
                }
            }
            if (changed) savePending(next)
        }
    }

    suspend fun pollPending(): KitchenPollResult = queueLock.withLock {
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
        val remoteNumbers = remote.map { it.orderNumber }.filter { it > 0 }.toSet()
        val localKeep = local.filter { order ->
            !order.synced || order.orderNumber !in remoteNumbers
        }
        val merged = (remote + localKeep)
            .distinctBy { it.id }
            .filter { it.id !in printed && it.items.isNotEmpty() }
        KitchenPollResult(merged, apiAvailable)
    }

    suspend fun markPrinted(order: KitchenOrder) = queueLock.withLock {
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

    private suspend fun upload(
        order: KitchenOrder,
        saleId: String?,
        adoptServerNumber: Boolean,
    ): KitchenOrder? {
        val wireItems = if (order.note.isNullOrBlank()) {
            order.items
        } else {
            order.items + KitchenOrderItem(noteLine(order.note), 1)
        }
        return runCatching {
            val response = api.submitKitchenOrder(
                SubmitKitchenOrderRequestDto(
                    saleId = saleId,
                    clientReference = order.id,
                    terminalName = order.terminalName,
                    orderNumber = order.orderNumber.takeIf { it > 0 },
                    note = order.note,
                    items = wireItems.map { KitchenOrderItemDto(it.description, it.quantity) },
                ),
            )
            if (!response.isSuccessful) {
                Log.w(TAG, "POST kitchen/orders ${response.code()} — pedido ${order.orderNumber} segue pendente")
                return@runCatching null
            }
            val body = response.body()
            val serverId = body?.id?.takeIf { it.isNotBlank() }
            val serverNumber = body?.orderNumber?.takeIf { it > 0 }
            order.copy(
                synced = true,
                orderNumber = if (adoptServerNumber) serverNumber ?: order.orderNumber else order.orderNumber,
            ).also { saved ->
                if (serverId != null && serverId != order.id) {
                    Log.i(TAG, "Painel aceitou pedido ${saved.orderNumber} como $serverId")
                }
            }
        }.onFailure {
            Log.w(TAG, "POST kitchen/orders indisponível — pedido ${order.orderNumber} segue pendente", it)
        }.getOrNull()
    }

    /** Guarda os 4 pedidos de cozinha mais recentes, o mais novo primeiro. */
    fun rememberPrinted(order: KitchenOrder) {
        val next = (listOf(order) + recentOrders().filterNot { it.id == order.id }).take(4)
        configStore.setKitchenRecentJson(
            json.encodeToString(ListSerializer(KitchenOrder.serializer()), next),
        )
    }

    fun recentOrders(): List<KitchenOrder> {
        val raw = configStore.getKitchenRecentJson() ?: return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(KitchenOrder.serializer()), raw)
        }.getOrDefault(emptyList()).take(4)
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
