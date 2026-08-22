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
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

enum class ReportPeriod(val label: String) {
    TODAY("Hoje"),
    WEEK("Semana"),
    MONTH("Mês"),
    CUSTOM("Personalizado"),
}

enum class ReportSegment(val label: String, val apiValue: String) {
    ALL("Tudo", "all"),
    TICKET("Bilheteria", "ticket"),
    PRODUCT("Conveniência", "product"),
}

data class ReportsUiState(
    val loading: Boolean = false,
    val period: ReportPeriod = ReportPeriod.TODAY,
    val segment: ReportSegment = ReportSegment.ALL,
    val customFrom: LocalDateTime? = null,
    val customTo: LocalDateTime? = null,
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
            val now = LocalDateTime.now(zone).withSecond(0).withNano(0)
            _state.update {
                it.copy(
                    period = period,
                    showCustomDialog = true,
                    customFrom = it.customFrom
                        ?: now.toLocalDate().minusDays(7).atStartOfDay(),
                    customTo = it.customTo ?: now,
                )
            }
        } else {
            _state.update { it.copy(period = period, showCustomDialog = false) }
            load()
        }
    }

    fun selectSegment(segment: ReportSegment) {
        if (_state.value.segment == segment) return
        _state.update { it.copy(segment = segment) }
        load()
    }

    fun dismissCustomDialog() {
        _state.update { it.copy(showCustomDialog = false) }
    }

    fun updateCustomFrom(value: LocalDateTime) {
        _state.update { it.copy(customFrom = value) }
    }

    fun updateCustomTo(value: LocalDateTime) {
        _state.update { it.copy(customTo = value) }
    }

    fun applyCustomPeriod() {
        val from = _state.value.customFrom
        val to = _state.value.customTo
        if (from == null || to == null) {
            _state.update { it.copy(error = "Informe data e horário de início e fim") }
            return
        }
        if (from.isAfter(to)) {
            _state.update { it.copy(error = "Início não pode ser depois do fim") }
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
                segmentLabel = _state.value.segment.takeIf { it != ReportSegment.ALL }?.label,
                deviceName = data.device?.name ?: configStore.getDeviceName(),
                producerName = configStore.getEstablishmentName(),
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
            runCatching { reportsRepository.fetchSummary(from, to, _state.value.segment.apiValue) }
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
                val fromDateTime = _state.value.customFrom
                    ?: now.toLocalDate().atStartOfDay()
                val toDateTime = _state.value.customTo ?: now.toLocalDateTime()
                val start = fromDateTime.atZone(zone)
                val end = toDateTime.atZone(zone)
                Triple(
                    start.toInstant(),
                    end.toInstant(),
                    "${formatDateTime(fromDateTime)} – ${formatDateTime(toDateTime)}",
                )
            }
        }
    }

    private fun formatDate(date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))

    private fun formatDateTime(value: LocalDateTime): String =
        value.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))

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
