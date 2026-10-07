package br.com.gate8.pos.ui.pdv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.gate8.pos.BuildConfig
import br.com.gate8.pos.core.network.ApiException
import br.com.gate8.pos.core.sale.PendingSaleSync
import br.com.gate8.pos.core.sale.SaleAdminService
import br.com.gate8.pos.core.sale.SaleRequestFactory
import br.com.gate8.pos.ui.common.CatalogUserMessages
import br.com.gate8.pos.ui.common.PaymentUserMessages
import br.com.gate8.pos.core.util.ClientReferenceGenerator
import br.com.gate8.pos.data.local.entity.PendingSaleEntity
import br.com.gate8.pos.data.local.entity.PendingSaleStatus
import br.com.gate8.pos.data.prefs.DeviceConfigStore
import br.com.gate8.pos.data.prefs.SplitPaymentStore
import br.com.gate8.pos.data.remote.dto.CatalogResponseDto
import br.com.gate8.pos.data.remote.dto.EventCatalogDto
import br.com.gate8.pos.data.remote.dto.TicketBatchDto
import br.com.gate8.pos.data.remote.dto.canAdd
import br.com.gate8.pos.data.remote.dto.isSoldOut
import br.com.gate8.pos.data.remote.dto.remaining
import br.com.gate8.pos.data.remote.dto.CreateSaleRequestDto
import br.com.gate8.pos.data.repository.CashierRepository
import br.com.gate8.pos.data.repository.CatalogRepository
import br.com.gate8.pos.data.repository.SaleRepository
import br.com.gate8.pos.domain.model.CartLine
import br.com.gate8.pos.domain.model.ItemType
import br.com.gate8.pos.domain.model.PaymentMethodApi
import br.com.gate8.pos.domain.model.SaleTicketGroup
import br.com.gate8.pos.payment.PaymentCancelledException
import br.com.gate8.pos.payment.chargeResilient
import br.com.gate8.pos.payment.PaymentGateway
import br.com.gate8.pos.payment.PaymentResult
import br.com.gate8.pos.payment.PixExpiredException
import br.com.gate8.pos.payment.PrepaidCheckout
import br.com.gate8.pos.payment.SplitCancelOutcome
import br.com.gate8.pos.payment.SplitPaymentController
import br.com.gate8.pos.payment.SplitPaymentUi
import br.com.gate8.pos.payment.SplitPayments
import br.com.gate8.pos.payment.SplitSessionRecord
import br.com.gate8.pos.payment.SplitStep
import br.com.gate8.pos.payment.toCartLine
import br.com.gate8.pos.printer.ReceiptPrinter
import br.com.gate8.pos.printer.TicketPrintPayload
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
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
    val split: SplitPaymentUi? = null,
    val splitNotice: String? = null,
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
    private val configStore: DeviceConfigStore,
    private val cashierRepository: CashierRepository,
    private val json: Json,
    private val splitStore: SplitPaymentStore,
    private val isDebug: Boolean,
) : ViewModel() {

    private val _state = MutableStateFlow(PdvUiState())
    val state: StateFlow<PdvUiState> = _state.asStateFlow()

    private val splitPay = SplitPaymentController(paymentGateway, splitStore, "pdv")
    private var splitFinished = false

    private var catalogFetchGeneration = 0

    init {
        refreshCatalog()
        refreshCashierStatus()
        splitPay.restore()?.let { restored ->
            _state.update {
                it.copy(
                    cart = restored.session.cartLines.map { line -> line.toCartLine() },
                    split = restored,
                    showCart = false,
                )
            }
        }
    }

    fun onScreenVisible() {
        refreshCashierStatus()
        refreshCatalog()
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
                            error = CatalogUserMessages.fromThrowable(
                                e,
                                "Falha ao carregar catálogo",
                            ),
                        )
                    }
                }
        }
    }

    fun quantityInCart(batchId: String): Int =
        _state.value.cart.firstOrNull { it.batchId == batchId }?.quantity ?: 0

    fun addTicket(batch: TicketBatchDto, eventName: String) {
        if (batch.isSoldOut) {
            _state.update { it.copy(error = "${batch.name} esgotado") }
            return
        }
        val inCart = quantityInCart(batch.id)
        if (!batch.canAdd(inCart)) {
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
            val qty = line.quantity.coerceAtMost(batch.remaining)
            if (qty <= 0) null else line.copy(quantity = qty)
        }
    }

    fun checkout(method: PaymentMethodApi, prepaid: PrepaidCheckout? = null) {
        val cart = _state.value.cart
        if (cart.isEmpty()) {
            splitFinished = false
            _state.update { it.copy(error = "Carrinho vazio") }
            return
        }
        if (prepaid == null && method == PaymentMethodApi.CASH && !_state.value.cashierOpen) {
            splitFinished = false
            _state.update { it.copy(error = "Caixa fechado. Abra o caixa na Home.") }
            return
        }
        val total = cart.sumOf { it.lineTotal }
        val clientRef = prepaid?.clientReference ?: ClientReferenceGenerator.newReference(
            configStore.getDeviceShortId(),
            isDebug,
        )
        val operatorName = configStore.getOperatorName()

        viewModelScope.launch {
            try {
            _state.update { it.copy(loading = true, payingMethod = method, error = null, message = null) }
            val payment = if (prepaid != null) {
                Result.success(prepaid.pay)
            } else {
                runCatching { paymentGateway.chargeResilient(total, method, clientRef) }
            }
            if (payment.isFailure) {
                val err = payment.exceptionOrNull()
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

            val request = SaleRequestFactory.create(
                clientReference = clientRef,
                operatorName = operatorName,
                method = method,
                total = total,
                payment = pay,
                cart = cart,
                payments = prepaid?.salePayments,
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
                        paymentLabel = prepaid?.paymentLabel,
                        payments = prepaid?.localPayments.orEmpty(),
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
                    saleAdmin.recordCheckout(
                        null,
                        clientRef,
                        cart,
                        total,
                        method,
                        pay,
                        paymentLabel = prepaid?.paymentLabel,
                        payments = prepaid?.localPayments.orEmpty(),
                    )
                    _state.update {
                        it.copy(
                            loading = false,
                            error = msg,
                            message = "Pagamento OK na adquirente. Venda salva para sync: $clientRef",
                        )
                    }
                    schedulePendingSync()
                }
            } finally {
                splitFinished = false
            }
        }
    }

    fun dismissSplitNotice() {
        _state.update { it.copy(splitNotice = null) }
    }

    fun openSplitPayment() {
        val cart = _state.value.cart
        if (cart.isEmpty() || _state.value.loading) {
            if (cart.isEmpty()) _state.update { it.copy(error = "Carrinho vazio") }
            return
        }
        beginSplit()
    }

    fun splitPickMethod(method: PaymentMethodApi) {
        val ui = _state.value.split ?: return
        _state.update { it.copy(split = splitPay.pickMethod(ui, method)) }
    }

    fun splitAmountChange(value: String) {
        val ui = _state.value.split ?: return
        _state.update { it.copy(split = splitPay.editAmount(ui, value)) }
    }

    fun splitBack() {
        val ui = _state.value.split ?: return
        if (ui.step == SplitStep.Summary) requestCancelSplit()
        else _state.update { it.copy(split = splitPay.backToSummary(ui)) }
    }

    fun splitAddPart() {
        val ui = _state.value.split ?: return
        _state.update { it.copy(split = ui.copy(step = SplitStep.PickMethod, error = null)) }
    }

    fun confirmSplitAmount() {
        val ui = _state.value.split ?: return
        if (_state.value.loading || splitFinished) return
        _state.update { it.copy(loading = true, payingMethod = ui.draftMethod, split = ui.copy(error = null)) }
        viewModelScope.launch {
            val charged = runCatching { splitPay.charge(ui) }
            charged.onSuccess { updated ->
                _state.update { it.copy(loading = false, payingMethod = null, split = updated) }
                if (updated.error == null && updated.session.remainingCents() == 0L) {
                    finishSplit(updated.session)
                }
            }.onFailure { err ->
                _state.update {
                    it.copy(
                        loading = false,
                        payingMethod = null,
                        split = ui.copy(
                            error = when (err) {
                                is PaymentCancelledException ->
                                    "Cobrança cancelada. Essa parte não foi adicionada."
                                is PixExpiredException ->
                                    "O Pix expirou. Essa parte não foi adicionada."
                                else -> PaymentUserMessages.failureReason(err)
                            },
                        ),
                    )
                }
            }
        }
    }

    fun requestCancelSplit() {
        val ui = _state.value.split ?: return
        if (ui.session.parts.isEmpty()) {
            splitPay.clear()
            _state.update { it.copy(split = null) }
            return
        }
        _state.update { it.copy(split = ui.copy(confirmingCancel = true)) }
    }

    fun dismissCancelSplit() {
        val ui = _state.value.split ?: return
        _state.update { it.copy(split = ui.copy(confirmingCancel = false)) }
    }

    fun confirmCancelSplit() {
        val ui = _state.value.split ?: return
        if (_state.value.loading) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, payingMethod = null) }
            when (val outcome = splitPay.cancel(ui)) {
                is SplitCancelOutcome.Cleared -> _state.update {
                    it.copy(loading = false, split = null, splitNotice = outcome.message.ifBlank { null })
                }
                is SplitCancelOutcome.StillOpen -> _state.update {
                    it.copy(loading = false, split = outcome.ui)
                }
            }
        }
    }

    fun dismissSplitReport() {
        val ui = _state.value.split ?: return
        _state.update { it.copy(split = ui.copy(cancelReport = null)) }
    }

    private fun beginSplit() {
        val ui = splitPay.begin(
            cart = _state.value.cart,
            kitchenNote = null,
            newClientReference = ClientReferenceGenerator.newReference(
                configStore.getDeviceShortId(),
                isDebug,
            ),
        )
        _state.update {
            it.copy(
                split = ui,
                showCart = false,
                cart = ui.session.cartLines.map { line -> line.toCartLine() }.ifEmpty { it.cart },
            )
        }
    }

    private fun finishSplit(session: SplitSessionRecord) {
        if (splitFinished || session.remainingCents() != 0L || session.parts.isEmpty()) return
        splitFinished = true
        val prepaid = SplitPayments.toPrepaid(session)
        val cart = session.cartLines.map { it.toCartLine() }
        splitPay.clear()
        _state.update { it.copy(split = null, cart = cart.ifEmpty { it.cart }, showCart = false) }
        checkout(prepaid.method, prepaid)
    }

    private fun schedulePendingSync() {
        viewModelScope.launch {
            pendingSaleSync.syncAll()
        }
    }

    /**
     * Vias do cartão ficam com a Cielo — Gate8 imprime os ingressos direto.
     * Em dinheiro: ingresso direto.
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
        val askClientCopy = isCardLike && !BuildConfig.FLAVOR.equals("cielo", ignoreCase = true)
        if (askClientCopy) {
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
                    payingMethod = null,
                )
            }
        } else {
            _state.update {
                it.copy(
                    loading = false,
                    cart = emptyList(),
                    showCart = false,
                    saleSuccessMessage = successMessage,
                    lastSaleId = success.saleId,
                    lastTicketCodes = success.ticketCodes,
                    payingMethod = null,
                )
            }
            printTickets(success.ticketGroups, cart, success.purchaseCode)
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

    /** Imprime um ingresso por ticket emitido (campos do Lovable + fallback do catálogo). */
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
            group.tickets.forEach { ticket ->
                printer.printTicket(
                    TicketPrintPayload(
                        eventName = ticket.eventName?.takeIf { it.isNotBlank() }
                            ?: event?.name
                            ?: line?.description
                            ?: "Ingresso",
                        batchName = ticket.batchName?.takeIf { it.isNotBlank() }
                            ?: batch?.name.orEmpty(),
                        eventDateLabel = formatEventDate(ticket.eventDate ?: event?.eventDate),
                        venue = ticket.venue?.takeIf { it.isNotBlank() } ?: event?.location,
                        terminalName = configStore.getDeviceName(),
                        holderName = ticket.holderName?.takeIf { it.isNotBlank() }
                            ?: line?.holderName
                            ?: configStore.getOperatorName(),
                        price = ticket.price ?: batch?.price ?: line?.unitPrice ?: 0.0,
                        qrPayload = ticket.qrPayload,
                        manualCode = ticket.manualCode,
                        purchaseCode = ticket.purchaseCode?.takeIf { it.isNotBlank() }
                            ?: purchaseCode,
                        statusLabel = ticket.statusLabel?.takeIf { it.isNotBlank() } ?: "Válido",
                        saleDateLabel = formatSaleDate(ticket.issuedAt),
                        issuedAtLabel = formatIssuedAt(ticket.issuedAt),
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

    private fun formatIssuedAt(raw: String?): String? {
        val formatted = formatSaleDate(raw) ?: return null
        return "Emitido: $formatted"
    }

    private fun formatSaleDate(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            OffsetDateTime.parse(raw).atZoneSameInstant(eventZone).format(issuedAtFmt)
        }.recoverCatching {
            LocalDateTime.parse(raw).format(issuedAtFmt)
        }.getOrNull()
    }

    private companion object {
        private val brLocale = Locale("pt", "BR")
        /** Lazy: evita crash no class-load em API &lt; 26 se desugar falhar. */
        private val eventZone: ZoneId by lazy {
            runCatching { ZoneId.of("America/Sao_Paulo") }
                .getOrElse { ZoneOffset.of("-03:00") }
        }
        private val eventDateFmt by lazy {
            DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm", brLocale)
        }
        private val eventDateOnlyFmt by lazy {
            DateTimeFormatter.ofPattern("dd/MM/yyyy", brLocale)
        }
        private val issuedAtFmt by lazy {
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm", brLocale)
        }
    }
}
