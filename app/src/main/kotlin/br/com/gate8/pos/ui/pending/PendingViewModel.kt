package br.com.gate8.pos.ui.pending

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.gate8.pos.data.local.entity.PendingSaleStatus
import br.com.gate8.pos.data.repository.SaleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PendingUiState(
    val items: List<String> = emptyList(),
    val message: String? = null,
)

class PendingViewModel(
    private val saleRepository: SaleRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(PendingUiState())
    val state: StateFlow<PendingUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val list = saleRepository.listPending()
            _state.update {
                it.copy(
                    items = list.map { p ->
                        "${p.clientReference} · ${p.status} · ${p.lastError ?: ""}"
                    },
                )
            }
        }
    }

    fun syncAll() {
        viewModelScope.launch {
            val pending = saleRepository.listPending().filter {
                it.status == PendingSaleStatus.PENDING_SYNC
            }
            var ok = 0
            var fail = 0
            pending.forEach { entity ->
                runCatching { saleRepository.syncPending(entity) }
                    .onSuccess { ok++ }
                    .onFailure { fail++ }
            }
            refresh()
            _state.update { it.copy(message = "Sync: $ok ok, $fail falhas") }
        }
    }
}
