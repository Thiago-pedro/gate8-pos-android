package br.com.gate8.pos.kitchen

import android.util.Log
import br.com.gate8.pos.data.prefs.DeviceConfigStore
import br.com.gate8.pos.data.repository.KitchenRepository
import br.com.gate8.pos.domain.model.KitchenOrder
import br.com.gate8.pos.printer.KitchenOrderLine
import br.com.gate8.pos.printer.KitchenOrderPayload
import br.com.gate8.pos.printer.KitchenPrintOutcome
import br.com.gate8.pos.printer.ReceiptPrinter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicReference

data class KitchenUiState(
    val kitchenMode: Boolean = true,
    val apiAvailable: Boolean? = null,
    val recentOrders: List<KitchenOrder> = emptyList(),
    val status: String = "Ouvindo vendas da categoria Cozinha…",
    val error: String? = null,
    val printerWarning: String? = null,
    val lastPollAtMillis: Long? = null,
)

/**
 * A busca e a impressão rodam em ciclos separados. Um pedido que falha na
 * impressora não para o GET nem trava os outros da fila.
 */
class KitchenStation(
    private val printer: ReceiptPrinter,
    private val kitchenRepository: KitchenRepository,
    private val configStore: DeviceConfigStore,
    private val queue: KitchenPrintQueue,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val startLock = Any()
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val stopListener = AtomicReference<(() -> Unit)?>(null)
    private var pollJob: Job? = null
    private var printJob: Job? = null

    private val _state = MutableStateFlow(
        KitchenUiState(
            kitchenMode = configStore.isKitchenMode(),
            recentOrders = runCatching { kitchenRepository.recentOrders() }.getOrDefault(emptyList()),
        ),
    )
    val state: StateFlow<KitchenUiState> = _state.asStateFlow()

    fun setStopListener(listener: (() -> Unit)?) {
        stopListener.set(listener)
    }

    fun start() {
        synchronized(startLock) {
            if (pollJob?.isActive != true) {
                pollJob = scope.launch { runPoll() }
            }
            if (printJob?.isActive != true) {
                printJob = scope.launch { runPrint() }
            }
        }
    }

    fun stop() {
        synchronized(startLock) {
            pollJob?.cancel()
            printJob?.cancel()
            pollJob = null
            printJob = null
        }
        queue.releasePrinting()
    }

    /** Liga a escuta na hora. O estado nasce desligado e, se só o prefs mudar, a tela volta para a home. */
    fun markListening() {
        _state.update { it.copy(kitchenMode = true) }
    }

    fun requestStop() {
        _state.update { it.copy(kitchenMode = false) }
        stop()
        stopListener.get()?.invoke()
    }

    fun onScreenVisible() {
        _state.update {
            it.copy(
                kitchenMode = configStore.isKitchenMode(),
                recentOrders = runCatching { kitchenRepository.recentOrders() }.getOrDefault(it.recentOrders),
            )
        }
    }

    private suspend fun runPoll() {
        while (currentCoroutineContext().isActive) {
            try {
                cycle()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Ciclo da cozinha falhou", e)
                _state.update { it.copy(error = "Falha ao ouvir a fila. Tentando de novo.") }
            }
            delay(POLL_MS)
        }
    }

    private suspend fun cycle() {
        if (!configStore.isKitchenMode()) {
            requestStop()
            return
        }
        try {
            kitchenRepository.flushUnsynced()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Reenvio da fila local falhou", e)
        }
        retryAcks()
        val result = try {
            kitchenRepository.pollPending()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "GET kitchen/orders falhou", e)
            _state.update { it.copy(error = "Falha ao ouvir a fila. Tentando de novo.") }
            return
        }
        val normalized = result.orders.mapNotNull { order ->
            try {
                normalize(order)
            } catch (e: Exception) {
                Log.e(TAG, "Pedido ${order.id} ignorado", e)
                null
            }
        }
        if (normalized.isNotEmpty()) {
            runCatching { kitchenRepository.rememberSeen(normalized) }
                .onFailure { Log.e(TAG, "Não atualizei os últimos pedidos", it) }
        }
        val now = System.currentTimeMillis()
        _state.update {
            it.copy(
                kitchenMode = true,
                apiAvailable = result.apiAvailable,
                recentOrders = runCatching { kitchenRepository.recentOrders() }.getOrDefault(it.recentOrders),
                lastPollAtMillis = if (result.apiAvailable) now else it.lastPollAtMillis,
                error = null,
                status = if (result.apiAvailable) {
                    "Ouvindo vendas da categoria Cozinha"
                } else {
                    "Sem conexão com a fila"
                },
            )
        }
        if (queue.offer(normalized)) {
            wake.trySend(Unit)
        }
    }

    private suspend fun retryAcks() {
        for (order in queue.pendingAcks()) {
            try {
                acknowledge(order)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Falha ao confirmar o pedido ${order.orderNumber}", e)
            }
        }
    }

    private suspend fun runPrint() {
        queue.releasePrinting()
        while (currentCoroutineContext().isActive) {
            try {
                wake.receive()
                drain()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Fila de impressão falhou", e)
            }
        }
    }

    private suspend fun drain() {
        while (currentCoroutineContext().isActive) {
            if (!configStore.isKitchenMode()) return
            val order = queue.takeNext() ?: return
            try {
                printOne(order)
            } catch (e: CancellationException) {
                queue.finishPrint(order)
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Pedido ${order.orderNumber} falhou e a fila segue", e)
                queue.printFailed(order.id)
                showPrinterWarning(e.message)
            }
        }
    }

    private suspend fun printOne(order: KitchenOrder) {
        if (!configStore.isKitchenMode()) {
            queue.printFailed(order.id)
            return
        }
        val payload = try {
            toPayload(order)
        } catch (e: CancellationException) {
            queue.printFailed(order.id)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Pedido ${order.orderNumber} inválido para impressão", e)
            queue.printFailed(order.id)
            showPrinterWarning(e.message)
            return
        }
        val outcome = try {
            withTimeout(PRINT_WAIT_MS) { printer.printKitchenOrder(payload) }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Impressão do pedido ${order.orderNumber} estourou o tempo", e)
            KitchenPrintOutcome(ok = false, message = "timeout")
        } catch (e: CancellationException) {
            queue.finishPrint(order)
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Impressão do pedido ${order.orderNumber} falhou", e)
            KitchenPrintOutcome(ok = false, message = e.message)
        }
        if (!outcome.ok && kitchenPrintShouldRetry(outcome.message)) {
            Log.w(TAG, "Impressora não imprimiu o pedido ${order.orderNumber}: ${outcome.message}")
            queue.printFailed(order.id)
            showPrinterWarning(outcome.message)
            return
        }
        if (!outcome.ok) {
            Log.w(TAG, "Pedido ${order.orderNumber} não será impresso de novo: ${outcome.message}")
            queue.finishPrint(order)
            publishRecent(order)
            return
        }
        queue.printSucceeded(order)
        publishRecent(order)
        acknowledge(order)
    }

    private fun publishRecent(order: KitchenOrder) {
        runCatching { kitchenRepository.rememberPrinted(order) }
            .onFailure { Log.e(TAG, "Não guardei o pedido ${order.orderNumber} na tela", it) }
        _state.update {
            it.copy(
                recentOrders = runCatching { kitchenRepository.recentOrders() }.getOrDefault(it.recentOrders),
                status = "Pedido ${order.orderNumber} impresso",
                printerWarning = null,
                error = null,
            )
        }
    }

    private suspend fun acknowledge(order: KitchenOrder) {
        val ok = try {
            kitchenRepository.confirmPrinted(order)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "POST printed do pedido ${order.orderNumber} falhou", e)
            false
        }
        if (ok) {
            queue.ackSucceeded(order.id)
            Log.i(TAG, "Pedido ${order.orderNumber} confirmado na fila")
        } else {
            Log.w(TAG, "Pedido ${order.orderNumber} impresso, mas /printed falhou — nova tentativa sem reimprimir")
        }
    }

    private fun showPrinterWarning(message: String?) {
        _state.update { it.copy(printerWarning = kitchenPrinterWarning(message)) }
    }

    private fun normalize(order: KitchenOrder): KitchenOrder =
        if (order.orderNumber > 0) order else order.copy(orderNumber = stableOrderNumber(order.id))

    private fun stableOrderNumber(id: String): Int {
        val digits = id.filter { it.isDigit() }.takeLast(4).toIntOrNull()
        if (digits != null && digits > 0) return digits
        return (id.hashCode() and 0xFFFF).coerceAtLeast(1)
    }

    private fun toPayload(order: KitchenOrder) = KitchenOrderPayload(
        orderNumber = order.orderNumber,
        terminalName = order.terminalName.ifBlank { "PDV" },
        soldAtMillis = order.soldAtMillis,
        items = order.items.map { KitchenOrderLine(it.description, it.quantity.coerceAtLeast(1)) },
        note = order.note,
    )

    private companion object {
        const val TAG = "Gate8Kitchen"
        const val POLL_MS = 3_000L
        const val PRINT_WAIT_MS = 30_000L
    }
}
