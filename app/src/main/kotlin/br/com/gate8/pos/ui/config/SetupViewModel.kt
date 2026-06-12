package br.com.gate8.pos.ui.config

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.gate8.pos.core.network.ApiException
import br.com.gate8.pos.core.sale.PendingSaleSync
import br.com.gate8.pos.core.sale.SaleAdminService
import br.com.gate8.pos.data.local.entity.PendingSaleStatus
import br.com.gate8.pos.data.prefs.DeviceConfigStore
import br.com.gate8.pos.data.repository.SaleRepository
import br.com.gate8.pos.domain.model.LastSaleRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SetupUiState(
    val operatorName: String = "",
    val producerName: String? = null,
    val deviceName: String? = null,
    val deviceId: String? = null,
    val baseUrl: String? = null,
    val lastSale: LastSaleRecord? = null,
    val message: String? = null,
    val error: String? = null,
    val pendingSyncCount: Int = 0,
    val syncing: Boolean = false,
    val showClearPendingConfirm: Boolean = false,
)

class SetupViewModel(
    private val configStore: DeviceConfigStore,
    private val saleAdmin: SaleAdminService,
    private val pendingSaleSync: PendingSaleSync,
    private val saleRepository: SaleRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SetupUiState())
    val state: StateFlow<SetupUiState> = _state.asStateFlow()

    init {
        refresh()
        syncPendingSales()
    }

    fun refresh() {
        _state.update {
            it.copy(
                operatorName = configStore.getOperatorName(),
                producerName = configStore.getProducerName(),
                deviceName = configStore.getDeviceName(),
                deviceId = configStore.getDeviceId(),
                baseUrl = configStore.getBaseUrl(),
                lastSale = saleAdmin.loadLastSale(),
                pendingSyncCount = 0,
            )
        }
        viewModelScope.launch {
            _state.update { it.copy(pendingSyncCount = countPendingSync()) }
        }
    }

    fun onScreenVisible() {
        refresh()
        syncPendingSales()
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
        _state.update { it.copy(operatorName = name) }
    }

    fun saveOperator() {
        configStore.setOperatorName(_state.value.operatorName.trim().ifBlank { "Operador POS" })
        _state.update { it.copy(message = "Operador salvo") }
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
