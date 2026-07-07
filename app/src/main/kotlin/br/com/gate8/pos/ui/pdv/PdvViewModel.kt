package br.com.gate8.pos.ui.pdv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.gate8.pos.core.network.ApiException
import br.com.gate8.pos.core.sale.PendingSaleSync
import br.com.gate8.pos.core.sale.SaleAdminService
import br.com.gate8.pos.core.sale.SaleDraftFactory
import br.com.gate8.pos.ui.common.PaymentUserMessages
import br.com.gate8.pos.core.util.ClientReferenceGenerator
import br.com.gate8.pos.data.local.entity.PendingSaleEntity
import br.com.gate8.pos.data.local.entity.PendingSaleStatus
import br.com.gate8.pos.data.prefs.DeviceConfigStore
import br.com.gate8.pos.data.remote.dto.CatalogResponseDto
import br.com.gate8.pos.data.remote.dto.EventCatalogDto
import br.com.gate8.pos.data.remote.dto.TicketBatchDto
import br.com.gate8.pos.data.remote.dto.CreateSaleRequestDto
import br.com.gate8.pos.data.remote.dto.SaleItemDto
import br.com.gate8.pos.data.remote.dto.AcquirerPaymentDto
import br.com.gate8.pos.data.repository.CashierRepository
import br.com.gate8.pos.data.repository.CatalogRepository
import br.com.gate8.pos.data.repository.SaleRepository
import br.com.gate8.pos.domain.model.CartLine
import br.com.gate8.pos.domain.model.ItemType
import br.com.gate8.pos.domain.model.PaymentMethodApi
import br.com.gate8.pos.domain.model.SaleTicketGroup
import br.com.gate8.pos.payment.PaymentCancelledException
import br.com.gate8.pos.payment.MpOrderReconciliation
import br.com.gate8.pos.payment.chargeResilient
import br.com.gate8.pos.payment.tryReconcileAfterPaymentFailure
import br.com.gate8.pos.payment.PaymentGateway
import br.com.gate8.pos.payment.PaymentResult
import br.com.gate8.pos.payment.PixExpiredException
import br.com.gate8.pos.printer.ReceiptPrinter
import br.com.gate8.pos.printer.TicketPrintPayload
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

data class PdvUiState(
    val loading: Boolean = false,
    /** Forma de pagamento em processamento (para mostrar a mensagem certa enquanto carrega). */
    val payingMethod: PaymentMethodApi? = null,
    /** Quando true, mostra o modal de "QR Code Pix expirado". */
    val pixExpired: Boolean = false,
    /** Quando true, mostra o modal de "pagamento cancelado". */
    val paymentCancelled: Boolean = false,
    /** Quando true, mostra o modal de "pagamento não concluído" (falha na maquininha). */
    val paymentFailed: Boolean = false,
    /** Motivo real da falha vindo da adquirente. */
    val paymentFailedReason: String? = null,
    val catalog: CatalogResponseDto? = null,
    val catalogVersion: Int = 0,
    val selectedEventId: String? = null,
    val cart: List<CartLine> = emptyList(),
    val message: String? = null,
    /** Quando preenchido, mostra o modal de "venda concluída" (igual conveniência). */
    val saleSuccessMessage: String? = null,
    val error: String? = null,
    val lastSaleId: String? = null,
    val lastTicketCodes: List<String> = emptyList(),
    val showCart: Boolean = false,
    val cashierOpen: Boolean = false,
    /** Quando preenchido, mostra o prompt "imprimir via do cliente?" antes dos ingressos. */
    val pendingClientCopy: PendingClientCopy? = null,
)

/**
 * Impressão de ingresso aguardando o operador responder se quer a via do cliente.
 * Guarda o que falta imprimir (via cliente opcional + ingressos).
 */
data class PendingClientCopy(
    val cart: List<CartLine>,
    val ticketGroups: List<SaleTicketGroup>,
    val purchaseCode: String?,
    val pay: PaymentResult,
    val successMessage: String,
)

class PdvViewModel(
    private val catalogRepository: CatalogRepository,
    private val saleRepository: SaleRepository,
    private val paymentGateway: PaymentGateway,
    private val printer: ReceiptPrinter,
    private val saleAdmin: SaleAdminService,
    private val pendingSaleSync: PendingSaleSync,
    private val mpOrderReconciliation: MpOrderReconciliation,
    private val configStore: DeviceConfigStore,
    private val cashierRepository: CashierRepository,
    private val json: Json,
    private val isDebug: Boolean,
) : ViewModel() {

    private val _state = MutableStateFlow(PdvUiState())
    val state: StateFlow<PdvUiState> = _state.asStateFlow()

    private var catalogFetchGeneration = 0

    init {
        refreshCatalog()
        refreshCashierStatus()
    }

    fun onScreenVisible() {
        refreshCashierStatus()
    }

    private fun refreshCashierStatus() {
        viewModelScope.launch {
            runCatching { cashierRepository.fetchStatus() }
                .onSuccess { status ->
                    _state.update { it.copy(cashierOpen = status.open) }
                }
        }
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
                message = null,
                showCart = if (newCart.isEmpty()) false else s.showCart,
            )
        }
    }

    fun openCart() {
        if (_state.value.cart.isEmpty()) {
            _state.update { it.copy(error = "Carrinho vazio") }
            return
        }
        refreshCashierStatus()
        _state.update { it.copy(showCart = true, error = null) }
    }

    fun closeCart() {
        _state.update { it.copy(showCart = false) }
    }

    fun clearCart() {
        _state.update { it.copy(cart = emptyList(), showCart = false) }
    }

    /** Cancela o pagamento em andamento (cartão/Pix) na maquininha. */
    fun cancelPayment() {
        paymentGateway.cancelCurrentPayment()
    }

    fun dismissPixExpired() {
        _state.update { it.copy(pixExpired = false) }
    }

    fun dismissPaymentCancelled() {
        _state.update { it.copy(paymentCancelled = false) }
    }

    fun dismissPaymentFailed() {
        _state.update { it.copy(paymentFailed = false, paymentFailedReason = null) }
    }

    fun dismissSaleSuccess() {
        _state.update { it.copy(saleSuccessMessage = null) }
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
        if (method == PaymentMethodApi.CASH && !_state.value.cashierOpen) {
            _state.update { it.copy(error = "Caixa fechado. Abra o caixa na Home.") }
            return
        }
        val total = cart.sumOf { it.lineTotal }
        val clientRef = ClientReferenceGenerator.newReference(
            configStore.getDeviceShortId(),
            isDebug,
        )
        val operatorName = configStore.getOperatorName()
        val saleDraft = if (method != PaymentMethodApi.CASH) {
            SaleDraftFactory.mpSaleDraft(cart, total, method, operatorName)
        } else {
            null
        }

        viewModelScope.launch {
            _state.update { it.copy(loading = true, payingMethod = method, error = null, message = null) }
            val payment = runCatching {
                paymentGateway.chargeResilient(total, method, clientRef, saleDraft)
            }
            if (payment.isFailure) {
                val err = payment.exceptionOrNull()
                val recovered = tryReconcileAfterPaymentFailure(mpOrderReconciliation, err, method)
                if (recovered != null) {
                    completeRecoveredCheckout(cart, total, method, clientRef, recovered)
                    return@launch
                }
                _state.update {
                    when (err) {
                        is PaymentCancelledException ->
                            it.copy(loading = false, payingMethod = null, paymentCancelled = true)
                        is PixExpiredException ->
                            it.copy(loading = false, payingMethod = null, pixExpired = true)
                        else ->
                            it.copy(
                                loading = false,
                                payingMethod = null,
                                paymentFailed = true,
                                paymentFailedReason = PaymentUserMessages.failureReason(err),
                            )
                    }
                }
                return@launch
            }
            val pay = payment.getOrThrow()

            val request = CreateSaleRequestDto(
                clientReference = clientRef,
                operatorName = operatorName,
                paymentMethod = method.apiValue,
                totalAmount = total,
                acquirer = if (method == PaymentMethodApi.CASH) null else AcquirerPaymentDto(
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
                    saleAdmin.recordCheckout(
                        saleId = success.saleId,
                        clientReference = clientRef,
                        cart = cart,
                        total = total,
                        method = method,
                        payment = pay,
                        ticketCodes = success.ticketCodes,
                    )
                    val successMsg = if (success.duplicated) {
                        "Venda já registrada!"
                    } else {
                        "Venda concluída com sucesso!"
                    }
                    beginTicketPrint(cart, method, pay, success, successMsg)
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
                    printAcquirerVias(method, pay)
                    saleAdmin.recordCheckout(null, clientRef, cart, total, method, pay)
                    _state.update {
                        it.copy(
                            loading = false,
                            error = msg,
                            message = "Pagamento OK na adquirente. Venda salva para sync: $clientRef",
                        )
                    }
                    schedulePendingSync()
                }
        }
    }

    private fun completeRecoveredCheckout(
        cart: List<CartLine>,
        total: Double,
        method: PaymentMethodApi,
        clientRef: String,
        recovered: MpOrderReconciliation.RecoveredCheckout,
    ) {
        val pay = recovered.payment
        val success = recovered.saleSuccess
        saleAdmin.recordCheckout(
            saleId = success.saleId,
            clientReference = clientRef,
            cart = cart,
            total = total,
            method = method,
            payment = pay,
            ticketCodes = success.ticketCodes,
        )
        val successMsg = if (success.duplicated) {
            "Venda já registrada!"
        } else {
            "Venda recuperada com sucesso!"
        }
        beginTicketPrint(cart, method, pay, success, successMsg)
    }

    private fun schedulePendingSync() {
        viewModelScope.launch {
            pendingSaleSync.syncAll()
        }
    }

    /**
     * Fluxo igual à conveniência: em cartão/Pix imprime a via do lojista e pergunta
     * se quer a via do cliente; os ingressos só saem depois (em [answerClientCopy]).
     * Em dinheiro não há via — imprime o ingresso direto.
     */
    private fun beginTicketPrint(
        cart: List<CartLine>,
        method: PaymentMethodApi,
        pay: PaymentResult,
        success: br.com.gate8.pos.domain.model.SaleSuccess,
        successMessage: String,
    ) {
        val isCardLike = method != PaymentMethodApi.CASH &&
            (!pay.transactionId.isNullOrBlank() || !pay.nsu.isNullOrBlank())
        if (isCardLike) {
            printer.printCardCopy(pay.transactionId, pay.nsu, merchantCopy = true)
            _state.update {
                it.copy(
                    loading = false,
                    cart = emptyList(),
                    showCart = false,
                    lastSaleId = success.saleId,
                    lastTicketCodes = success.ticketCodes,
                    pendingClientCopy = PendingClientCopy(
                        cart = cart,
                        ticketGroups = success.ticketGroups,
                        purchaseCode = success.purchaseCode,
                        pay = pay,
                        successMessage = successMessage,
                    ),
                )
            }
        } else {
            printTickets(success.ticketGroups, cart, success.purchaseCode)
            _state.update {
                it.copy(
                    loading = false,
                    cart = emptyList(),
                    showCart = false,
                    saleSuccessMessage = successMessage,
                    lastSaleId = success.saleId,
                    lastTicketCodes = success.ticketCodes,
                )
            }
        }
    }

    /** Resposta do operador ao prompt "imprimir via do cliente?" — depois saem os ingressos. */
    fun answerClientCopy(printClientCopy: Boolean) {
        val pending = _state.value.pendingClientCopy ?: return
        if (printClientCopy) {
            printer.printCardCopy(pending.pay.transactionId, pending.pay.nsu, merchantCopy = false)
        }
        printTickets(pending.ticketGroups, pending.cart, pending.purchaseCode)
        _state.update {
            it.copy(pendingClientCopy = null, saleSuccessMessage = pending.successMessage)
        }
    }

    /**
     * Caminho de falha da API (offline): imprime as vias da adquirente sem prompt.
     * Em dinheiro não imprime via nenhuma.
     */
    private fun printAcquirerVias(method: PaymentMethodApi, pay: PaymentResult) {
        if (method == PaymentMethodApi.CASH) return
        printer.printCardCopy(pay.transactionId, pay.nsu, merchantCopy = true)
        printer.printCardCopy(pay.transactionId, pay.nsu, merchantCopy = false)
    }

    /** Imprime um ingresso por código emitido, com os dados do evento/lote do catálogo. */
    private fun printTickets(
        groups: List<SaleTicketGroup>,
        cart: List<CartLine>,
        purchaseCode: String?,
    ) {
        val events = _state.value.catalog?.events.orEmpty()
        groups.forEach { group ->
            val line = cart.getOrNull(group.itemIndex)
            val event = events.firstOrNull { it.id == line?.eventId }
            val batch = event?.ticketBatches?.firstOrNull { it.id == line?.batchId }
            group.codes.forEach { code ->
                printer.printTicket(
                    TicketPrintPayload(
                        eventName = event?.name ?: line?.description ?: "Ingresso",
                        batchName = batch?.name ?: "",
                        eventDateLabel = formatEventDate(event?.eventDate),
                        venue = event?.location,
                        terminalName = configStore.getDeviceName(),
                        holderName = line?.holderName ?: configStore.getOperatorName(),
                        price = batch?.price ?: line?.unitPrice ?: 0.0,
                        validationCode = code,
                        purchaseCode = purchaseCode,
                    ),
                )
            }
        }
    }

    /** Formata a data do evento ("dd/MM/yyyy às HH:mm") tolerando vários formatos da API. */
    private fun formatEventDate(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return runCatching { OffsetDateTime.parse(raw).atZoneSameInstant(eventZone).format(eventDateFmt) }
            .recoverCatching { LocalDateTime.parse(raw).format(eventDateFmt) }
            .recoverCatching { LocalDate.parse(raw).format(eventDateOnlyFmt) }
            .getOrDefault(raw)
    }

    private companion object {
        private val brLocale = Locale("pt", "BR")
        private val eventZone = ZoneId.of("America/Sao_Paulo")
        private val eventDateFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm", brLocale)
        private val eventDateOnlyFmt = DateTimeFormatter.ofPattern("dd/MM/yyyy", brLocale)
    }
}
