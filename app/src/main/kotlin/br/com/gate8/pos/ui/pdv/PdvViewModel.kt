package br.com.gate8.pos.ui.pdv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.gate8.pos.core.sale.PendingSaleSync
import br.com.gate8.pos.core.sale.SaleAdminService
import br.com.gate8.pos.core.network.ApiException
import br.com.gate8.pos.core.util.ClientReferenceGenerator
import br.com.gate8.pos.data.local.entity.PendingSaleEntity
import br.com.gate8.pos.data.local.entity.PendingSaleStatus
import br.com.gate8.pos.data.prefs.DeviceConfigStore
import br.com.gate8.pos.data.remote.dto.CatalogResponseDto
import br.com.gate8.pos.data.remote.dto.EventCatalogDto
import br.com.gate8.pos.data.remote.dto.TicketBatchDto
import br.com.gate8.pos.data.remote.dto.CreateSaleRequestDto
import br.com.gate8.pos.data.remote.dto.SaleItemDto
import br.com.gate8.pos.data.remote.dto.StonePaymentDto
import br.com.gate8.pos.data.repository.CatalogRepository
import br.com.gate8.pos.data.repository.SaleRepository
import br.com.gate8.pos.domain.model.CartLine
import br.com.gate8.pos.domain.model.ItemType
import br.com.gate8.pos.domain.model.PaymentMethodApi
import br.com.gate8.pos.payment.PaymentGateway
import br.com.gate8.pos.printer.ReceiptPrinter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

data class PdvUiState(
    val loading: Boolean = false,
    val catalog: CatalogResponseDto? = null,
    val catalogVersion: Int = 0,
    val selectedEventId: String? = null,
    val cart: List<CartLine> = emptyList(),
    val message: String? = null,
    val error: String? = null,
    val lastSaleId: String? = null,
    val lastTicketCodes: List<String> = emptyList(),
    val showCart: Boolean = false,
)

class PdvViewModel(
    private val catalogRepository: CatalogRepository,
    private val saleRepository: SaleRepository,
    private val paymentGateway: PaymentGateway,
    private val printer: ReceiptPrinter,
    private val saleAdmin: SaleAdminService,
    private val pendingSaleSync: PendingSaleSync,
    private val configStore: DeviceConfigStore,
    private val json: Json,
    private val isDebug: Boolean,
) : ViewModel() {

    private val _state = MutableStateFlow(PdvUiState())
    val state: StateFlow<PdvUiState> = _state.asStateFlow()

    private var catalogFetchGeneration = 0

    init {
        refreshCatalog()
    }

    fun selectEvent(eventId: String) {
        _state.update { it.copy(selectedEventId = eventId, message = null, error = null) }
    }

    fun clearSelectedEvent() {
        _state.update { it.copy(selectedEventId = null) }
    }

    fun selectedEvent(): EventCatalogDto? {
        val id = _state.value.selectedEventId ?: return null
        return _state.value.catalog?.events?.firstOrNull { it.id == id }
    }

    fun refreshCatalog() {
        val generation = ++catalogFetchGeneration
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val result = runCatching { catalogRepository.fetchAndCache() }
            if (generation != catalogFetchGeneration) return@launch

            result
                .onSuccess { catalog ->
                    _state.update { s ->
                        val keepSelection = s.selectedEventId?.let { id ->
                            catalog.events.any { it.id == id }
                        } ?: false
                        s.copy(
                            loading = false,
                            catalog = catalog,
                            catalogVersion = s.catalogVersion + 1,
                            selectedEventId = if (keepSelection) s.selectedEventId else null,
                            cart = trimCart(s.cart, catalog.events),
                        )
                    }
                }
                .onFailure { e ->
                    val cached = catalogRepository.getCached()
                    _state.update {
                        it.copy(
                            loading = false,
                            catalog = cached,
                            error = e.message ?: "Falha ao carregar catálogo",
                        )
                    }
                }
        }
    }

    fun quantityInCart(batchId: String): Int =
        _state.value.cart.firstOrNull { it.batchId == batchId }?.quantity ?: 0

    fun addTicket(batch: TicketBatchDto, eventName: String) {
        if (batch.available <= 0) {
            _state.update { it.copy(error = "${batch.name} esgotado") }
            return
        }
        val inCart = quantityInCart(batch.id)
        if (inCart >= batch.available) {
            _state.update {
                it.copy(error = "Disponível: ${batch.available} (${batch.name})")
            }
            return
        }
        val description = "$eventName - ${batch.name}"
        _state.update { s ->
            val existing = s.cart.indexOfFirst { it.batchId == batch.id }
            val newCart = if (existing >= 0) {
                s.cart.mapIndexed { i, line ->
                    if (i == existing) line.copy(quantity = line.quantity + 1) else line
                }
            } else {
                s.cart + CartLine(
                    itemType = ItemType.TICKET,
                    batchId = batch.id,
                    eventId = batch.eventId,
                    description = description,
                    quantity = 1,
                    unitPrice = batch.price,
                    holderName = configStore.getOperatorName(),
                )
            }
            s.copy(cart = newCart, message = null, error = null)
        }
    }

    fun removeTicket(batchId: String) {
        _state.update { s ->
            val idx = s.cart.indexOfFirst { it.batchId == batchId }
            if (idx < 0) return@update s
            val line = s.cart[idx]
            val newCart = if (line.quantity <= 1) {
                s.cart.filterNot { it.batchId == batchId }
            } else {
                s.cart.mapIndexed { i, l ->
                    if (i == idx) l.copy(quantity = l.quantity - 1) else l
                }
            }
            s.copy(
                cart = newCart,
                message = if (newCart.isEmpty()) null else "Ingresso removido",
                showCart = if (newCart.isEmpty()) false else s.showCart,
            )
        }
    }

    fun openCart() {
        if (_state.value.cart.isEmpty()) {
            _state.update { it.copy(error = "Carrinho vazio") }
            return
        }
        _state.update { it.copy(showCart = true, error = null) }
    }

    fun closeCart() {
        _state.update { it.copy(showCart = false) }
    }

    fun clearCart() {
        _state.update { it.copy(cart = emptyList(), showCart = false) }
    }

    private fun trimCart(cart: List<CartLine>, events: List<EventCatalogDto>): List<CartLine> {
        val batches = events.flatMap { e -> e.ticketBatches }
        return cart.mapNotNull { line ->
            val batchId = line.batchId ?: return@mapNotNull null
            val batch = batches.firstOrNull { it.id == batchId } ?: return@mapNotNull null
            val qty = line.quantity.coerceAtMost(batch.available)
            if (qty <= 0) null else line.copy(quantity = qty)
        }
    }

    fun checkout(method: PaymentMethodApi) {
        val cart = _state.value.cart
        if (cart.isEmpty()) {
            _state.update { it.copy(error = "Carrinho vazio") }
            return
        }
        val total = cart.sumOf { it.lineTotal }
        val clientRef = ClientReferenceGenerator.newReference(
            configStore.getDeviceShortId(),
            isDebug,
        )

        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, message = null) }
            val payment = runCatching { paymentGateway.charge(total, method) }
            if (payment.isFailure) {
                _state.update { it.copy(loading = false, error = payment.exceptionOrNull()?.message) }
                return@launch
            }
            val pay = payment.getOrThrow()

            val request = CreateSaleRequestDto(
                clientReference = clientRef,
                operatorName = configStore.getOperatorName(),
                paymentMethod = method.apiValue,
                totalAmount = total,
                stone = if (method == PaymentMethodApi.CASH) null else StonePaymentDto(
                    nsu = pay.nsu,
                    authorization = pay.authorization,
                    brand = pay.brand,
                    transactionId = pay.transactionId,
                ),
                items = cart.map { line ->
                    SaleItemDto(
                        itemType = line.itemType.apiValue,
                        productId = line.productId,
                        batchId = line.batchId,
                        eventId = line.eventId,
                        holderName = line.holderName,
                        holderEmail = line.holderEmail,
                        description = line.description,
                        quantity = line.quantity,
                        unitPrice = line.unitPrice,
                    )
                },
            )

            val pending = PendingSaleEntity(
                clientReference = clientRef,
                payloadJson = json.encodeToString(CreateSaleRequestDto.serializer(), request),
                status = PendingSaleStatus.PENDING_SYNC,
                createdAt = System.currentTimeMillis(),
            )
            saleRepository.enqueuePending(pending)

            runCatching { saleRepository.submitSale(request) }
                .onSuccess { success ->
                    printer.printReceipt(cart, total, method.apiValue, pay.nsu, pay.authorization)
                    success.ticketCodes.forEach { code ->
                        printer.printTicketQr(code, configStore.getOperatorName(), "Ingresso")
                    }
                    saleAdmin.recordCheckout(
                        saleId = success.saleId,
                        clientReference = clientRef,
                        cart = cart,
                        total = total,
                        method = method,
                        payment = pay,
                        ticketCodes = success.ticketCodes,
                    )
                    _state.update {
                        it.copy(
                            loading = false,
                            cart = emptyList(),
                            showCart = false,
                            message = if (success.duplicated) {
                                "Venda já sincronizada (${success.saleId})"
                            } else {
                                "Venda OK: ${success.saleId}"
                            },
                            lastSaleId = success.saleId,
                            lastTicketCodes = success.ticketCodes,
                        )
                    }
                    schedulePendingSync()
                }
                .onFailure { e ->
                    val msg = when (e) {
                        is ApiException -> {
                            val avail = e.available?.let { " (disp: $it)" } ?: ""
                            "${e.message}$avail"
                        }
                        else -> e.message ?: "Falha na API — venda na fila offline"
                    }
                    printer.printReceipt(cart, total, method.apiValue, pay.nsu, pay.authorization)
                    saleAdmin.recordCheckout(null, clientRef, cart, total, method, pay)
                    _state.update {
                        it.copy(
                            loading = false,
                            error = msg,
                            message = "Pagamento OK na Stone (mock). Venda salva para sync: $clientRef",
                        )
                    }
                    schedulePendingSync()
                }
        }
    }

    private fun schedulePendingSync() {
        viewModelScope.launch {
            pendingSaleSync.syncAll()
        }
    }
}
