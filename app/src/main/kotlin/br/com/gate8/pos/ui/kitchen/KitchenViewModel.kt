package br.com.gate8.pos.ui.kitchen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.gate8.pos.data.prefs.DeviceConfigStore
import br.com.gate8.pos.data.repository.KitchenRepository
import br.com.gate8.pos.domain.model.KitchenOrder
import br.com.gate8.pos.printer.KitchenOrderLine
import br.com.gate8.pos.printer.KitchenOrderPayload
import br.com.gate8.pos.printer.ReceiptPrinter
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
    val lastOrderNumber: Int? = null,
    val lastItemsLabel: String? = null,
    val status: String = "Ouvindo vendas da categoria Cozinha…",
    val printing: Boolean = false,
    val error: String? = null,
)

class KitchenViewModel(
    private val printer: ReceiptPrinter,
    private val kitchenRepository: KitchenRepository,
    private val configStore: DeviceConfigStore,
) : ViewModel() {
    private val _state = MutableStateFlow(KitchenUiState(kitchenMode = configStore.isKitchenMode()))
    val state: StateFlow<KitchenUiState> = _state.asStateFlow()

    private val printMutex = Mutex()

    init {
        viewModelScope.launch {
            while (isActive) {
                pollAndPrint()
                delay(2_500)
            }
        }
    }

    fun onScreenVisible() {
        _state.update { it.copy(kitchenMode = configStore.isKitchenMode(), error = null) }
    }

    fun printTestOrder() {
        viewModelScope.launch {
            printMutex.withLock {
                _state.update { it.copy(printing = true, error = null) }
                val number = kitchenRepository.nextLocalOrderNumber()
                val terminal = configStore.getDeviceName()?.takeIf { it.isNotBlank() }
                    ?: configStore.getDeviceShortId()
                printer.printKitchenOrder(
                    KitchenOrderPayload(
                        orderNumber = number,
                        terminalName = terminal,
                        soldAtMillis = System.currentTimeMillis(),
                        items = listOf(
                            KitchenOrderLine("X-Burger", 2),
                            KitchenOrderLine("Batata frita", 1),
                        ),
                    ),
                )
                _state.update {
                    it.copy(
                        printing = false,
                        lastOrderNumber = number,
                        lastItemsLabel = "2x X-Burger, 1x Batata frita",
                        status = "Pedido teste impresso",
                    )
                }
            }
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

        _state.update {
            it.copy(
                kitchenMode = true,
                apiAvailable = result.apiAvailable,
                error = null,
                status = if (result.apiAvailable) {
                    "Ouvindo vendas da categoria Cozinha"
                } else {
                    "Ouvindo neste aparelho"
                },
            )
        }

        result.orders.forEach { incoming ->
            val order = if (incoming.orderNumber <= 0) {
                incoming.copy(orderNumber = kitchenRepository.nextLocalOrderNumber())
            } else {
                incoming
            }
            printMutex.withLock {
                printer.printKitchenOrder(toPayload(order))
                kitchenRepository.markPrinted(order)
                _state.update {
                    it.copy(
                        lastOrderNumber = order.orderNumber,
                        lastItemsLabel = order.items.joinToString { item ->
                            "${item.quantity}x ${item.description}"
                        },
                        status = "Pedido ${order.orderNumber} impresso",
                    )
                }
            }
        }
    }

    private fun toPayload(order: KitchenOrder) = KitchenOrderPayload(
        orderNumber = order.orderNumber,
        terminalName = order.terminalName,
        soldAtMillis = order.soldAtMillis,
        items = order.items.map { KitchenOrderLine(it.description, it.quantity) },
        note = order.note,
    )
}
