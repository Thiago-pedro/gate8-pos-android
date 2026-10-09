package br.com.gate8.pos.ui.kitchen

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.gate8.pos.data.prefs.DeviceConfigStore
import br.com.gate8.pos.data.repository.KitchenRepository
import br.com.gate8.pos.domain.model.KitchenOrder
import br.com.gate8.pos.printer.KitchenOrderLine
import br.com.gate8.pos.printer.KitchenOrderPayload
import br.com.gate8.pos.printer.ReceiptPrinter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class KitchenUiState(
    val kitchenMode: Boolean = true,
    val apiAvailable: Boolean? = null,
    val recentOrders: List<KitchenOrder> = emptyList(),
    val status: String = "Ouvindo vendas da categoria Cozinha…",
    val error: String? = null,
)

class KitchenViewModel(
    private val printer: ReceiptPrinter,
    private val kitchenRepository: KitchenRepository,
    private val configStore: DeviceConfigStore,
) : ViewModel() {
    private val _state = MutableStateFlow(
        KitchenUiState(
            kitchenMode = configStore.isKitchenMode(),
            recentOrders = kitchenRepository.recentOrders(),
        ),
    )
    val state: StateFlow<KitchenUiState> = _state.asStateFlow()

    private val printMutex = Mutex()

    init {
        viewModelScope.launch {
            while (isActive) {
                try {
                    kitchenRepository.flushUnsynced()
                    pollAndPrint()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Falha ao ouvir a cozinha", e)
                    _state.update {
                        it.copy(error = e.message ?: "Falha ao ouvir a fila. Tentando de novo.")
                    }
                }
                delay(2_500)
            }
        }
    }

    fun onScreenVisible() {
        _state.update {
            it.copy(
                kitchenMode = configStore.isKitchenMode(),
                recentOrders = kitchenRepository.recentOrders(),
                error = null,
            )
        }
    }

    fun exitKitchen() {
        configStore.setKitchenMode(false)
        _state.update { it.copy(kitchenMode = false) }
    }

    private suspend fun pollAndPrint() {
        if (!configStore.isKitchenMode()) {
            _state.update { it.copy(kitchenMode = false) }
            return
        }
        val result = runCatching { kitchenRepository.pollPending() }
            .onFailure { e ->
                _state.update { it.copy(error = e.message ?: "Falha ao ouvir a fila") }
            }
            .getOrNull() ?: return

        val offline = !result.apiAvailable
        _state.update {
            it.copy(
                kitchenMode = true,
                apiAvailable = result.apiAvailable,
                error = if (offline) {
                    "Sem conexão com a fila. Pedido de outra maquininha espera a internet voltar."
                } else {
                    null
                },
                status = if (offline) {
                    "Sem conexão com a fila"
                } else {
                    "Ouvindo vendas da categoria Cozinha"
                },
            )
        }

        result.orders.forEach { incoming ->
            val order = if (incoming.orderNumber > 0) {
                incoming
            } else {
                incoming.copy(orderNumber = stableOrderNumber(incoming.id))
            }
            if (!configStore.isKitchenMode()) return
            printMutex.withLock {
                if (!configStore.isKitchenMode()) return@withLock
                val ok = try {
                    printer.printKitchenOrder(toPayload(order))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Impressão do pedido ${order.orderNumber} falhou", e)
                    false
                }
                if (!ok) {
                    Log.w(TAG, "Impressora não confirmou o pedido ${order.orderNumber}. Não vou repetir.")
                }
                runCatching { kitchenRepository.markPrinted(order) }
                    .onFailure { e -> Log.e(TAG, "Pedido impresso, mas a fila não foi atualizada", e) }
                kitchenRepository.rememberPrinted(order)
            }
            _state.update {
                it.copy(
                    recentOrders = kitchenRepository.recentOrders(),
                    status = "Pedido ${order.orderNumber} impresso",
                    error = null,
                )
            }
        }
    }

    /** Número estável quando o painel não devolveu o pedido. Não muda a cada tentativa. */
    private fun stableOrderNumber(id: String): Int {
        val digits = id.filter { it.isDigit() }.takeLast(4).toIntOrNull()
        if (digits != null && digits > 0) return digits
        return (id.hashCode() and 0xFFFF).coerceAtLeast(1)
    }

    private fun toPayload(order: KitchenOrder) = KitchenOrderPayload(
        orderNumber = order.orderNumber,
        terminalName = order.terminalName,
        soldAtMillis = order.soldAtMillis,
        items = order.items.map { KitchenOrderLine(it.description, it.quantity) },
        note = order.note,
    )

    private companion object {
        const val TAG = "Gate8Kitchen"
    }
}
