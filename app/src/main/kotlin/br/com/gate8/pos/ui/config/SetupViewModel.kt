package br.com.gate8.pos.ui.config

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.gate8.pos.core.network.ApiException
import br.com.gate8.pos.core.sale.PendingSaleSync
import br.com.gate8.pos.core.sale.SaleAdminService
import br.com.gate8.pos.data.local.entity.PendingSaleStatus
import br.com.gate8.pos.data.prefs.DeviceConfigStore
import br.com.gate8.pos.data.repository.CashierRepository
import br.com.gate8.pos.data.repository.SaleRepository
import br.com.gate8.pos.domain.model.LastSaleRecord
import br.com.gate8.pos.device.PosHardwareInfo
import br.com.gate8.pos.stone.settings.StoneSettingsGateway
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Prefixo fixo do nome do operador para identificar vendas feitas na maquininha. */
private const val OPERATOR_PREFIX = "POS - "

data class SetupUiState(
    val operatorName: String = "",
    val stoneCode: String = "",
    val showStoneSection: Boolean = false,
    val stonePixConfigured: Boolean = false,
    val stonePosHint: String? = null,
    val stoneActivating: Boolean = false,
    val producerName: String? = null,
    val deviceName: String? = null,
    val deviceId: String? = null,
    val terminalManufacturer: String? = null,
    val terminalSerial: String? = null,
    val baseUrl: String? = null,
    val lastSale: LastSaleRecord? = null,
    val cashierOpen: Boolean = false,
    val cashierExpectedDrawer: Double = 0.0,
    val message: String? = null,
    val error: String? = null,
    val pendingSyncCount: Int = 0,
    val syncing: Boolean = false,
    val showClearPendingConfirm: Boolean = false,
    /** Modo ficha na conveniência: cada item sai em uma ficha separada. */
    val convenienceTicketMode: Boolean = false,
)

class SetupViewModel(
    private val configStore: DeviceConfigStore,
    private val saleAdmin: SaleAdminService,
    private val pendingSaleSync: PendingSaleSync,
    private val saleRepository: SaleRepository,
    private val cashierRepository: CashierRepository,
    private val stoneSettings: StoneSettingsGateway,
    private val hardwareInfo: PosHardwareInfo,
) : ViewModel() {
    private val _state = MutableStateFlow(SetupUiState())
    val state: StateFlow<SetupUiState> = _state.asStateFlow()

    init {
        refresh()
        syncPendingSales()
    }

    fun refresh() {
        val terminal = hardwareInfo.readTerminal()
        _state.update {
            it.copy(
                operatorName = stripOperatorPrefix(configStore.getOperatorName()),
                stoneCode = stoneSettings.getSavedStoneCode(),
                showStoneSection = stoneSettings.showStoneSection,
                stonePixConfigured = stoneSettings.pixCredentialsConfigured(),
                stonePosHint = stoneSettings.posActiveStoneCodesHint(),
                producerName = configStore.getProducerName(),
                deviceName = configStore.getDeviceName(),
                deviceId = configStore.getDeviceId(),
                terminalManufacturer = terminal.manufacturer,
                terminalSerial = terminal.serialNumber,
                baseUrl = configStore.getBaseUrl(),
                lastSale = saleAdmin.loadLastSale(),
                pendingSyncCount = 0,
                convenienceTicketMode = configStore.isConvenienceTicketMode(),
            )
        }
        viewModelScope.launch {
            _state.update { it.copy(pendingSyncCount = countPendingSync()) }
        }
        refreshCashierStatus()
    }

    private fun refreshCashierStatus() {
        viewModelScope.launch {
            runCatching { cashierRepository.fetchStatus() }
                .onSuccess { status ->
                    _state.update {
                        it.copy(
                            cashierOpen = status.open,
                            cashierExpectedDrawer = status.totals?.expectedDrawer ?: 0.0,
                        )
                    }
                }
        }
    }

    fun onScreenVisible() {
        refresh()
        syncPendingSales()
        refreshCashierStatus()
    }

    fun syncPendingSales() {
        if (_state.value.syncing) return
        viewModelScope.launch {
            _state.update { it.copy(syncing = true, error = null, message = null) }
            val pendingBefore = countPendingSync()
            _state.update { it.copy(pendingSyncCount = pendingBefore) }

            val result = pendingSaleSync.syncAll()
            val pendingAfter = countPendingSync()

            _state.update {
                it.copy(
                    syncing = false,
                    lastSale = saleAdmin.loadLastSale(),
                    pendingSyncCount = pendingAfter,
                    message = when {
                        result.synced > 0 && pendingAfter == 0 ->
                            "${result.synced} venda(s) enviada(s) ao servidor"
                        result.synced > 0 ->
                            "${result.synced} enviada(s); ainda há $pendingAfter pendente(s)"
                        else -> it.message
                    },
                    error = if (pendingAfter > 0) {
                        pendingSyncUserMessage(pendingAfter, result.lastError)
                    } else {
                        null
                    },
                )
            }
        }
    }

    fun requestClearPendingQueue() {
        _state.update { it.copy(showClearPendingConfirm = true) }
    }

    fun dismissClearPendingConfirm() {
        _state.update { it.copy(showClearPendingConfirm = false) }
    }

    /** Fecha o modal de aviso (sucesso ou erro) da tela de configurações. */
    fun dismissNotice() {
        _state.update { it.copy(message = null, error = null) }
    }

    fun confirmClearPendingQueue() {
        viewModelScope.launch {
            val removed = saleRepository.discardPendingQueue()
            _state.update {
                it.copy(
                    showClearPendingConfirm = false,
                    pendingSyncCount = 0,
                    error = null,
                    message = if (removed > 0) {
                        "Fila local limpa ($removed venda(s)). Faça novas vendas para registrar no servidor."
                    } else {
                        "Nenhuma venda pendente na fila."
                    },
                )
            }
        }
    }

    private fun pendingSyncUserMessage(count: Int, detail: String?): String {
        val base = "$count venda(s) na fila local — ainda não chegaram ao servidor."
        val reason = friendlySyncError(detail)
        return if (reason != null) "$base\n$reason" else base
    }

    private fun friendlySyncError(detail: String?): String? {
        if (detail.isNullOrBlank()) return "Toque em Enviar vendas ao servidor para tentar de novo."
        return when {
            detail.contains("Unable to resolve host", ignoreCase = true) ->
                "Sem internet: não alcançou gate8.club."
            detail.contains("Failed to connect", ignoreCase = true) ->
                "Não conectou ao servidor."
            detail.contains("401", ignoreCase = true) ||
                detail.contains("Token inválido", ignoreCase = true) ||
                detail.contains("unauthorized", ignoreCase = true) ->
                "Sessão expirada — faça login novamente."
            detail.contains("403", ignoreCase = true) ||
                detail.contains("inativo", ignoreCase = true) ->
                "Maquininha inativa no painel."
            detail.contains("insufficient_stock", ignoreCase = true) ->
                "Servidor ainda recusou por estoque. Se seus produtos são estoque livre, " +
                    "toque em Limpar fila de teste abaixo e faça vendas novas."
            detail.contains("product_not_available", ignoreCase = true) ->
                "Produto indisponível ou inativo no painel."
            detail.contains("batch_sold_out", ignoreCase = true) ||
                detail.contains("batch_expired", ignoreCase = true) ->
                "Lote de ingresso esgotado ou expirado no painel."
            else -> detail.take(160)
        }
    }

    private suspend fun countPendingSync(): Int =
        saleRepository.listPending().count { it.status == PendingSaleStatus.PENDING_SYNC }

    fun updateOperator(name: String) {
        _state.update { it.copy(operatorName = stripOperatorPrefix(name)) }
    }

    /** Remove o prefixo fixo "POS - " caso o valor salvo/colado já o contenha. */
    private fun stripOperatorPrefix(value: String): String {
        val trimmed = value.trim()
        return if (trimmed.startsWith(OPERATOR_PREFIX, ignoreCase = true)) {
            trimmed.substring(OPERATOR_PREFIX.length).trim()
        } else {
            trimmed
        }
    }

    fun updateStoneCode(code: String) {
        _state.update { it.copy(stoneCode = code) }
    }

    fun saveAndActivateStone() {
        if (_state.value.stoneActivating) return
        viewModelScope.launch {
            _state.update { it.copy(stoneActivating = true, error = null, message = null) }
            val result = stoneSettings.saveAndActivate(_state.value.stoneCode)
            _state.update {
                it.copy(
                    stoneActivating = false,
                    stoneCode = stoneSettings.getSavedStoneCode(),
                    message = result.getOrNull(),
                    error = result.exceptionOrNull()?.message,
                )
            }
        }
    }

    fun reactivateTerminal() {
        if (_state.value.stoneActivating) return
        viewModelScope.launch {
            _state.update { it.copy(stoneActivating = true, error = null, message = null) }
            val result = stoneSettings.reactivateTerminal()
            _state.update {
                it.copy(
                    stoneActivating = false,
                    message = result.getOrNull(),
                    error = result.exceptionOrNull()?.message,
                )
            }
        }
    }

    fun saveOperator() {
        val typed = stripOperatorPrefix(_state.value.operatorName).ifBlank { "Operador" }
        val name = OPERATOR_PREFIX + typed
        configStore.setOperatorName(name)
        _state.update {
            it.copy(
                operatorName = typed,
                message = "Operador salvo",
                error = null,
            )
        }
        if (!_state.value.cashierOpen) return
        viewModelScope.launch {
            runCatching { cashierRepository.updateOperator(name) }
                .onFailure {
                    _state.update {
                        it.copy(
                            error = "Nome salvo no aparelho. No painel, feche e reabra o caixa ou peça ao Lovable o endpoint PATCH /cashier/operator.",
                        )
                    }
                }
        }
    }

    fun setConvenienceTicketMode(enabled: Boolean) {
        configStore.setConvenienceTicketMode(enabled)
        _state.update {
            it.copy(
                convenienceTicketMode = enabled,
                message = if (enabled) {
                    "Modo ficha ligado: cada item sai em uma ficha separada"
                } else {
                    "Modo ficha desligado: itens saem em um recibo só"
                },
                error = null,
            )
        }
    }

    fun reprintLast() {
        saleAdmin.reprintLast()
            .onSuccess { msg -> _state.update { it.copy(message = msg, error = null) } }
            .onFailure { e -> _state.update { it.copy(error = e.message, message = null) } }
    }

    fun logout() {
        configStore.logout()
    }

    fun isLoggedIn(): Boolean = configStore.isLoggedIn()
}
