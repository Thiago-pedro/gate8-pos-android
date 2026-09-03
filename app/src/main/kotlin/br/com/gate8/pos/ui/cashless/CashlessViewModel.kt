package br.com.gate8.pos.ui.cashless

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.gate8.pos.cashless.CashlessCardGateway
import br.com.gate8.pos.cashless.CashlessCardSnapshot
import br.com.gate8.pos.cashless.CashlessOperationException
import br.com.gate8.pos.cashless.CashlessUnavailableException
import br.com.gate8.pos.core.sale.PendingSaleSync
import br.com.gate8.pos.core.sale.SaleAdminService
import br.com.gate8.pos.core.sale.SaleDraftFactory
import br.com.gate8.pos.core.sale.SaleRequestFactory
import br.com.gate8.pos.core.util.ClientReferenceGenerator
import br.com.gate8.pos.data.local.entity.PendingSaleEntity
import br.com.gate8.pos.data.local.entity.PendingSaleStatus
import br.com.gate8.pos.data.prefs.DeviceConfigStore
import br.com.gate8.pos.data.remote.dto.CreateSaleRequestDto
import br.com.gate8.pos.data.repository.CashierRepository
import br.com.gate8.pos.data.repository.SaleRepository
import br.com.gate8.pos.domain.model.CartLine
import br.com.gate8.pos.domain.model.ItemType
import br.com.gate8.pos.domain.model.PaymentMethodApi
import br.com.gate8.pos.payment.MpOrderReconciliation
import br.com.gate8.pos.payment.PaymentCancelledException
import br.com.gate8.pos.payment.PaymentGateway
import br.com.gate8.pos.payment.PaymentResult
import br.com.gate8.pos.payment.PixExpiredException
import br.com.gate8.pos.payment.chargeResilient
import br.com.gate8.pos.payment.tryReconcileAfterPaymentFailure
import br.com.gate8.pos.printer.ReceiptPrinter
import br.com.gate8.pos.ui.common.PaymentUserMessages
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

data class CashlessUiState(
    val loading: Boolean = false,
    val waitingCard: Boolean = false,
    val amountInput: String = "",
    val card: CashlessCardSnapshot? = null,
    val message: String? = null,
    val error: String? = null,
    val showPaymentSheet: Boolean = false,
    val cashierOpen: Boolean = false,
    val payingMethod: PaymentMethodApi? = null,
    val pendingAmount: Double = 0.0,
    val paymentCancelled: Boolean = false,
    val pixExpired: Boolean = false,
    val paymentFailed: Boolean = false,
    val paymentFailedReason: String? = null,
)

class CashlessViewModel(
    private val cashless: CashlessCardGateway,
    private val paymentGateway: PaymentGateway,
    private val saleRepository: SaleRepository,
    private val saleAdmin: SaleAdminService,
    private val pendingSaleSync: PendingSaleSync,
    private val mpOrderReconciliation: MpOrderReconciliation,
    private val configStore: DeviceConfigStore,
    private val cashierRepository: CashierRepository,
    private val printer: ReceiptPrinter,
    private val json: Json,
    private val isDebug: Boolean,
) : ViewModel() {
    private val _state = MutableStateFlow(CashlessUiState())
    val state: StateFlow<CashlessUiState> = _state.asStateFlow()

    init {
        refreshCashierStatus()
    }

    fun onScreenVisible() {
        refreshCashierStatus()
    }

    fun onAmountChange(value: String) {
        val filtered = value.filter { it.isDigit() || it == ',' || it == '.' }.take(12)
        _state.update { it.copy(amountInput = filtered, error = null, message = null) }
    }

    fun consultBalance() {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    loading = true,
                    waitingCard = true,
                    error = null,
                    message = "Aproxime o cartão Mifare na maquininha…",
                )
            }
            runCatching { cashless.readCard() }
                .onSuccess { snap ->
                    _state.update {
                        it.copy(
                            loading = false,
                            waitingCard = false,
                            card = snap,
                            message = snap.message ?: "Leitura concluída",
                            error = null,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            loading = false,
                            waitingCard = false,
                            error = friendlyCardError(e),
                            message = null,
                        )
                    }
                }
        }
    }

    /** Valida valor e abre a escolha Débito / Crédito / Pix / Dinheiro. */
    fun openTopUpPayment() {
        val amount = parseAmount(_state.value.amountInput)
        if (amount == null || amount <= 0.0) {
            _state.update { it.copy(error = "Informe o valor a creditar (ex.: 10,00)") }
            return
        }
        refreshCashierStatus()
        _state.update {
            it.copy(
                showPaymentSheet = true,
                pendingAmount = amount,
                error = null,
                message = null,
            )
        }
    }

    fun dismissPaymentSheet() {
        _state.update { it.copy(showPaymentSheet = false) }
    }

    fun cancelPayment() {
        paymentGateway.cancelCurrentPayment()
    }

    fun dismissPaymentCancelled() {
        _state.update { it.copy(paymentCancelled = false) }
    }

    fun dismissPixExpired() {
        _state.update { it.copy(pixExpired = false) }
    }

    fun dismissPaymentFailed() {
        _state.update { it.copy(paymentFailed = false, paymentFailedReason = null) }
    }

    fun checkout(method: PaymentMethodApi) {
        val amount = _state.value.pendingAmount.takeIf { it > 0 }
            ?: parseAmount(_state.value.amountInput)
        if (amount == null || amount <= 0.0) {
            _state.update {
                it.copy(
                    showPaymentSheet = false,
                    error = "Informe o valor a creditar (ex.: 10,00)",
                )
            }
            return
        }
        if (method == PaymentMethodApi.CASH && !_state.value.cashierOpen) {
            _state.update {
                it.copy(
                    showPaymentSheet = false,
                    error = "Caixa fechado. Abra o caixa na Home.",
                )
            }
            return
        }

        val cart = listOf(
            CartLine(
                itemType = ItemType.CUSTOM,
                description = "Recarga cashless",
                quantity = 1,
                unitPrice = amount,
            ),
        )
        val clientRef = ClientReferenceGenerator.newReference(
            configStore.getDeviceShortId(),
            isDebug,
        )
        val operatorName = configStore.getOperatorName()
        val saleDraft = if (method != PaymentMethodApi.CASH) {
            SaleDraftFactory.mpSaleDraft(cart, amount, method, operatorName)
        } else {
            null
        }

        viewModelScope.launch {
            _state.update {
                it.copy(
                    showPaymentSheet = false,
                    loading = true,
                    waitingCard = false,
                    payingMethod = method,
                    pendingAmount = amount,
                    error = null,
                    message = null,
                )
            }

            val payment = runCatching {
                paymentGateway.chargeResilient(amount, method, clientRef, saleDraft)
            }
            if (payment.isFailure) {
                val err = payment.exceptionOrNull()
                val recovered = tryReconcileAfterPaymentFailure(mpOrderReconciliation, err, method)
                if (recovered != null) {
                    finishAfterPayment(
                        amount = amount,
                        method = method,
                        clientRef = clientRef,
                        operatorName = operatorName,
                        pay = recovered.payment,
                        baseCart = cart,
                    )
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

            finishAfterPayment(
                amount = amount,
                method = method,
                clientRef = clientRef,
                operatorName = operatorName,
                pay = payment.getOrThrow(),
                baseCart = cart,
            )
        }
    }

    private suspend fun finishAfterPayment(
        amount: Double,
        method: PaymentMethodApi,
        clientRef: String,
        operatorName: String,
        pay: PaymentResult,
        baseCart: List<CartLine>,
    ) {
        _state.update {
            it.copy(
                waitingCard = true,
                message = "Pagamento OK. Aproxime o cartão para creditar R$ ${"%.2f".format(amount)}…",
            )
        }

        val cardResult = runCatching { cashless.topUp(amount) }
        val snap = cardResult.getOrNull()
        val cardError = cardResult.exceptionOrNull()?.let { friendlyCardError(it) }

        val cart = if (snap != null) {
            listOf(
                CartLine(
                    itemType = ItemType.CUSTOM,
                    description = "Recarga cashless · UID ${snap.uidHex}",
                    quantity = 1,
                    unitPrice = amount,
                ),
            )
        } else {
            baseCart
        }

        val request = SaleRequestFactory.create(
            clientReference = clientRef,
            operatorName = operatorName,
            method = method,
            total = amount,
            payment = pay,
            cart = cart,
        )
        val pending = PendingSaleEntity(
            clientReference = clientRef,
            payloadJson = json.encodeToString(CreateSaleRequestDto.serializer(), request),
            status = PendingSaleStatus.PENDING_SYNC,
            createdAt = System.currentTimeMillis(),
        )
        saleRepository.enqueuePending(pending)

        val saleId = runCatching { saleRepository.submitSale(request).saleId }.getOrNull()
        saleAdmin.recordCheckout(saleId, clientRef, cart, amount, method, pay)
        printer.printSaleSummary(cart, amount, method.apiValue, pay.nsu, pay.authorization)
        viewModelScope.launch { pendingSaleSync.syncAll() }

        if (snap != null) {
            _state.update {
                it.copy(
                    loading = false,
                    waitingCard = false,
                    payingMethod = null,
                    card = snap,
                    amountInput = "",
                    pendingAmount = 0.0,
                    message = snap.message
                        ?: "Recarga de R$ ${"%.2f".format(amount)} concluída",
                    error = null,
                )
            }
        } else {
            _state.update {
                it.copy(
                    loading = false,
                    waitingCard = false,
                    payingMethod = null,
                    amountInput = "",
                    pendingAmount = 0.0,
                    message = "Pagamento de R$ ${"%.2f".format(amount)} registrado.",
                    error = "Pagamento OK, mas a gravação no cartão falhou: $cardError",
                )
            }
        }
    }

    private fun refreshCashierStatus() {
        viewModelScope.launch {
            runCatching { cashierRepository.fetchStatus() }
                .onSuccess { status ->
                    _state.update { it.copy(cashierOpen = status.open) }
                }
        }
    }

    private fun parseAmount(raw: String): Double? {
        val normalized = raw.trim().replace(',', '.')
        if (normalized.isBlank()) return null
        return normalized.toDoubleOrNull()
    }

    private fun friendlyCardError(e: Throwable): String = when (e) {
        is CashlessUnavailableException -> e.message ?: "Cashless indisponível neste aparelho"
        is CashlessOperationException -> e.message ?: "Falha na operação Mifare"
        is TimeoutCancellationException ->
            "Tempo esgotado. Aproxime o cartão e tente de novo."
        else -> e.message ?: "Não foi possível falar com o cartão"
    }
}
