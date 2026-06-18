package br.com.gate8.pos.ui.refund

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.gate8.pos.core.sale.SaleAdminService
import br.com.gate8.pos.domain.model.LastSaleRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RefundUiState(
    val loading: Boolean = false,
    val sales: List<LastSaleRecord> = emptyList(),
    val message: String? = null,
    val error: String? = null,
    val pendingVoid: LastSaleRecord? = null,
)

class RefundViewModel(
    private val saleAdmin: SaleAdminService,
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
            it.copy(sales = saleAdmin.loadRecentSales(), error = null, message = null)
        }
    }

    fun requestVoid(sale: LastSaleRecord) {
        _state.update { it.copy(pendingVoid = sale) }
    }

    fun dismissConfirm() {
        _state.update { it.copy(pendingVoid = null) }
    }

    fun confirmVoid() {
        val target = _state.value.pendingVoid ?: return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, pendingVoid = null, error = null, message = null) }
            saleAdmin.voidSale(target.clientReference)
                .onSuccess { msg ->
                    _state.update {
                        it.copy(
                            loading = false,
                            message = msg,
                            sales = saleAdmin.loadRecentSales(),
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            loading = false,
                            error = e.message ?: "Falha no estorno",
                            sales = saleAdmin.loadRecentSales(),
                        )
                    }
                }
        }
    }

    fun clearFeedback() {
        _state.update { it.copy(message = null, error = null) }
    }
}
