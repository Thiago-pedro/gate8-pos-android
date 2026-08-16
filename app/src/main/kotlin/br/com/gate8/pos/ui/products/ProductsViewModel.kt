package br.com.gate8.pos.ui.products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.gate8.pos.core.network.ApiException
import br.com.gate8.pos.core.sale.PendingSaleSync
import br.com.gate8.pos.core.sale.SaleAdminService
import br.com.gate8.pos.core.sale.SaleDraftFactory
import br.com.gate8.pos.ui.common.CatalogUserMessages
import br.com.gate8.pos.ui.common.PaymentUserMessages
import br.com.gate8.pos.core.network.isStockOrProductError
import br.com.gate8.pos.core.network.saleErrorMessage
import br.com.gate8.pos.core.util.ClientReferenceGenerator
import br.com.gate8.pos.data.local.entity.PendingSaleEntity
import br.com.gate8.pos.data.local.entity.PendingSaleStatus
import br.com.gate8.pos.data.prefs.DeviceConfigStore
import br.com.gate8.pos.data.remote.dto.CatalogResponseDto
import br.com.gate8.pos.data.remote.dto.CreateSaleRequestDto
import br.com.gate8.pos.data.remote.dto.ProductDto
import br.com.gate8.pos.data.remote.dto.SaleItemDto
import br.com.gate8.pos.data.remote.dto.AcquirerPaymentDto
import br.com.gate8.pos.data.repository.CashierRepository
import br.com.gate8.pos.data.repository.CatalogRepository
import br.com.gate8.pos.data.repository.SaleRepository
import br.com.gate8.pos.domain.model.CartLine
import br.com.gate8.pos.domain.model.ItemType
import br.com.gate8.pos.domain.model.PaymentMethodApi
import br.com.gate8.pos.domain.model.canAddMore
import br.com.gate8.pos.domain.model.isOutOfStock
import br.com.gate8.pos.domain.model.tracksStock
import br.com.gate8.pos.payment.PaymentCancelledException
import br.com.gate8.pos.payment.MpOrderReconciliation
import br.com.gate8.pos.payment.chargeResilient
import br.com.gate8.pos.payment.tryReconcileAfterPaymentFailure
import br.com.gate8.pos.payment.PaymentGateway
import br.com.gate8.pos.payment.PaymentResult
import br.com.gate8.pos.payment.PixExpiredException
import br.com.gate8.pos.printer.ReceiptPrinter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/** Dados do modal de sucesso exibido ao concluir uma venda. */
data class SaleSuccessUi(
    val title: String,
    val detail: String? = null,
)

/**
 * Impressão pendente aguardando o operador responder se deseja a via do cliente.
 * Guarda o que falta imprimir (via cliente opcional + comprovante Gate8 + fichas).
 */
data class PendingClientCopy(
    val cart: List<CartLine>,
    val total: Double,
    val method: PaymentMethodApi,
    val pay: PaymentResult,
    val success: SaleSuccessUi,
)

data class ProductsUiState(
    val loading: Boolean = false,
    /** Forma de pagamento em processamento (para mostrar a mensagem certa enquanto carrega). */
    val payingMethod: PaymentMethodApi? = null,
    /** Quando preenchido, mostra o modal de "venda concluída". */
    val saleSuccess: SaleSuccessUi? = null,
    /** Quando preenchido, mostra o prompt "imprimir via do cliente?". */
    val pendingClientCopy: PendingClientCopy? = null,
    /** Quando true, mostra o modal de "QR Code Pix expirado". */
    val pixExpired: Boolean = false,
    /** Quando true, mostra o modal de "pagamento cancelado". */
    val paymentCancelled: Boolean = false,
    /** Quando true, mostra o modal de falha no pagamento (leitura ou autorização). */
    val paymentFailed: Boolean = false,
    /** Motivo real devolvido pela maquininha/adquirente (ex.: "Transação não aprovada"). */
    val paymentFailedReason: String? = null,
    val catalog: CatalogResponseDto? = null,
    /** Incrementado a cada refresh bem-sucedido para forçar recomposição da grade. */
    val catalogVersion: Int = 0,
    val cart: List<CartLine> = emptyList(),
    val showCart: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val cashierOpen: Boolean = false,
)

class ProductsViewModel(
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

    private val _state = MutableStateFlow(ProductsUiState())
    val state: StateFlow<ProductsUiState> = _state.asStateFlow()

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

    val cartItemCount: Int get() = _state.value.cart.sumOf { it.quantity }

    val cartTotal: Double get() = _state.value.cart.sumOf { it.lineTotal }

    fun quantityInCart(productId: String): Int =
        _state.value.cart.firstOrNull { it.productId == productId }?.quantity ?: 0

    fun refreshCatalog() {
        val generation = ++catalogFetchGeneration
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val result = runCatching { catalogRepository.fetchAndCache() }
            if (generation != catalogFetchGeneration) return@launch

            result
                .onSuccess { catalog ->
                    _state.update { s ->
                        val trimmedCart = trimCart(s.cart, catalog.products)
                        s.copy(
                            loading = false,
                            catalog = catalog,
                            catalogVersion = s.catalogVersion + 1,
                            cart = trimmedCart,
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
                                "Falha ao carregar produtos",
                            ),
                        )
                    }
                }
        }
    }

    fun addProduct(product: ProductDto) {
        if (product.isOutOfStock) {
            _state.update { it.copy(error = "${product.name} sem estoque") }
            return
        }
        val inCart = quantityInCart(product.id)
        if (!product.canAddMore(inCart)) {
            val available = product.stockQuantity ?: 0
            _state.update {
                it.copy(error = "Estoque máximo: $available (${product.name})")
            }
            return
        }
        _state.update { s ->
            val existing = s.cart.indexOfFirst { it.productId == product.id }
            val newCart = if (existing >= 0) {
                s.cart.mapIndexed { i, line ->
                    if (i == existing) line.copy(quantity = line.quantity + 1) else line
                }
            } else {
                s.cart + CartLine(
                    itemType = ItemType.PRODUCT,
                    productId = product.id,
                    eventId = product.eventId,
                    description = product.name,
                    quantity = 1,
                    unitPrice = product.price,
                )
            }
            s.copy(cart = newCart, error = null)
        }
    }

    fun removeProduct(productId: String) {
        _state.update { s ->
            val idx = s.cart.indexOfFirst { it.productId == productId }
            if (idx < 0) return@update s
            val line = s.cart[idx]
            val newCart = if (line.quantity <= 1) {
                s.cart.filterNot { it.productId == productId }
            } else {
                s.cart.mapIndexed { i, l ->
                    if (i == idx) l.copy(quantity = l.quantity - 1) else l
                }
            }
            s.copy(
                cart = newCart,
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

    fun dismissSaleSuccess() {
        _state.update { it.copy(saleSuccess = null) }
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

    fun checkout(method: PaymentMethodApi) {
        val cart = _state.value.cart
        if (cart.isEmpty()) return
        if (!validateCartStock()) return
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
                    saleAdmin.recordCheckout(success.saleId, clientRef, cart, total, method, pay)
                    val successUi = SaleSuccessUi(
                        title = if (success.duplicated) {
                            "Venda já registrada!"
                        } else {
                            "Venda concluída com sucesso!"
                        },
                    )
                    _state.update { it.copy(loading = false, cart = emptyList(), showCart = false) }
                    beginReceiptPrint(cart, total, method, pay, successUi)
                    refreshCatalog()
                    schedulePendingSync()
                }
                .onFailure { e ->
                    handleCheckoutFailure(e, cart, total, method, pay, clientRef)
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
        saleAdmin.recordCheckout(recovered.saleSuccess.saleId, clientRef, cart, total, method, pay)
        val successUi = SaleSuccessUi(
            title = if (recovered.saleSuccess.duplicated) {
                "Venda já registrada!"
            } else {
                "Venda recuperada com sucesso!"
            },
        )
        _state.update { it.copy(loading = false, cart = emptyList(), showCart = false) }
        beginReceiptPrint(cart, total, method, pay, successUi)
        refreshCatalog()
    }

    private fun schedulePendingSync() {
        viewModelScope.launch {
            pendingSaleSync.syncAll()
        }
    }

    /**
     * Inicia a impressão na ordem certa. Para cartão/Pix: imprime a via do lojista
     * e pergunta se deve sair a via do cliente (via [answerClientCopy]); em dinheiro,
     * já imprime o comprovante e as fichas.
     */
    private fun beginReceiptPrint(
        cart: List<CartLine>,
        total: Double,
        method: PaymentMethodApi,
        pay: PaymentResult,
        success: SaleSuccessUi,
    ) {
        val isCardLike = method != PaymentMethodApi.CASH &&
            (!pay.transactionId.isNullOrBlank() || !pay.nsu.isNullOrBlank())
        if (isCardLike) {
            printer.printCardCopy(pay.transactionId, pay.nsu, merchantCopy = true)
            _state.update {
                it.copy(pendingClientCopy = PendingClientCopy(cart, total, method, pay, success))
            }
        } else {
            printSummaryAndTickets(cart, total, method, pay)
            _state.update { it.copy(saleSuccess = success) }
        }
    }

    /** Resposta do operador ao prompt "imprimir via do cliente?". */
    fun answerClientCopy(printClientCopy: Boolean) {
        val pending = _state.value.pendingClientCopy ?: return
        if (printClientCopy) {
            printer.printCardCopy(pending.pay.transactionId, pending.pay.nsu, merchantCopy = false)
        }
        printSummaryAndTickets(pending.cart, pending.total, pending.method, pending.pay)
        _state.update { it.copy(pendingClientCopy = null, saleSuccess = pending.success) }
    }

    /** Comprovante textual da Gate8 e, em modo ficha, uma ficha por unidade de item. */
    private fun printSummaryAndTickets(
        cart: List<CartLine>,
        total: Double,
        method: PaymentMethodApi,
        pay: PaymentResult,
    ) {
        printer.printSaleSummary(cart, total, method.apiValue, pay.nsu, pay.authorization)
        if (configStore.isConvenienceTicketMode()) {
            printer.printConvenienceTickets(cart, terminalName(), pay.authorization)
        }
    }

    /**
     * Impressão completa sem prompt (usada nos caminhos de falha da API): comprovante
     * único com vias de cartão e, em modo ficha, as fichas.
     */
    private fun printSaleReceipt(
        cart: List<CartLine>,
        total: Double,
        method: PaymentMethodApi,
        pay: PaymentResult,
    ) {
        printer.printReceipt(
            cart,
            total,
            method.apiValue,
            pay.nsu,
            pay.authorization,
            acquirerTransactionId = pay.transactionId.takeIf { method != PaymentMethodApi.CASH },
        )
        if (configStore.isConvenienceTicketMode()) {
            printer.printConvenienceTickets(cart, terminalName(), pay.authorization)
        }
    }

    /** Nome do dispositivo usado como "terminal" nas fichas (ex.: "CX 9"). */
    private fun terminalName(): String =
        configStore.getDeviceName()?.takeIf { it.isNotBlank() } ?: configStore.getDeviceShortId()

    private fun products(): List<ProductDto> = _state.value.catalog?.products.orEmpty()

    private fun validateCartStock(): Boolean {
        for (line in _state.value.cart) {
            val product = products().firstOrNull { it.id == line.productId } ?: continue
            if (!product.tracksStock) continue
            val available = product.stockQuantity ?: 0
            if (line.quantity > available) {
                _state.update {
                    it.copy(error = "Estoque insuficiente para ${product.name}. Disponível: $available")
                }
                return false
            }
        }
        return true
    }

    private fun trimCart(cart: List<CartLine>, products: List<ProductDto>): List<CartLine> =
        cart.mapNotNull { line ->
            val product = products.firstOrNull { it.id == line.productId } ?: return@mapNotNull null
            if (!product.tracksStock) return@mapNotNull line
            val qty = line.quantity.coerceAtMost(product.stockQuantity ?: 0)
            if (qty <= 0) null else line.copy(quantity = qty)
        }

    private fun handleCheckoutFailure(
        e: Throwable,
        cart: List<CartLine>,
        total: Double,
        method: PaymentMethodApi,
        pay: PaymentResult,
        clientRef: String,
    ) {
        when (e) {
            is ApiException -> {
                if (e.isStockOrProductError()) {
                    printSaleReceipt(cart, total, method, pay)
                    saleAdmin.recordCheckout(null, clientRef, cart, total, method, pay)
                    _state.update {
                        it.copy(
                            loading = false,
                            error = e.saleErrorMessage(),
                            message = "Pagamento registrado localmente; estoque rejeitou na API",
                        )
                    }
                    refreshCatalog()
                } else {
                    printSaleReceipt(cart, total, method, pay)
                    saleAdmin.recordCheckout(null, clientRef, cart, total, method, pay)
                    _state.update {
                        it.copy(
                            loading = false,
                            error = e.saleErrorMessage(),
                            message = "Pagamento OK (mock). Venda salva para sync: $clientRef",
                        )
                    }
                }
            }
            else -> {
                printSaleReceipt(cart, total, method, pay)
                saleAdmin.recordCheckout(null, clientRef, cart, total, method, pay)
                _state.update {
                    it.copy(
                        loading = false,
                        error = e.message ?: "Falha na API — venda na fila offline",
                        message = "Pagamento OK (mock). Venda salva para sync: $clientRef",
                    )
                }
            }
        }
    }
}
