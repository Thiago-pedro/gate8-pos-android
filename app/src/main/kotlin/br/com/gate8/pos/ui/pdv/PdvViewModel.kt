package br.com.gate8.pos.ui.pdv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.gate8.pos.core.network.ApiException
import br.com.gate8.pos.core.util.ClientReferenceGenerator
import br.com.gate8.pos.data.local.entity.PendingSaleEntity
import br.com.gate8.pos.data.local.entity.PendingSaleStatus
import br.com.gate8.pos.data.prefs.DeviceConfigStore
import br.com.gate8.pos.data.remote.dto.CatalogResponseDto
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
    val cart: List<CartLine> = emptyList(),
    val message: String? = null,
    val error: String? = null,
    val lastSaleId: String? = null,
    val lastTicketCodes: List<String> = emptyList(),
)

class PdvViewModel(
    private val catalogRepository: CatalogRepository,
    private val saleRepository: SaleRepository,
    private val paymentGateway: PaymentGateway,
    private val printer: ReceiptPrinter,
    private val configStore: DeviceConfigStore,
    private val json: Json,
    private val isDebug: Boolean,
) : ViewModel() {

    private val _state = MutableStateFlow(PdvUiState())
    val state: StateFlow<PdvUiState> = _state.asStateFlow()

    init {
        refreshCatalog()
    }

    fun refreshCatalog() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { catalogRepository.fetchAndCache() }
                .onSuccess { catalog -> _state.update { it.copy(loading = false, catalog = catalog) } }
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

    fun addProduct(productId: String, name: String, price: Double) {
        addLine(
            CartLine(
                itemType = ItemType.PRODUCT,
                productId = productId,
                description = name,
                quantity = 1,
                unitPrice = price,
            ),
        )
    }

    fun addTicket(batchId: String, eventId: String, name: String, price: Double) {
        addLine(
            CartLine(
                itemType = ItemType.TICKET,
                batchId = batchId,
                eventId = eventId,
                description = name,
                quantity = 1,
                unitPrice = price,
                holderName = configStore.getOperatorName(),
            ),
        )
    }

    private fun addLine(line: CartLine) {
        _state.update { s -> s.copy(cart = s.cart + line, message = "Adicionado: ${line.description}") }
    }

    fun clearCart() {
        _state.update { it.copy(cart = emptyList()) }
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
                    _state.update {
                        it.copy(
                            loading = false,
                            cart = emptyList(),
                            message = if (success.duplicated) {
                                "Venda já sincronizada (${success.saleId})"
                            } else {
                                "Venda OK: ${success.saleId}"
                            },
                            lastSaleId = success.saleId,
                            lastTicketCodes = success.ticketCodes,
                        )
                    }
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
                    _state.update {
                        it.copy(
                            loading = false,
                            error = msg,
                            message = "Pagamento OK na Stone (mock). Venda salva para sync: $clientRef",
                        )
                    }
                }
        }
    }
}
