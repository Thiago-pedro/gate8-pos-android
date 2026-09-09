package br.com.gate8.pos.ui.cashless

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.gate8.pos.cashless.CashlessCardGateway
import br.com.gate8.pos.cashless.CashlessCardSnapshot
import br.com.gate8.pos.cashless.CashlessOperationException
import br.com.gate8.pos.cashless.CashlessUnavailableException
import br.com.gate8.pos.core.network.ApiException
import br.com.gate8.pos.core.sale.PendingSaleSync
import br.com.gate8.pos.core.sale.SaleAdminService
import br.com.gate8.pos.core.sale.SaleDraftFactory
import br.com.gate8.pos.core.sale.SaleRequestFactory
import br.com.gate8.pos.core.util.BrazilianDocumentValidator
import br.com.gate8.pos.core.util.ClientReferenceGenerator
import br.com.gate8.pos.data.local.entity.CashlessMovementType
import br.com.gate8.pos.data.local.entity.PendingSaleEntity
import br.com.gate8.pos.data.local.entity.PendingSaleStatus
import br.com.gate8.pos.data.prefs.DeviceConfigStore
import br.com.gate8.pos.data.remote.dto.CreateSaleRequestDto
import br.com.gate8.pos.data.repository.CashierRepository
import br.com.gate8.pos.data.repository.CashlessAccountRepository
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
import br.com.gate8.pos.printer.CashlessStatementLine
import br.com.gate8.pos.printer.CashlessStatementPayload
import br.com.gate8.pos.printer.ReceiptPrinter
import br.com.gate8.pos.ui.common.PaymentUserMessages
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
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
    val accountCpf: String? = null,
    val accountPhone: String? = null,
    val accountName: String? = null,
    /** Cadastro/sistema considera o UID inválido para uso (bloqueado ou transferido). */
    val accountBlocked: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val showPaymentSheet: Boolean = false,
    val showRegisterSheet: Boolean = false,
    val showLostCpfSheet: Boolean = false,
    val showConfirmZero: Boolean = false,
    val registerNameInput: String = "",
    val registerCpfInput: String = "",
    val registerPhoneInput: String = "",
    val lostCpfInput: String = "",
    val cashierOpen: Boolean = false,
    val payingMethod: PaymentMethodApi? = null,
    val pendingAmount: Double = 0.0,
    val pendingUid: String? = null,
    /**
     * Pagamento OK, mas o chip ainda não recebeu o crédito.
     * Operador deve aproximar o cartão e tocar em "Creditar cartão".
     */
    val pendingChipCredit: PendingChipCredit? = null,
    val paymentCancelled: Boolean = false,
    val pixExpired: Boolean = false,
    val paymentFailed: Boolean = false,
    val paymentFailedReason: String? = null,
    val recoverStep: CashlessRecoverStep = CashlessRecoverStep.Idle,
    val recoverOldUid: String? = null,
    val recoverBalance: Double = 0.0,
    val recoverCpf: String? = null,
    val recoverPhone: String? = null,
    /** true = cartão perdido (sem chip antigo para zerar). */
    val lostCardMode: Boolean = false,
    val showConfirmBlock: Boolean = false,
    val showAskRecover: Boolean = false,
    val showCardOptions: Boolean = false,
    val showConsultResult: Boolean = false,
    val consultResultTitle: String? = null,
    val consultResultDetail: String? = null,
)

/** Crédito pendente no chip após pagamento já cobrado. */
data class PendingChipCredit(
    val amount: Double,
    val requireUid: String,
    val method: PaymentMethodApi,
    val clientRef: String,
    val operatorName: String,
    val pay: PaymentResult,
    val saleQueued: Boolean = false,
)

enum class CashlessRecoverStep {
    Idle,
    ReadingOld,
    WaitingNew,
    WaitingOldZero,
}

class CashlessViewModel(
    private val cashless: CashlessCardGateway,
    private val accounts: CashlessAccountRepository,
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

    fun onRegisterNameChange(value: String) {
        _state.update { it.copy(registerNameInput = value.take(80)) }
    }

    fun onRegisterCpfChange(value: String) {
        _state.update { it.copy(registerCpfInput = value.filter { c -> c.isDigit() }.take(11)) }
    }

    fun onRegisterPhoneChange(value: String) {
        _state.update { it.copy(registerPhoneInput = value.filter { c -> c.isDigit() }.take(11)) }
    }

    fun onLostCpfChange(value: String) {
        _state.update { it.copy(lostCpfInput = value.filter { c -> c.isDigit() }.take(11)) }
    }

    fun consultBalance() {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    loading = true,
                    waitingCard = true,
                    error = null,
                    message = "Aproxime o cartão na maquininha…",
                )
            }
            runCatching {
                val snap = cashless.readCard()
                applyCardRead(snap, defaultMessage = snap.message ?: "Leitura concluída")
            }.onFailure { e ->
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

    /**
     * Recarga: valor → aproxima cartão → se novo, cadastra CPF → pagamento → grava.
     */
    fun startTopUp() {
        val amount = parseAmount(_state.value.amountInput)
        if (amount == null || amount <= 0.0) {
            _state.update { it.copy(error = "Informe o valor a creditar (ex.: 10,00)") }
            return
        }
        refreshCashierStatus()
        viewModelScope.launch {
            _state.update {
                it.copy(
                    loading = true,
                    waitingCard = true,
                    pendingAmount = amount,
                    pendingUid = null,
                    showRegisterSheet = false,
                    showPaymentSheet = false,
                    error = null,
                    message = "Aproxime o cartão para identificar…",
                )
            }
            runCatching { cashless.readCard() }
                .onSuccess { snap ->
                    val account = accounts.getByUid(snap.uidHex)
                    // Sem cadastro ativo (novo, encerrado ou liberado) → só cadastrar CPF/telefone.
                    // Não exige desbloqueio prévio: o POST cria o vínculo novo no mesmo UID.
                    if (account == null) {
                        _state.update {
                            it.copy(
                                loading = false,
                                waitingCard = false,
                                card = snap,
                                pendingUid = snap.uidHex,
                                pendingAmount = amount,
                                accountCpf = null,
                                accountPhone = null,
                                accountName = null,
                                accountBlocked = false,
                                showRegisterSheet = true,
                                registerNameInput = "",
                                registerCpfInput = "",
                                registerPhoneInput = "",
                                message = "Cartão livre. Cadastre nome, CPF e telefone para continuar.",
                                error = null,
                            )
                        }
                        return@onSuccess
                    }
                    if (snap.isBlocked || account.blocked) {
                        _state.update {
                            it.copy(
                                loading = false,
                                waitingCard = false,
                                card = snap,
                                accountCpf = account.cpf.takeIf { c -> c.isNotBlank() },
                                accountPhone = account.phone.takeIf { p -> p.isNotBlank() },
                                accountName = account.name.takeIf { n -> n.isNotBlank() },
                                accountBlocked = true,
                                error = "Cartão bloqueado. Use Opções do cartão para recuperar o saldo.",
                                message = null,
                            )
                        }
                        return@onSuccess
                    }
                    syncBalanceFromSnap(snap)
                    _state.update {
                        it.copy(
                            loading = false,
                            waitingCard = false,
                            card = snap,
                            pendingUid = snap.uidHex,
                            pendingAmount = amount,
                            accountCpf = account.cpf,
                            accountPhone = account.phone,
                            accountName = account.name.takeIf { it.isNotBlank() },
                            accountBlocked = false,
                            showPaymentSheet = true,
                            message = buildString {
                                val label = account.name.trim().ifBlank { formatCpf(account.cpf) }
                                append("Cartão de $label. Escolha a forma de pagamento.")
                            },
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

    fun dismissRegisterSheet() {
        _state.update {
            it.copy(showRegisterSheet = false, pendingUid = null, pendingAmount = 0.0)
        }
    }

    fun submitRegister() {
        val uid = _state.value.pendingUid ?: return
        val name = _state.value.registerNameInput.trim()
        val cpf = _state.value.registerCpfInput.filter { it.isDigit() }
        val phone = _state.value.registerPhoneInput.filter { it.isDigit() }

        // CPF obrigatório; nome e celular opcionais (se preenchidos, validam).
        if (cpf.isEmpty()) {
            _state.update { it.copy(error = "Informe o CPF para cadastrar o cartão.") }
            return
        }
        if (cpf.length != 11) {
            _state.update { it.copy(error = "CPF deve ter 11 dígitos.") }
            return
        }
        if (!BrazilianDocumentValidator.isValidCpf(cpf)) {
            _state.update { it.copy(error = "CPF inválido. Confira os números digitados.") }
            return
        }
        if (phone.isNotEmpty()) {
            if (phone.length != 11) {
                _state.update {
                    it.copy(error = "Telefone deve ter 11 dígitos (DDD + número com 9 dígitos).")
                }
                return
            }
            if (!BrazilianDocumentValidator.isValidMobilePhone(phone)) {
                _state.update {
                    it.copy(error = "Telefone inválido. Use DDD + celular com 9 dígitos (ex.: 11987654321).")
                }
                return
            }
        }

        val balanceCents = ((_state.value.card?.balanceReais ?: 0.0) * 100).roundToInt()
        viewModelScope.launch {
            runCatching { accounts.register(uid, name, cpf, phone, balanceCents) }
                .onSuccess {
                    refreshCashierStatus()
                    _state.update {
                        it.copy(
                            showRegisterSheet = false,
                            showPaymentSheet = true,
                            accountName = name.takeIf { it.isNotEmpty() },
                            accountCpf = cpf,
                            accountPhone = phone.takeIf { it.isNotEmpty() },
                            error = null,
                            message = "Cadastro OK. Escolha a forma de pagamento.",
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(error = friendlyAccountError(e))
                    }
                }
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

    fun dismissConsultResult() {
        _state.update {
            it.copy(
                showConsultResult = false,
                consultResultTitle = null,
                consultResultDetail = null,
                message = null,
            )
        }
    }

    /** Fecha modal de sucesso/erro (avisos padronizados). */
    fun dismissFeedback() {
        _state.update { it.copy(message = null, error = null) }
    }

    /** Aproxima o cartão e imprime o extrato de movimentações. */
    fun printStatement() {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    loading = true,
                    waitingCard = true,
                    error = null,
                    message = "Aproxime o cartão para imprimir o extrato…",
                )
            }
            runCatching {
                val snap = cashless.readCard()
                val account = runCatching { accounts.getByUid(snap.uidHex) }.getOrNull()
                val revoked = snap.isBlocked ||
                    account?.blocked == true ||
                    runCatching { accounts.isUidRevokedForUse(snap.uidHex) }.getOrDefault(false)
                if (!revoked) {
                    runCatching { syncBalanceFromSnap(snap) }
                }
                var movements = accounts.listStatement(snap.uidHex, account?.cpf)
                if (revoked || movements.any { it.type == CashlessMovementType.TRANSF_SAIDA }) {
                    movements = accounts.ensureSubstituidoMovement(
                        snap.uidHex,
                        account?.cpf?.takeIf { it.isNotBlank() },
                    )
                }
                if (movements.isEmpty()) {
                    _state.update {
                        it.copy(
                            loading = false,
                            waitingCard = false,
                            card = snap,
                            accountCpf = account?.cpf?.takeIf { c -> c.isNotBlank() },
                            accountPhone = account?.phone?.takeIf { p -> p.isNotBlank() },
                            accountBlocked = revoked,
                            error = "Nenhuma movimentação registrada ainda para este cartão " +
                                "nesta maquininha. Recargas e transferências passam a entrar no extrato.",
                            message = null,
                        )
                    }
                    return@runCatching
                }
                val balanceReais = if (revoked) {
                    (account?.balanceCents ?: 0) / 100.0
                } else {
                    snap.balanceReais ?: (account?.balanceCents?.div(100.0) ?: 0.0)
                }
                val df = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR"))
                val payload = CashlessStatementPayload(
                    uidHex = snap.uidHex,
                    cpf = account?.cpf?.takeIf { it.isNotBlank() }?.let { formatCpf(it) },
                    phone = account?.phone?.takeIf { it.isNotBlank() },
                    balanceReais = balanceReais,
                    lines = movements.map { m ->
                        val label = movementLabel(m.type)
                        val noteSuffix = m.note?.takeIf { it.isNotBlank() }
                            ?.let { " · $it" }
                            .orEmpty()
                        CashlessStatementLine(
                            dateLabel = df.format(Date(m.createdAt)),
                            label = label + noteSuffix,
                            amountLabel = signedMoney(m.amountCents),
                            balanceAfterLabel = "R$ ${"%.2f".format(m.balanceAfterCents / 100.0)}",
                        )
                    },
                    terminalName = configStore.getDeviceName()?.takeIf { it.isNotBlank() }
                        ?: configStore.getDeviceShortId(),
                    establishmentName = configStore.getEstablishmentName(),
                )
                printer.printCashlessStatement(payload)
                _state.update {
                    it.copy(
                        loading = false,
                        waitingCard = false,
                        card = snap,
                        accountCpf = account?.cpf?.takeIf { c -> c.isNotBlank() },
                        accountPhone = account?.phone?.takeIf { p -> p.isNotBlank() },
                        accountBlocked = revoked,
                        message = null,
                        error = null,
                        showConsultResult = true,
                        consultResultTitle = if (revoked) "Extrato · cartão bloqueado" else "Extrato impresso",
                        consultResultDetail = buildString {
                            append("UID ${snap.uidHex}\n")
                            append("${movements.size} movimentações\n")
                            append("Saldo atual R$ ${"%.2f".format(balanceReais)}")
                            if (revoked) append("\nStatus: bloqueado / substituído")
                        },
                    )
                }
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        loading = false,
                        waitingCard = false,
                        error = friendlyCardError(e),
                        message = null,
                        showConsultResult = false,
                    )
                }
            }
        }
    }

    fun startZeroBalance() {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    loading = true,
                    waitingCard = true,
                    showConfirmZero = false,
                    error = null,
                    message = "Aproxime o cartão para zerar e encerrar…",
                )
            }
            runCatching {
                val snap = cashless.readCard()
                val account = runCatching { accounts.getByUid(snap.uidHex) }.getOrNull()
                val revoked = snap.isBlocked ||
                    account?.blocked == true ||
                    runCatching { accounts.isUidRevokedForUse(snap.uidHex) }.getOrDefault(false)
                val chipBalance = if (snap.isGate8Format) (snap.balanceReais ?: 0.0) else 0.0
                val hasActiveCadastro = account != null || revoked

                if (!snap.isGate8Format && !hasActiveCadastro) {
                    _state.update {
                        it.copy(
                            loading = false,
                            waitingCard = false,
                            card = snap,
                            error = "Cartão já está em branco e sem cadastro ativo.",
                            message = null,
                        )
                    }
                    return@runCatching
                }

                if (snap.isGate8Format && chipBalance <= 0.0 && !hasActiveCadastro) {
                    _state.update {
                        it.copy(
                            loading = false,
                            waitingCard = false,
                            card = snap,
                            error = "Cartão já está zerado e sem cadastro ativo.",
                            message = null,
                        )
                    }
                    return@runCatching
                }

                // Tem saldo no chip e/ou cadastro ativo → confirma limpeza + encerramento.
                _state.update {
                    it.copy(
                        loading = false,
                        waitingCard = false,
                        card = snap,
                        pendingUid = snap.uidHex,
                        recoverBalance = chipBalance,
                        accountBlocked = hasActiveCadastro,
                        accountName = account?.name?.takeIf { n -> n.isNotBlank() },
                        accountCpf = account?.cpf?.takeIf { c -> c.isNotBlank() },
                        accountPhone = account?.phone?.takeIf { p -> p.isNotBlank() },
                        showConfirmZero = true,
                        message = null,
                        error = null,
                    )
                }
            }.onFailure { e ->
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

    fun dismissConfirmZero() {
        _state.update {
            it.copy(
                showConfirmZero = false,
                pendingUid = null,
                recoverBalance = 0.0,
                accountBlocked = false,
            )
        }
    }

    fun confirmZeroBalance() {
        val uid = _state.value.pendingUid ?: return
        val previousBalance = _state.value.recoverBalance
        val cpf = _state.value.accountCpf
        viewModelScope.launch {
            _state.update {
                it.copy(
                    showConfirmZero = false,
                    loading = true,
                    waitingCard = true,
                    message = "Aproxime o mesmo cartão para limpar e encerrar…",
                    error = null,
                )
            }
            runCatching {
                val snap = cashless.wipeCard(requireUid = uid)
                accounts.recordMovement(
                    uidHex = uid,
                    type = CashlessMovementType.ZERAGEM,
                    amountCents = -((previousBalance * 100).roundToInt()),
                    balanceAfterCents = 0,
                    cpf = cpf,
                    note = "ENCERRAMENTO · chip limpo e cadastro encerrado",
                )
                accounts.closeCard(uid)
                snap
            }
                .onSuccess { snap ->
                    _state.update {
                        it.copy(
                            loading = false,
                            waitingCard = false,
                            card = snap,
                            pendingUid = null,
                            recoverBalance = 0.0,
                            accountBlocked = false,
                            accountName = null,
                            accountCpf = null,
                            accountPhone = null,
                            message = "Cartão limpo e encerrado no sistema. " +
                                "Pode cadastrar de novo na próxima festa.",
                            error = null,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            loading = false,
                            waitingCard = false,
                            error = when (e) {
                                is ApiException -> friendlyAccountError(e)
                                else -> friendlyCardError(e)
                            },
                            message = null,
                        )
                    }
                }
        }
    }

    fun openLostCard() {
        _state.update {
            it.copy(
                showCardOptions = false,
                showLostCpfSheet = true,
                lostCpfInput = "",
                lostCardMode = true,
                error = null,
                message = null,
            )
        }
    }

    fun dismissLostCpfSheet() {
        _state.update { it.copy(showLostCpfSheet = false) }
    }

    fun openCardOptions() {
        _state.update { it.copy(showCardOptions = true, error = null, message = null) }
    }

    fun dismissCardOptions() {
        _state.update { it.copy(showCardOptions = false) }
    }

    fun chooseLostOrStolen() {
        openLostCard()
    }

    fun chooseBlockCard() {
        _state.update { it.copy(showCardOptions = false) }
        startBlockCard()
    }

    fun chooseUnblockCard() {
        _state.update { it.copy(showCardOptions = false) }
        startUnblockCard()
    }

    fun chooseRecoverBalance() {
        _state.update { it.copy(showCardOptions = false) }
        startRecoverBalance()
    }

    fun chooseZeroBalance() {
        _state.update { it.copy(showCardOptions = false) }
        startZeroBalance()
    }

    fun searchLostByCpf() {
        val cpf = _state.value.lostCpfInput.filter { it.isDigit() }
        if (cpf.length != 11) {
            _state.update { it.copy(error = "Informe o CPF com 11 dígitos.") }
            return
        }
        if (!BrazilianDocumentValidator.isValidCpf(cpf)) {
            _state.update { it.copy(error = "CPF inválido. Confira os números digitados.") }
            return
        }
        viewModelScope.launch {
            runCatching { accounts.blockByCpf(cpf) }
                .onSuccess { account ->
                    val balance = account.balanceCents / 100.0
                    accounts.recordMovement(
                        uidHex = account.uidHex,
                        type = CashlessMovementType.BLOQUEIO,
                        amountCents = 0,
                        balanceAfterCents = account.balanceCents,
                        cpf = account.cpf,
                        note = "Bloqueio por CPF (cartão perdido)",
                    )
                    _state.update {
                        it.copy(
                            showLostCpfSheet = false,
                            recoverOldUid = account.uidHex,
                            recoverBalance = balance,
                            recoverCpf = account.cpf,
                            recoverPhone = account.phone,
                            lostCardMode = true,
                            accountCpf = account.cpf,
                            accountPhone = account.phone,
                            showAskRecover = true,
                            message = "Cartão ${account.uidHex} bloqueado pelo CPF. " +
                                "Saldo no sistema: R$ ${"%.2f".format(balance)}",
                            error = null,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(error = friendlyAccountError(e)) }
                }
        }
    }

    fun cancelRecoverWizard() {
        _state.update {
            it.copy(
                recoverStep = CashlessRecoverStep.Idle,
                recoverOldUid = null,
                recoverBalance = 0.0,
                recoverCpf = null,
                recoverPhone = null,
                lostCardMode = false,
                showConfirmBlock = false,
                showAskRecover = false,
                showCardOptions = false,
                loading = false,
                waitingCard = false,
                message = null,
                error = null,
            )
        }
    }

    fun startBlockCard() {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    loading = true,
                    waitingCard = true,
                    recoverStep = CashlessRecoverStep.ReadingOld,
                    lostCardMode = false,
                    showConfirmBlock = false,
                    showAskRecover = false,
                    showCardOptions = false,
                    error = null,
                    message = "Aproxime o cartão que deseja bloquear…",
                )
            }
            runCatching { cashless.readCard() }
                .onSuccess { snap ->
                    val account = accounts.getByUid(snap.uidHex)
                    val balance = when {
                        (snap.balanceReais ?: 0.0) > 0 -> snap.balanceReais ?: 0.0
                        account != null && account.balanceCents > 0 -> account.balanceCents / 100.0
                        else -> 0.0
                    }
                    if (balance <= 0.0 && account == null) {
                        _state.update {
                            it.copy(
                                loading = false,
                                waitingCard = false,
                                recoverStep = CashlessRecoverStep.Idle,
                                card = snap,
                                error = "Sem saldo e sem cadastro neste cartão.",
                                message = null,
                            )
                        }
                        return@onSuccess
                    }
                    if (snap.isBlocked || account?.blocked == true) {
                        _state.update {
                            it.copy(
                                loading = false,
                                waitingCard = false,
                                card = snap,
                                recoverOldUid = snap.uidHex,
                                recoverBalance = balance,
                                recoverCpf = account?.cpf,
                                recoverPhone = account?.phone,
                                recoverStep = CashlessRecoverStep.Idle,
                                lostCardMode = false,
                                showAskRecover = true,
                                accountCpf = account?.cpf,
                                accountPhone = account?.phone,
                                message = "Cartão já bloqueado · R$ ${"%.2f".format(balance)}",
                                error = null,
                            )
                        }
                    } else {
                        _state.update {
                            it.copy(
                                loading = false,
                                waitingCard = false,
                                card = snap,
                                recoverOldUid = snap.uidHex,
                                recoverBalance = balance,
                                recoverCpf = account?.cpf,
                                recoverPhone = account?.phone,
                                recoverStep = CashlessRecoverStep.Idle,
                                showConfirmBlock = true,
                                accountCpf = account?.cpf,
                                accountPhone = account?.phone,
                                message = null,
                                error = null,
                            )
                        }
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            loading = false,
                            waitingCard = false,
                            recoverStep = CashlessRecoverStep.Idle,
                            error = friendlyCardError(e),
                            message = null,
                        )
                    }
                }
        }
    }

    fun startUnblockCard() {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    loading = true,
                    waitingCard = true,
                    showCardOptions = false,
                    error = null,
                    message = "Aproxime o cartão para desbloquear…",
                )
            }
            runCatching { cashless.readCard() }
                .onSuccess { snap ->
                    val account = accounts.getByUid(snap.uidHex)
                    val chipBlocked = snap.isBlocked
                    val dbBlocked = account?.blocked == true
                    if (!chipBlocked && !dbBlocked) {
                        _state.update {
                            it.copy(
                                loading = false,
                                waitingCard = false,
                                card = snap,
                                accountCpf = account?.cpf,
                                accountPhone = account?.phone,
                                error = "Este cartão já está desbloqueado.",
                                message = null,
                            )
                        }
                        return@onSuccess
                    }
                    val balance = snap.balanceReais
                        ?: account?.balanceCents?.div(100.0)
                        ?: 0.0
                    _state.update {
                        it.copy(
                            message = "Aproxime o mesmo cartão para confirmar o desbloqueio…",
                        )
                    }
                    runCatching {
                        cashless.writeBalance(balance, blocked = false, requireUid = snap.uidHex)
                    }
                        .onSuccess { written ->
                            val cents = ((written.balanceReais ?: balance) * 100).roundToInt()
                            accounts.setBlocked(snap.uidHex, blocked = false, balanceCents = cents)
                            accounts.recordMovement(
                                uidHex = snap.uidHex,
                                type = CashlessMovementType.DESBLOQUEIO,
                                amountCents = 0,
                                balanceAfterCents = cents,
                                cpf = account?.cpf,
                                note = "Cartão desbloqueado",
                            )
                            _state.update {
                                it.copy(
                                    loading = false,
                                    waitingCard = false,
                                    card = written,
                                    accountCpf = account?.cpf,
                                    accountPhone = account?.phone,
                                    message = "Cartão desbloqueado · saldo R$ ${"%.2f".format(written.balanceReais ?: balance)}",
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

    /**
     * Recupera saldo de cartão já bloqueado (chip na mão) e oferece transferir para um novo.
     */
    fun startRecoverBalance() {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    loading = true,
                    waitingCard = true,
                    showCardOptions = false,
                    lostCardMode = false,
                    error = null,
                    message = "Aproxime o cartão bloqueado para recuperar o saldo…",
                )
            }
            runCatching { cashless.readCard() }
                .onSuccess { snap ->
                    val account = accounts.getByUid(snap.uidHex)
                    val chipBlocked = snap.isBlocked
                    val dbBlocked = account?.blocked == true
                    if (!chipBlocked && !dbBlocked) {
                        _state.update {
                            it.copy(
                                loading = false,
                                waitingCard = false,
                                card = snap,
                                accountCpf = account?.cpf,
                                accountPhone = account?.phone,
                                error = "Cartão não está bloqueado. Use Bloquear ou Perda/roubo antes.",
                                message = null,
                            )
                        }
                        return@onSuccess
                    }
                    val balance = when {
                        (snap.balanceReais ?: 0.0) > 0 -> snap.balanceReais ?: 0.0
                        account != null && account.balanceCents > 0 -> account.balanceCents / 100.0
                        else -> 0.0
                    }
                    if (balance <= 0.0) {
                        _state.update {
                            it.copy(
                                loading = false,
                                waitingCard = false,
                                card = snap,
                                accountCpf = account?.cpf,
                                accountPhone = account?.phone,
                                error = "Não há saldo para recuperar neste cartão.",
                                message = null,
                            )
                        }
                        return@onSuccess
                    }
                    _state.update {
                        it.copy(
                            loading = false,
                            waitingCard = false,
                            card = snap,
                            recoverOldUid = snap.uidHex,
                            recoverBalance = balance,
                            recoverCpf = account?.cpf,
                            recoverPhone = account?.phone,
                            accountCpf = account?.cpf,
                            accountPhone = account?.phone,
                            lostCardMode = false,
                            showAskRecover = true,
                            message = null,
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

    fun confirmBlockCard() {
        val uid = _state.value.recoverOldUid ?: return
        val balance = _state.value.recoverBalance
        viewModelScope.launch {
            _state.update {
                it.copy(
                    showConfirmBlock = false,
                    loading = true,
                    waitingCard = true,
                    message = "Aproxime o mesmo cartão para confirmar o bloqueio…",
                    error = null,
                )
            }
            runCatching { cashless.writeBalance(balance, blocked = true, requireUid = uid) }
                .onSuccess { snap ->
                    val cents = (balance * 100).roundToInt()
                    accounts.setBlocked(uid, blocked = true, balanceCents = cents)
                    accounts.recordMovement(
                        uidHex = uid,
                        type = CashlessMovementType.BLOQUEIO,
                        amountCents = 0,
                        balanceAfterCents = cents,
                        cpf = _state.value.recoverCpf ?: _state.value.accountCpf,
                        note = "Cartão bloqueado",
                    )
                    _state.update {
                        it.copy(
                            loading = false,
                            waitingCard = false,
                            card = snap,
                            recoverOldUid = uid,
                            recoverBalance = balance,
                            lostCardMode = false,
                            showAskRecover = true,
                            message = "Cartão bloqueado · R$ ${"%.2f".format(balance)}",
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

    fun dismissConfirmBlock() {
        _state.update {
            it.copy(
                showConfirmBlock = false,
                recoverStep = CashlessRecoverStep.Idle,
                recoverOldUid = null,
                recoverBalance = 0.0,
            )
        }
    }

    fun declineRecover() {
        val balance = _state.value.recoverBalance
        _state.update {
            it.copy(
                showAskRecover = false,
                recoverStep = CashlessRecoverStep.Idle,
                message = "Cartão bloqueado. Saldo R$ ${"%.2f".format(balance)} " +
                    "fica no sistema até transferir para um novo cartão.",
                error = null,
            )
        }
    }

    fun acceptRecover() {
        val balance = _state.value.recoverBalance
        if (balance <= 0.0) {
            _state.update {
                it.copy(showAskRecover = false, error = "Não há saldo para transferir.")
            }
            return
        }
        _state.update {
            it.copy(
                showAskRecover = false,
                recoverStep = CashlessRecoverStep.WaitingNew,
                loading = true,
                waitingCard = true,
                message = "Saldo a transferir: R$ ${"%.2f".format(balance)}. " +
                    "Aproxime o NOVO cartão…",
                error = null,
            )
        }
        viewModelScope.launch { transferToNewCard() }
    }

    private suspend fun transferToNewCard() {
        val oldUid = _state.value.recoverOldUid ?: run {
            cancelRecoverWizard()
            return
        }
        val balance = _state.value.recoverBalance
        val lostMode = _state.value.lostCardMode
        val cpf = _state.value.recoverCpf
        val phone = _state.value.recoverPhone
        runCatching {
            cashless.writeBalance(
                amountReais = balance,
                blocked = false,
                rejectUid = oldUid,
            )
        }
            .onSuccess { newSnap ->
                val cents = (balance * 100).roundToInt()
                if (cpf != null && phone != null) {
                    accounts.reassignUid(
                        oldUid = oldUid,
                        newUid = newSnap.uidHex,
                        balanceCents = cents,
                        cpf = cpf,
                        phone = phone,
                    )
                } else {
                    accounts.updateBalance(newSnap.uidHex, cents)
                    accounts.setBlocked(oldUid, blocked = true, balanceCents = 0)
                }
                accounts.recordMovement(
                    uidHex = oldUid,
                    type = CashlessMovementType.TRANSF_SAIDA,
                    amountCents = -cents,
                    balanceAfterCents = 0,
                    cpf = cpf,
                    note = "Para ${newSnap.uidHex}",
                )
                accounts.recordMovement(
                    uidHex = oldUid,
                    type = CashlessMovementType.SUBSTITUIDO,
                    amountCents = 0,
                    balanceAfterCents = 0,
                    cpf = cpf,
                    note = "Cartão substituído pelo UID ${newSnap.uidHex}",
                )
                accounts.recordMovement(
                    uidHex = newSnap.uidHex,
                    type = CashlessMovementType.TRANSF_ENTRADA,
                    amountCents = cents,
                    balanceAfterCents = cents,
                    cpf = cpf,
                    note = "De $oldUid",
                )
                if (lostMode) {
                    _state.update {
                        it.copy(
                            loading = false,
                            waitingCard = false,
                            recoverStep = CashlessRecoverStep.Idle,
                            recoverOldUid = null,
                            recoverBalance = 0.0,
                            recoverCpf = null,
                            recoverPhone = null,
                            lostCardMode = false,
                            card = newSnap,
                            accountCpf = cpf,
                            accountPhone = phone,
                            message = "Pronto! R$ ${"%.2f".format(balance)} no cartão novo. " +
                                "O antigo ficou bloqueado no sistema.",
                            error = null,
                        )
                    }
                } else {
                    _state.update {
                        it.copy(
                            card = newSnap,
                            recoverStep = CashlessRecoverStep.WaitingOldZero,
                            loading = true,
                            waitingCard = true,
                            message = "Novo cartão OK (R$ ${"%.2f".format(balance)}). " +
                                "Agora aproxime o cartão ANTIGO para zerar…",
                            error = null,
                        )
                    }
                    zeroOldCard(oldUid)
                }
            }
            .onFailure { e ->
                _state.update {
                    it.copy(
                        loading = false,
                        waitingCard = false,
                        recoverStep = CashlessRecoverStep.Idle,
                        showAskRecover = true,
                        error = friendlyCardError(e),
                        message = null,
                    )
                }
            }
    }

    private suspend fun zeroOldCard(oldUid: String) {
        runCatching {
            cashless.writeBalance(0.0, blocked = false, requireUid = oldUid)
        }
            .onSuccess { oldSnap ->
                accounts.updateBalance(oldUid, 0)
                val balance = _state.value.recoverBalance
                _state.update {
                    it.copy(
                        loading = false,
                        waitingCard = false,
                        recoverStep = CashlessRecoverStep.Idle,
                        recoverOldUid = null,
                        recoverBalance = 0.0,
                        card = oldSnap,
                        message = "Pronto! R$ ${"%.2f".format(balance)} no cartão novo. " +
                            "Cartão antigo zerado e pronto para reusar.",
                        error = null,
                    )
                }
            }
            .onFailure { e ->
                _state.update {
                    it.copy(
                        loading = false,
                        waitingCard = false,
                        recoverStep = CashlessRecoverStep.WaitingOldZero,
                        error = "Saldo já foi para o novo cartão, mas o antigo não zerou: " +
                            friendlyCardError(e),
                        message = "Aproxime o cartão ANTIGO de novo para zerar.",
                    )
                }
            }
    }

    fun retryZeroOldCard() {
        val oldUid = _state.value.recoverOldUid ?: return
        if (_state.value.recoverStep != CashlessRecoverStep.WaitingOldZero) return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    loading = true,
                    waitingCard = true,
                    error = null,
                    message = "Aproxime o cartão ANTIGO para zerar…",
                )
            }
            zeroOldCard(oldUid)
        }
    }

    fun checkout(method: PaymentMethodApi) {
        val amount = _state.value.pendingAmount.takeIf { it > 0 }
            ?: parseAmount(_state.value.amountInput)
        val requireUid = _state.value.pendingUid
        if (amount == null || amount <= 0.0) {
            _state.update {
                it.copy(showPaymentSheet = false, error = "Informe o valor a creditar (ex.: 10,00)")
            }
            return
        }
        if (requireUid.isNullOrBlank()) {
            _state.update {
                it.copy(showPaymentSheet = false, error = "Identifique o cartão antes de pagar.")
            }
            return
        }
        if (method == PaymentMethodApi.CASH && !_state.value.cashierOpen) {
            _state.update {
                it.copy(showPaymentSheet = false, error = "Caixa fechado. Abra o caixa na Home.")
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
                        requireUid = requireUid,
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
                requireUid = requireUid,
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
        requireUid: String,
    ) {
        _state.update {
            it.copy(
                waitingCard = true,
                loading = true,
                payingMethod = method,
                pendingAmount = amount,
                pendingUid = requireUid,
                pendingChipCredit = null,
                message = "Pagamento OK. Aproxime o MESMO cartão para gravar R$ ${"%.2f".format(amount)}…",
                error = null,
            )
        }

        val cardResult = runCatching { cashless.topUp(amount, requireUid = requireUid) }
        val snap = cardResult.getOrNull()
        val cardError = cardResult.exceptionOrNull()?.let { friendlyCardError(it) }

        if (snap != null) {
            completeChipCredit(
                amount = amount,
                method = method,
                clientRef = clientRef,
                operatorName = operatorName,
                pay = pay,
                snap = snap,
                saleAlreadyQueued = false,
            )
            return
        }

        // Pagamento já cobrado — registra a venda, mas NÃO imprime comprovante de recarga
        // até o chip ser creditado (evita recibo "ok" com cartão zerado).
        queueTopUpSale(
            amount = amount,
            method = method,
            clientRef = clientRef,
            operatorName = operatorName,
            pay = pay,
            cart = baseCart,
        )
        _state.update {
            it.copy(
                loading = false,
                waitingCard = false,
                payingMethod = null,
                pendingAmount = amount,
                pendingUid = requireUid,
                pendingChipCredit = PendingChipCredit(
                    amount = amount,
                    requireUid = requireUid,
                    method = method,
                    clientRef = clientRef,
                    operatorName = operatorName,
                    pay = pay,
                    saleQueued = true,
                ),
                message = null,
                error = "Pagamento de R$ ${"%.2f".format(amount)} OK, mas o cartão NÃO foi creditado: " +
                    "$cardError\n\nAproxime o cartão e toque em Creditar cartão.",
            )
        }
    }

    /** Segunda tentativa de gravar o saldo após pagamento já cobrado. */
    fun retryChipCredit() {
        val pending = _state.value.pendingChipCredit ?: return
        viewModelScope.launch {
            _state.update {
                it.copy(
                    loading = true,
                    waitingCard = true,
                    payingMethod = pending.method,
                    error = null,
                    message = "Aproxime o cartão ${pending.requireUid} para creditar " +
                        "R$ ${"%.2f".format(pending.amount)}…",
                )
            }
            val cardResult = runCatching {
                cashless.topUp(pending.amount, requireUid = pending.requireUid)
            }
            val snap = cardResult.getOrNull()
            if (snap != null) {
                completeChipCredit(
                    amount = pending.amount,
                    method = pending.method,
                    clientRef = pending.clientRef,
                    operatorName = pending.operatorName,
                    pay = pending.pay,
                    snap = snap,
                    saleAlreadyQueued = pending.saleQueued,
                )
            } else {
                val cardError = cardResult.exceptionOrNull()?.let { friendlyCardError(it) }
                _state.update {
                    it.copy(
                        loading = false,
                        waitingCard = false,
                        payingMethod = null,
                        error = "Ainda não creditou: $cardError\n\nTente de novo com o mesmo cartão.",
                        message = null,
                    )
                }
            }
        }
    }

    private suspend fun completeChipCredit(
        amount: Double,
        method: PaymentMethodApi,
        clientRef: String,
        operatorName: String,
        pay: PaymentResult,
        snap: CashlessCardSnapshot,
        saleAlreadyQueued: Boolean,
    ) {
        val cart = listOf(
            CartLine(
                itemType = ItemType.CUSTOM,
                description = "Recarga cashless",
                quantity = 1,
                unitPrice = amount,
            ),
        )
        val cpfMasked = maskCpf(_state.value.accountCpf)
        val saleId = if (!saleAlreadyQueued) {
            queueTopUpSale(
                amount = amount,
                method = method,
                clientRef = clientRef,
                operatorName = operatorName,
                pay = pay,
                cart = cart,
            )
        } else {
            saleAdmin.loadLastSale()
                ?.takeIf { it.clientReference == clientRef }
                ?.saleId
        }
        // Atualiza a última venda com UID/CPF para a reimpressão da Home.
        saleAdmin.recordCheckout(
            saleId = saleId,
            clientReference = clientRef,
            cart = cart,
            total = amount,
            method = method,
            payment = pay,
            cashlessUid = snap.uidHex,
            cashlessCpfMasked = cpfMasked,
            cashlessBalanceAfter = snap.balanceReais,
        )
        printer.printSaleSummary(
            cart,
            amount,
            method.apiValue,
            pay.nsu,
            pay.authorization,
            cashlessUid = snap.uidHex,
            cashlessCpfMasked = cpfMasked,
            cashlessBalanceAfter = snap.balanceReais,
        )
        viewModelScope.launch { pendingSaleSync.syncAll() }

        val cents = ((snap.balanceReais ?: 0.0) * 100).roundToInt()
        val addCents = (amount * 100).roundToInt()
        accounts.updateBalance(snap.uidHex, cents)
        accounts.recordMovement(
            uidHex = snap.uidHex,
            type = CashlessMovementType.RECARGA,
            amountCents = addCents,
            balanceAfterCents = cents,
            cpf = _state.value.accountCpf,
            note = method.displayLabel(),
        )
        _state.update {
            it.copy(
                loading = false,
                waitingCard = false,
                payingMethod = null,
                card = snap,
                amountInput = "",
                pendingAmount = 0.0,
                pendingUid = null,
                pendingChipCredit = null,
                message = snap.message
                    ?: "Recarga de R$ ${"%.2f".format(amount)} concluída",
                error = null,
            )
        }
    }

    private suspend fun queueTopUpSale(
        amount: Double,
        method: PaymentMethodApi,
        clientRef: String,
        operatorName: String,
        pay: PaymentResult,
        cart: List<CartLine>,
    ): String? {
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
        return saleId
    }

    private suspend fun applyCardRead(snap: CashlessCardSnapshot, defaultMessage: String) {
        val account = runCatching { accounts.getByUid(snap.uidHex) }.getOrNull()
        val revoked = snap.isBlocked ||
            account?.blocked == true ||
            runCatching { accounts.isUidRevokedForUse(snap.uidHex) }.getOrDefault(false)
        // Nunca espelha saldo do chip de volta se o cartão está inválido no sistema.
        if (!revoked) {
            runCatching { syncBalanceFromSnap(snap) }
        }
        val chipBalance = snap.balanceReais ?: 0.0
        val systemBalance = if (revoked) {
            (account?.balanceCents ?: 0) / 100.0
        } else {
            chipBalance
        }
        val releasedForReuse = !revoked &&
            runCatching {
                // Tem histórico de saída, mas já foi limpo (ZERAGEM/REUSO).
                !accounts.isUidRevokedForUse(snap.uidHex) &&
                    (snap.balanceReais ?: 0.0) <= 0.009
            }.getOrDefault(false)
        val status = when {
            revoked -> "Bloqueado · não liberado para uso"
            !snap.isGate8Format || chipBalance <= 0.009 ->
                if (releasedForReuse || account == null) {
                    "Em branco · zerado (pode cadastrar de novo)"
                } else {
                    "Em branco · zerado"
                }
            else -> "Pronto para usar"
        }
        val cpfDigits = account?.cpf?.filter { it.isDigit() }.orEmpty()
        val phoneDigits = account?.phone?.filter { it.isDigit() }.orEmpty()
        val holderName = account?.name?.trim().orEmpty()
        val detail = buildString {
            append("UID ${snap.uidHex}\n")
            if (revoked) {
                append("Saldo no sistema R$ ${"%.2f".format(systemBalance)}\n")
                if (chipBalance > 0.009) {
                    append("Residual no chip R$ ${"%.2f".format(chipBalance)} (não vale)\n")
                }
                append("Status: $status")
            } else {
                append("Saldo R$ ${"%.2f".format(chipBalance)}\n")
                append("Status: $status")
            }
            if (!revoked && holderName.isNotBlank()) {
                append("\nNome $holderName")
            }
            if (!revoked && cpfDigits.length == 11) {
                append("\nCPF ${formatCpf(cpfDigits)}")
                if (phoneDigits.isNotBlank()) append("\nTel. $phoneDigits")
            } else if (revoked) {
                append("\nSaldo já transferido ou cartão bloqueado.")
            } else {
                append("\n$defaultMessage")
            }
        }
        _state.update { state ->
            state.copy(
                loading = false,
                waitingCard = false,
                card = snap,
                accountName = holderName.takeIf { !revoked && it.isNotBlank() },
                accountCpf = cpfDigits.takeIf { digits -> !revoked && digits.length == 11 },
                accountPhone = phoneDigits.takeIf { digits -> !revoked && digits.isNotBlank() },
                accountBlocked = revoked,
                message = null,
                error = null,
                showConsultResult = true,
                consultResultTitle = if (revoked) "Cartão bloqueado" else "Consulta de saldo",
                consultResultDetail = detail,
            )
        }
    }

    private suspend fun syncBalanceFromSnap(snap: CashlessCardSnapshot) {
        if (!snap.isGate8Format) return
        val cents = ((snap.balanceReais ?: 0.0) * 100).roundToInt().coerceAtLeast(0)
        accounts.updateBalance(snap.uidHex, cents)
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

    private fun formatCpf(cpf: String): String {
        val d = cpf.filter { it.isDigit() }
        if (d.length != 11) return cpf
        return "${d.substring(0, 3)}.${d.substring(3, 6)}.${d.substring(6, 9)}-${d.substring(9)}"
    }

    /** LGPD: mostra só início e fim (ex.: 123.***.***-45). */
    private fun maskCpf(cpf: String?): String? {
        val d = cpf?.filter { it.isDigit() }.orEmpty()
        if (d.length != 11) return null
        return "${d.take(3)}.***.***-${d.takeLast(2)}"
    }

    private fun movementLabel(type: String): String = when (type) {
        CashlessMovementType.RECARGA -> "RECARGA"
        CashlessMovementType.ZERAGEM -> "ZERAGEM"
        CashlessMovementType.BLOQUEIO -> "BLOQUEIO"
        CashlessMovementType.DESBLOQUEIO -> "DESBLOQUEIO"
        CashlessMovementType.TRANSF_SAIDA -> "TRANSF. SAIDA"
        CashlessMovementType.TRANSF_ENTRADA -> "TRANSF. ENTRADA"
        CashlessMovementType.CONSUMO -> "CONSUMO"
        CashlessMovementType.ESTORNO -> "ESTORNO"
        CashlessMovementType.SUBSTITUIDO -> "SUBSTITUIDO"
        else -> type
    }

    private fun signedMoney(amountCents: Int): String {
        val value = kotlin.math.abs(amountCents) / 100.0
        val formatted = "R$ ${"%.2f".format(value)}"
        return if (amountCents < 0) "-$formatted" else "+$formatted"
    }

    private fun friendlyAccountError(e: Throwable): String = when (e) {
        is ApiException -> when (e.errorCode) {
            "invalid_cpf" -> "CPF inválido. Digite um CPF com 11 dígitos válidos."
            else -> e.message?.takeIf { it.isNotBlank() && it != e.errorCode }
                ?: "Erro no cadastro cashless"
        }
        else -> e.message ?: "Falha no cadastro cashless"
    }

    private fun friendlyCardError(e: Throwable): String = when (e) {
        is CashlessUnavailableException -> e.message ?: "Cashless indisponível neste aparelho"
        is CashlessOperationException -> e.message ?: "Falha na operação cashless"
        is TimeoutCancellationException ->
            "Tempo esgotado. Aproxime o cartão e tente de novo."
        else -> e.message ?: "Não foi possível falar com o cartão"
    }
}
