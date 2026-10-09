package br.com.gate8.pos.ui.refund

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.gate8.pos.cashless.CashlessCardGateway
import br.com.gate8.pos.core.sale.SaleAdminService
import br.com.gate8.pos.data.prefs.DeviceConfigStore
import br.com.gate8.pos.domain.model.LastSaleRecord
import br.com.gate8.pos.domain.model.PaymentMethodApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

data class RefundUiState(
    val loading: Boolean = false,
    /** Aguardando aproximação do cartão cashless para devolver o saldo. */
    val waitingCashlessCard: Boolean = false,
    val waitingCashlessAmount: Double = 0.0,
    /** Vendas de hoje neste terminal (mais recente primeiro). */
    val sales: List<LastSaleRecord> = emptyList(),
    val query: String = "",
    val message: String? = null,
    val error: String? = null,
    val pendingVoid: LastSaleRecord? = null,
    /** Aguardando o cartão cashless do gerente para liberar o estorno. */
    val waitingManagerCard: Boolean = false,
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
    private val cashlessCard: CashlessCardGateway,
) : ViewModel() {
    private val _state = MutableStateFlow(RefundUiState())
    val state: StateFlow<RefundUiState> = _state.asStateFlow()
    private var managerJob: Job? = null

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
        if (_state.value.loading || _state.value.waitingManagerCard || sale.voided) return
        val expected = configStore.getManagerCardUid()
        if (expected.isNullOrBlank()) {
            _state.update {
                it.copy(
                    error = "Cadastre o cartão do gerente em Configurações para liberar o estorno.",
                    message = null,
                )
            }
            return
        }
        _state.update {
            it.copy(
                pendingVoid = sale,
                waitingManagerCard = true,
                error = null,
                message = null,
            )
        }
        managerJob?.cancel()
        managerJob = viewModelScope.launch {
            val snap = try {
                cashlessCard.readCard()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        waitingManagerCard = false,
                        pendingVoid = null,
                        error = e.message ?: "Não foi possível ler o cartão do gerente.",
                    )
                }
                return@launch
            }
            ensureActive()
            if (!_state.value.waitingManagerCard) return@launch
            val uid = snap.uidHex.trim()
            if (!uid.equals(expected, ignoreCase = true)) {
                _state.update {
                    it.copy(
                        waitingManagerCard = false,
                        pendingVoid = null,
                        error = "Cartão não autorizado. Aproxime o cartão do gerente cadastrado.",
                    )
                }
                return@launch
            }
            performVoid(sale)
        }
    }

    fun cancelManagerWait() {
        if (!_state.value.waitingManagerCard) return
        managerJob?.cancel()
        managerJob = null
        _state.update { it.copy(waitingManagerCard = false, pendingVoid = null) }
    }

    private fun performVoid(target: LastSaleRecord) {
        viewModelScope.launch {
            val isCashless = target.paymentMethod == PaymentMethodApi.CASHLESS.apiValue
            _state.update {
                it.copy(
                    loading = true,
                    waitingManagerCard = false,
                    waitingCashlessCard = isCashless,
                    waitingCashlessAmount = if (isCashless) target.total else 0.0,
                    pendingVoid = null,
                    error = null,
                    message = null,
                )
            }
            saleAdmin.voidSale(target.clientReference)
                .onSuccess { msg ->
                    _state.update {
                        it.copy(
                            loading = false,
                            waitingCashlessCard = false,
                            waitingCashlessAmount = 0.0,
                            voidSuccess = true,
                            message = msg,
                            sales = saleAdmin.loadRecentSales().filter { s -> isToday(s.createdAt) },
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            loading = false,
                            waitingCashlessCard = false,
                            waitingCashlessAmount = 0.0,
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

    override fun onCleared() {
        managerJob?.cancel()
        super.onCleared()
    }

    private fun isToday(timestamp: Long): Boolean {
        val now = Calendar.getInstance()
        val that = Calendar.getInstance().apply { timeInMillis = timestamp }
        return now.get(Calendar.YEAR) == that.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == that.get(Calendar.DAY_OF_YEAR)
    }
}
