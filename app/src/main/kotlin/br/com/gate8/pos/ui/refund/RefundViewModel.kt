package br.com.gate8.pos.ui.refund

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.gate8.pos.core.sale.SaleAdminService
import br.com.gate8.pos.core.util.ProducerTokenValidator
import br.com.gate8.pos.data.prefs.DeviceConfigStore
import br.com.gate8.pos.domain.model.LastSaleRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

data class RefundUiState(
    val loading: Boolean = false,
    /** Vendas de hoje neste terminal (mais recente primeiro). */
    val sales: List<LastSaleRecord> = emptyList(),
    val query: String = "",
    val message: String? = null,
    val error: String? = null,
    val pendingVoid: LastSaleRecord? = null,
    val tokenInput: String = "",
    val tokenError: String? = null,
    /** Quando true, mostra o modal de "estorno concluído". */
    val voidSuccess: Boolean = false,
) {
    /** Vendas filtradas pelo texto de busca (NSU, valor, código). */
    val visibleSales: List<LastSaleRecord>
        get() {
            val q = query.trim().lowercase()
            if (q.isBlank()) return sales
            return sales.filter { sale ->
                val totalDot = "%.2f".format(sale.total)
                val totalComma = totalDot.replace('.', ',')
                sequenceOf(
                    sale.nsu,
                    sale.saleId,
                    sale.clientReference,
                    sale.authorization,
                    totalDot,
                    totalComma,
                ).any { it?.lowercase()?.contains(q) == true }
            }
        }
}

class RefundViewModel(
    private val saleAdmin: SaleAdminService,
    private val configStore: DeviceConfigStore,
) : ViewModel() {
    private val _state = MutableStateFlow(RefundUiState())
    val state: StateFlow<RefundUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun onScreenVisible() {
        refresh()
    }

    fun refresh() {
        _state.update {
            it.copy(
                sales = saleAdmin.loadRecentSales().filter { sale -> isToday(sale.createdAt) },
                error = null,
                message = null,
            )
        }
    }

    fun onQueryChange(value: String) {
        _state.update { it.copy(query = value) }
    }

    fun requestVoid(sale: LastSaleRecord) {
        _state.update { it.copy(pendingVoid = sale, tokenInput = "", tokenError = null) }
    }

    fun onTokenChange(value: String) {
        _state.update { it.copy(tokenInput = ProducerTokenValidator.normalize(value), tokenError = null) }
    }

    fun dismissConfirm() {
        _state.update { it.copy(pendingVoid = null, tokenInput = "", tokenError = null) }
    }

    fun confirmVoid() {
        val target = _state.value.pendingVoid ?: return
        val expected = configStore.getProducerToken()
        val entered = ProducerTokenValidator.normalize(_state.value.tokenInput)

        if (expected.isNullOrBlank()) {
            _state.update { it.copy(tokenError = "Token de login não encontrado. Faça login novamente.") }
            return
        }
        if (entered != expected) {
            _state.update { it.copy(tokenError = "Token incorreto. Use o token de login da maquininha.") }
            return
        }

        viewModelScope.launch {
            _state.update {
                it.copy(
                    loading = true,
                    pendingVoid = null,
                    tokenInput = "",
                    tokenError = null,
                    error = null,
                    message = null,
                )
            }
            saleAdmin.voidSale(target.clientReference)
                .onSuccess {
                    _state.update {
                        it.copy(
                            loading = false,
                            voidSuccess = true,
                            sales = saleAdmin.loadRecentSales().filter { s -> isToday(s.createdAt) },
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            loading = false,
                            error = e.message ?: "Falha no estorno",
                            sales = saleAdmin.loadRecentSales().filter { s -> isToday(s.createdAt) },
                        )
                    }
                }
        }
    }

    fun dismissVoidSuccess() {
        _state.update { it.copy(voidSuccess = false) }
    }

    fun clearFeedback() {
        _state.update { it.copy(message = null, error = null) }
    }

    private fun isToday(timestamp: Long): Boolean {
        val now = Calendar.getInstance()
        val that = Calendar.getInstance().apply { timeInMillis = timestamp }
        return now.get(Calendar.YEAR) == that.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == that.get(Calendar.DAY_OF_YEAR)
    }
}
