package br.com.gate8.pos.ui.checkin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.gate8.pos.data.repository.CheckinRepository
import br.com.gate8.pos.domain.model.CheckinOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CheckinUiState(
    val code: String = "",
    val loading: Boolean = false,
    val outcome: CheckinOutcome? = null,
    val message: String = "",
    val holderName: String? = null,
)

class CheckinViewModel(
    private val repository: CheckinRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(CheckinUiState())
    val state: StateFlow<CheckinUiState> = _state.asStateFlow()

    fun onCodeChange(value: String) {
        _state.update { it.copy(code = value, outcome = null) }
    }

    fun submit() {
        val code = _state.value.code.trim()
        if (code.length < 8) {
            _state.update { it.copy(message = "Informe o código do QR", outcome = CheckinOutcome.Invalid) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val result = repository.checkin(code)
            _state.update {
                it.copy(
                    loading = false,
                    outcome = result.outcome,
                    message = result.message,
                    holderName = result.holderName,
                )
            }
        }
    }
}
