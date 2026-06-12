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
    val lastSale: LastSaleRecord? = null,
    val message: String? = null,
    val error: String? = null,
    val showConfirm: Boolean = false,
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
        _state.update { it.copy(lastSale = saleAdmin.loadLastSale(), error = null, message = null) }
    }

    fun requestVoid() {
        _state.update { it.copy(showConfirm = true) }
    }

    fun dismissConfirm() {
        _state.update { it.copy(showConfirm = false) }
    }

    fun confirmVoid() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, showConfirm = false, error = null, message = null) }
            saleAdmin.voidLastSale()
                .onSuccess { msg ->
                    _state.update {
                        it.copy(
                            loading = false,
                            message = msg,
                            lastSale = saleAdmin.loadLastSale(),
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(loading = false, error = e.message ?: "Falha no estorno")
                    }
                }
        }
    }

    fun clearFeedback() {
        _state.update { it.copy(message = null, error = null) }
    }
}
