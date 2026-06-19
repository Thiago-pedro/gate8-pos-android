package br.com.gate8.pos.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.gate8.pos.data.prefs.DeviceConfigStore
import br.com.gate8.pos.data.remote.dto.CashierStatusDto
import br.com.gate8.pos.data.remote.dto.ReportsSummaryDto
import br.com.gate8.pos.data.repository.CashierRepository
import br.com.gate8.pos.data.repository.ReportsRepository
import br.com.gate8.pos.printer.ReportCashierInfo
import br.com.gate8.pos.printer.ReportPrintItem
import br.com.gate8.pos.printer.ReportPrintPayload
import br.com.gate8.pos.printer.ReportPrintRow
import br.com.gate8.pos.printer.ReceiptPrinter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

enum class ReportPeriod(val label: String) {
    TODAY("Hoje"),
    WEEK("Semana"),
    MONTH("Mês"),
    CUSTOM("Personalizado"),
}

data class ReportsUiState(
    val loading: Boolean = false,
    val period: ReportPeriod = ReportPeriod.TODAY,
    val customFrom: LocalDate? = null,
    val customTo: LocalDate? = null,
    val showCustomDialog: Boolean = false,
    val data: ReportsSummaryDto? = null,
    val isDemoData: Boolean = false,
    val cashier: CashierStatusDto? = null,
    val error: String? = null,
    val periodLabel: String = "",
    val printMessage: String? = null,
)

class ReportsViewModel(
    private val reportsRepository: ReportsRepository,
    private val printer: ReceiptPrinter,
    private val configStore: DeviceConfigStore,
    private val cashierRepository: CashierRepository,
) : ViewModel() {
    private val zone = ZoneId.of("America/Sao_Paulo")
    private val _state = MutableStateFlow(ReportsUiState())
    val state: StateFlow<ReportsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun onScreenVisible() {
        load()
    }

    fun selectPeriod(period: ReportPeriod) {
        if (period == ReportPeriod.CUSTOM) {
            val today = LocalDate.now(zone)
            _state.update {
                it.copy(
                    period = period,
                    showCustomDialog = true,
                    customFrom = it.customFrom ?: today.minusDays(7),
                    customTo = it.customTo ?: today,
                )
            }
        } else {
            _state.update { it.copy(period = period, showCustomDialog = false) }
            load()
        }
    }

    fun dismissCustomDialog() {
        _state.update { it.copy(showCustomDialog = false) }
    }

    fun updateCustomFrom(value: LocalDate) {
        _state.update { it.copy(customFrom = value) }
    }

    fun updateCustomTo(value: LocalDate) {
        _state.update { it.copy(customTo = value) }
    }

    fun applyCustomPeriod() {
        val from = _state.value.customFrom
        val to = _state.value.customTo
        if (from == null || to == null) {
            _state.update { it.copy(error = "Informe as duas datas") }
            return
        }
        if (from.isAfter(to)) {
            _state.update { it.copy(error = "Data inicial não pode ser depois da final") }
            return
        }
        _state.update { it.copy(showCustomDialog = false, error = null) }
        load()
    }

    fun refresh() {
        load()
    }

    fun printReport() {
        val data = _state.value.data
        if (data == null) {
            _state.update { it.copy(printMessage = null, error = "Carregue o relatório antes de imprimir") }
            return
        }
        val s = data.summary
        printer.printReportSummary(
            ReportPrintPayload(
                periodLabel = _state.value.periodLabel,
                deviceName = data.device?.name ?: configStore.getDeviceName(),
                producerName = configStore.getProducerName(),
                saleCount = s.saleCount,
                voidCount = s.voidCount,
                grossTotal = s.grossTotal,
                voidTotal = s.voidTotal,
                netTotal = s.netTotal,
                averageTicket = s.averageTicket,
                byPaymentMethod = data.byPaymentMethod.map { ReportPrintRow(it.label, it.count, it.total) },
                byBrand = data.byBrand.map { ReportPrintRow(it.brand, it.count, it.total) },
                topItems = data.topItems.map { ReportPrintItem(it.name, it.quantity, it.total) },
                cashier = _state.value.cashier?.toReportCashierInfo(),
            ),
        )
        _state.update { it.copy(printMessage = "Relatório enviado à impressora", error = null) }
    }

    private fun load() {
        val (from, to, label) = resolveRange()
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, printMessage = null, periodLabel = label) }
            val cashier = runCatching { cashierRepository.fetchStatus() }.getOrNull()
            runCatching { reportsRepository.fetchSummary(from, to) }
                .onSuccess { summary ->
                    val isDemo = summary.device?.id == "demo"
                    _state.update {
                        it.copy(
                            loading = false,
                            data = summary,
                            isDemoData = isDemo,
                            cashier = cashier,
                            periodLabel = label,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            loading = false,
                            cashier = cashier,
                            error = e.message ?: "Não foi possível carregar o relatório",
                        )
                    }
                }
        }
    }

    private fun resolveRange(): Triple<Instant, Instant, String> {
        val now = ZonedDateTime.now(zone)
        return when (_state.value.period) {
            ReportPeriod.TODAY -> {
                val start = now.toLocalDate().atStartOfDay(zone)
                Triple(start.toInstant(), now.toInstant(), "Hoje · ${formatDate(now.toLocalDate())}")
            }
            ReportPeriod.WEEK -> {
                val start = now.minusDays(6).toLocalDate().atStartOfDay(zone)
                Triple(
                    start.toInstant(),
                    now.toInstant(),
                    "Últimos 7 dias · ${formatDate(start.toLocalDate())} – ${formatDate(now.toLocalDate())}",
                )
            }
            ReportPeriod.MONTH -> {
                val start = now.withDayOfMonth(1).toLocalDate().atStartOfDay(zone)
                Triple(
                    start.toInstant(),
                    now.toInstant(),
                    "Mês · ${formatDate(start.toLocalDate())} – ${formatDate(now.toLocalDate())}",
                )
            }
            ReportPeriod.CUSTOM -> {
                val fromDate = _state.value.customFrom ?: now.toLocalDate()
                val toDate = _state.value.customTo ?: now.toLocalDate()
                val start = fromDate.atStartOfDay(zone)
                val end = toDate.atTime(23, 59, 59).atZone(zone)
                Triple(
                    start.toInstant(),
                    end.toInstant(),
                    "${formatDate(fromDate)} – ${formatDate(toDate)}",
                )
            }
        }
    }

    private fun formatDate(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))

    private fun CashierStatusDto.toReportCashierInfo(): ReportCashierInfo = ReportCashierInfo(
        open = open,
        operatorName = session?.operatorName,
        openingBalance = totals?.openingBalance ?: session?.openingBalance ?: 0.0,
        cashSales = totals?.cashSales ?: 0.0,
        withdrawals = totals?.withdrawals ?: 0.0,
        expenses = totals?.expenses ?: 0.0,
        expectedDrawer = totals?.expectedDrawer ?: session?.expectedBalance ?: 0.0,
        countedBalance = session?.countedBalance,
        difference = session?.difference,
    )
}
