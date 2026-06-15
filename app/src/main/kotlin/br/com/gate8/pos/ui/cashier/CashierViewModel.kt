package br.com.gate8.pos.ui.cashier

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.gate8.pos.data.prefs.DeviceConfigStore
import br.com.gate8.pos.data.remote.dto.CashierCloseSummaryDto
import br.com.gate8.pos.data.remote.dto.CashierMovementDto
import br.com.gate8.pos.data.remote.dto.CashierSessionDto
import br.com.gate8.pos.data.remote.dto.CashierStatusDto
import br.com.gate8.pos.data.remote.dto.CashierTotalsDto
import br.com.gate8.pos.data.repository.CashierRepository
import br.com.gate8.pos.printer.CashierPrintMovement
import br.com.gate8.pos.printer.CashierPrintPayload
import br.com.gate8.pos.printer.ReportPrintRow
import br.com.gate8.pos.printer.ReceiptPrinter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class CashierDialog {
    NONE,
    OPEN,
    WITHDRAWAL,
    EXPENSE,
    CLOSE,
}

data class CashierUiState(
    val loading: Boolean = false,
    val open: Boolean = false,
    val session: CashierSessionDto? = null,
    val totals: CashierTotalsDto? = null,
    val movements: List<CashierMovementDto> = emptyList(),
    val closeSummary: CashierCloseSummaryDto? = null,
    val dialog: CashierDialog = CashierDialog.NONE,
    val amountInput: String = "",
    val descriptionInput: String = "",
    val notesInput: String = "",
    val error: String? = null,
    val message: String? = null,
    val operatorLabel: String = "",
)

class CashierViewModel(
    private val cashierRepository: CashierRepository,
    private val printer: ReceiptPrinter,
    private val configStore: DeviceConfigStore,
) : ViewModel() {
    private val zone = ZoneId.of("America/Sao_Paulo")
    private val _state = MutableStateFlow(CashierUiState())
    val state: StateFlow<CashierUiState> = _state.asStateFlow()

    init {
        refreshOperatorLabel()
        refresh()
    }

    fun onScreenVisible() {
        refreshOperatorLabel()
        refresh()
    }

    private fun refreshOperatorLabel() {
        _state.update { it.copy(operatorLabel = configStore.getOperatorName()) }
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { cashierRepository.fetchStatus() }
                .onSuccess { status -> applyStatus(status) }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            loading = false,
                            error = e.message ?: "Não foi possível carregar o caixa",
                        )
                    }
                }
        }
    }

    fun showDialog(dialog: CashierDialog) {
        _state.update {
            it.copy(
                dialog = dialog,
                amountInput = "",
                descriptionInput = "",
                notesInput = "",
                error = null,
                message = null,
            )
        }
    }

    fun dismissDialog() {
        _state.update { it.copy(dialog = CashierDialog.NONE, error = null) }
    }

    fun updateAmount(value: String) {
        _state.update { it.copy(amountInput = value.filter { c -> c.isDigit() || c == ',' || c == '.' }) }
    }

    fun updateDescription(value: String) {
        _state.update { it.copy(descriptionInput = value) }
    }

    fun updateNotes(value: String) {
        _state.update { it.copy(notesInput = value) }
    }

    fun confirmOpen() {
        val amount = parseAmount(_state.value.amountInput)
        if (amount == null) {
            _state.update { it.copy(error = "Informe o troco inicial (ex.: 200,00)") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                cashierRepository.open(amount, configStore.getOperatorName(), _state.value.notesInput)
            }
                .onSuccess { status ->
                    applyStatus(status)
                    _state.update {
                        it.copy(
                            dialog = CashierDialog.NONE,
                            message = "Caixa aberto com troco R$ ${"%.2f".format(amount)}",
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(loading = false, error = e.message) }
                }
        }
    }

    fun confirmWithdrawal() {
        submitMovement(isExpense = false)
    }

    fun confirmExpense() {
        submitMovement(isExpense = true)
    }

    private fun submitMovement(isExpense: Boolean) {
        val amount = parseAmount(_state.value.amountInput)
        val description = _state.value.descriptionInput.trim()
        if (amount == null || amount <= 0) {
            _state.update { it.copy(error = "Informe um valor válido") }
            return
        }
        if (description.isEmpty()) {
            _state.update { it.copy(error = "Informe o motivo/descrição") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                if (isExpense) {
                    cashierRepository.expense(amount, description)
                } else {
                    cashierRepository.withdrawal(amount, description)
                }
            }
                .onSuccess { status ->
                    applyStatus(status)
                    _state.update {
                        it.copy(
                            dialog = CashierDialog.NONE,
                            message = if (isExpense) "Despesa registrada" else "Sangria registrada",
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(loading = false, error = e.message) }
                }
        }
    }

    fun confirmClose() {
        val counted = parseAmount(_state.value.amountInput)
        if (counted == null) {
            _state.update { it.copy(error = "Informe quanto há na gaveta") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                cashierRepository.close(counted, _state.value.notesInput)
            }
                .onSuccess { status ->
                    _state.update {
                        it.copy(
                            loading = false,
                            open = status.open,
                            session = status.session,
                            totals = status.totals,
                            movements = status.movements,
                            closeSummary = status.summary,
                            dialog = CashierDialog.NONE,
                            message = "Caixa fechado",
                        )
                    }
                    printCloseSummary(status)
                }
                .onFailure { e ->
                    _state.update { it.copy(loading = false, error = e.message) }
                }
        }
    }

    fun printCurrentSummary() {
        val summary = _state.value.closeSummary
        if (summary != null) {
            printFromCloseSummary(summary)
            _state.update { it.copy(message = "Relatório enviado à impressora") }
            return
        }
        val session = _state.value.session
        val totals = _state.value.totals
        if (session == null || totals == null) {
            _state.update { it.copy(error = "Nenhum turno para imprimir") }
            return
        }
        printer.printCashierSummary(buildPrintPayload(session, totals, _state.value.movements, null))
        _state.update { it.copy(message = "Relatório enviado à impressora", error = null) }
    }

    private fun printCloseSummary(status: CashierStatusDto) {
        val summary = status.summary ?: return
        printFromCloseSummary(summary)
    }

    private fun printFromCloseSummary(summary: CashierCloseSummaryDto) {
        val session = summary.session ?: return
        val totals = summary.totals ?: return
        printer.printCashierSummary(
            buildPrintPayload(
                session = session,
                totals = totals,
                movements = summary.movements,
                difference = summary.difference,
                countedBalance = summary.countedBalance,
            ),
        )
    }

    private fun buildPrintPayload(
        session: CashierSessionDto,
        totals: CashierTotalsDto,
        movements: List<CashierMovementDto>,
        difference: Double?,
        countedBalance: Double? = session.countedBalance,
    ): CashierPrintPayload {
        return CashierPrintPayload(
            deviceName = configStore.getDeviceName(),
            producerName = configStore.getProducerName(),
            operatorName = configStore.getOperatorName().ifBlank { session.operatorName },
            openedAtLabel = formatInstantLabel(session.openedAt),
            closedAtLabel = session.closedAt?.let { formatInstantLabel(it) },
            openingBalance = totals.openingBalance,
            cashSales = totals.cashSales,
            withdrawals = totals.withdrawals,
            expenses = totals.expenses,
            expectedDrawer = totals.expectedDrawer,
            countedBalance = countedBalance,
            difference = difference ?: session.difference,
            saleCount = totals.saleCount,
            grandTotal = totals.grandTotal,
            byPaymentMethod = totals.byPaymentMethod.map { ReportPrintRow(it.label, it.count, it.total) },
            movements = movements.map {
                CashierPrintMovement(
                    typeLabel = if (it.type == "expense") "Despesa" else "Sangria",
                    amount = it.amount,
                    description = it.description,
                    timeLabel = it.createdAt?.let { ts -> formatInstantLabel(ts) },
                )
            },
        )
    }

    private fun applyStatus(status: CashierStatusDto) {
        _state.update {
            it.copy(
                loading = false,
                open = status.open,
                session = status.session,
                totals = status.totals,
                movements = status.movements,
                closeSummary = status.summary ?: it.closeSummary,
            )
        }
    }

    private fun parseAmount(raw: String): Double? {
        val normalized = raw.trim().replace(",", ".")
        if (normalized.isEmpty()) return null
        return normalized.toDoubleOrNull()
    }

    private fun formatInstantLabel(iso: String?): String {
        if (iso.isNullOrBlank()) return "—"
        return runCatching {
            val instant = Instant.parse(iso)
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                .withZone(zone)
                .format(instant)
        }.getOrDefault(iso)
    }
}
