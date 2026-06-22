package br.com.gate8.pos.ui.cashier

import android.util.Log
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
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

enum class CashierDialog {
    NONE,
    OPEN,
    WITHDRAWAL,
    EXPENSE,
    CLOSE,
}

/** Dados do modal de sucesso (abertura/fechamento de caixa). */
data class CashierSuccessUi(
    val title: String,
    val detail: String? = null,
)

/** Aviso de quebra de caixa: diferença entre o contado e o esperado ao fechar. */
data class CashierBreakUi(
    val counted: Double,
    val expected: Double,
    val difference: Double,
)

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
    /** Quando preenchido, mostra o modal de sucesso (caixa aberto/fechado). */
    val successModal: CashierSuccessUi? = null,
    /** Comprovante do último fechamento, montado localmente, para (re)impressão. */
    val lastClosePayload: CashierPrintPayload? = null,
    val operatorLabel: String = "",
    /** Quando preenchido, mostra o aviso de quebra de caixa pedindo confirmação. */
    val pendingCloseConfirm: CashierBreakUi? = null,
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
        _state.update { it.copy(dialog = CashierDialog.NONE, error = null, pendingCloseConfirm = null) }
    }

    fun dismissSuccessModal() {
        _state.update { it.copy(successModal = null) }
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
                            // A abertura foi bem-sucedida: garantimos o estado "aberto"
                            // mesmo que a resposta da API não traga o campo open=true.
                            open = true,
                            dialog = CashierDialog.NONE,
                            successModal = CashierSuccessUi(
                                title = "Caixa aberto",
                                detail = "Troco inicial: R$ ${"%.2f".format(amount)}",
                            ),
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
        // Se há divergência entre o contado e o esperado, avisa da quebra de caixa
        // e pede confirmação antes de fechar de fato.
        val expected = _state.value.totals?.expectedDrawer ?: 0.0
        val diff = counted - expected
        if (kotlin.math.abs(diff) >= 0.01) {
            _state.update {
                it.copy(
                    pendingCloseConfirm = CashierBreakUi(counted, expected, diff),
                    error = null,
                )
            }
            return
        }
        performClose(counted)
    }

    /** Confirma o fechamento mesmo com quebra de caixa (sobra/falta). */
    fun confirmCloseAnyway() {
        val pending = _state.value.pendingCloseConfirm ?: return
        _state.update { it.copy(pendingCloseConfirm = null) }
        performClose(pending.counted)
    }

    /** Cancela o aviso de quebra e volta para o modal de fechamento. */
    fun dismissCloseConfirm() {
        _state.update { it.copy(pendingCloseConfirm = null) }
    }

    private fun performClose(counted: Double) {
        // Snapshot do turno enquanto ainda está aberto: a resposta de fechamento
        // da API nem sempre traz session/totals, então usamos o que já temos na tela.
        val preSession = _state.value.session
        val preTotals = _state.value.totals
        val preMovements = _state.value.movements
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                cashierRepository.close(counted, _state.value.notesInput)
            }
                .onSuccess { status ->
                    val payload = buildClosePayload(status, preSession, preTotals, preMovements, counted)
                    _state.update {
                        it.copy(
                            loading = false,
                            open = false,
                            session = status.session ?: preSession,
                            totals = status.totals ?: preTotals,
                            movements = status.movements.ifEmpty { preMovements },
                            closeSummary = status.summary,
                            lastClosePayload = payload,
                            dialog = CashierDialog.NONE,
                        )
                    }
                    val printed = printPayload(payload)
                    _state.update {
                        it.copy(
                            successModal = CashierSuccessUi(
                                title = "Caixa fechado",
                                detail = if (printed) {
                                    "O relatório do turno foi enviado para a impressora."
                                } else {
                                    "Turno encerrado. Use \"Reimprimir último fechamento\" para sair o relatório."
                                },
                            ),
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(loading = false, error = e.message) }
                }
        }
    }

    fun printCurrentSummary() {
        val printed = attemptPrintSummary()
        _state.update {
            if (printed) {
                it.copy(message = "Relatório enviado à impressora", error = null)
            } else {
                it.copy(error = "Nenhum turno para imprimir")
            }
        }
    }

    /**
     * Imprime o resumo do caixa usando a melhor fonte disponível:
     * primeiro o comprovante guardado no último fechamento ([CashierUiState.lastClosePayload]),
     * depois o [CashierUiState.closeSummary] e por fim a sessão/totais atuais.
     * Retorna `false` quando não há dados para imprimir.
     */
    private fun attemptPrintSummary(): Boolean {
        val s = _state.value
        s.lastClosePayload?.let { return printPayload(it) }

        val summary = s.closeSummary
        val session = summary?.session ?: s.session
        val totals = summary?.totals ?: s.totals
        if (session == null || totals == null) {
            Log.w(TAG, "Resumo de caixa não impresso: session=${session != null}, totals=${totals != null}")
            return false
        }
        val movements = summary?.movements?.takeIf { it.isNotEmpty() } ?: s.movements
        return printPayload(
            buildPrintPayload(
                session = session,
                totals = totals,
                movements = movements,
                difference = summary?.difference ?: session.difference,
                countedBalance = summary?.countedBalance ?: session.countedBalance,
            ),
        )
    }

    /** Envia um comprovante de caixa para a impressora. Retorna `false` se nulo ou se falhar. */
    private fun printPayload(payload: CashierPrintPayload?): Boolean {
        if (payload == null) {
            Log.w(TAG, "Resumo de caixa não impresso: sem dados do turno")
            return false
        }
        return runCatching {
            printer.printCashierSummary(payload)
            Log.i(TAG, "Resumo de caixa enviado para impressão (${payload.saleCount} vendas)")
        }.onFailure { e ->
            Log.e(TAG, "Falha ao imprimir resumo de caixa", e)
        }.isSuccess
    }

    /**
     * Monta o comprovante de fechamento priorizando os dados que já temos:
     * o snapshot do turno aberto (preTotals/preSession) + o valor contado informado.
     * A resposta da API de fechamento é usada quando traz os campos.
     */
    private fun buildClosePayload(
        status: CashierStatusDto,
        preSession: CashierSessionDto?,
        preTotals: CashierTotalsDto?,
        preMovements: List<CashierMovementDto>,
        counted: Double,
    ): CashierPrintPayload {
        val summary = status.summary
        val session = summary?.session ?: status.session ?: preSession
        // Sem vendas, os totais podem vir nulos. Em vez de não imprimir, sintetizamos um
        // resumo a partir do troco inicial (esperado = abertura) para imprimir SEMPRE.
        val totals = summary?.totals ?: status.totals ?: preTotals ?: run {
            val opening = preSession?.openingBalance ?: session?.openingBalance ?: 0.0
            CashierTotalsDto(openingBalance = opening, expectedDrawer = opening)
        }
        val movements = summary?.movements?.takeIf { it.isNotEmpty() }
            ?: status.movements.takeIf { it.isNotEmpty() }
            ?: preMovements
        val difference = summary?.difference
            ?: status.session?.difference
            ?: (counted - totals.expectedDrawer)
        val closedLabel = session?.closedAt?.let { formatInstantLabel(it) }
            ?: formatInstantLabel(Instant.now().toString())
        return CashierPrintPayload(
            deviceName = configStore.getDeviceName(),
            producerName = configStore.getEstablishmentName(),
            operatorName = configStore.getOperatorName().ifBlank { session?.operatorName ?: "" },
            openedAtLabel = formatInstantLabel(session?.openedAt),
            closedAtLabel = closedLabel,
            openingBalance = totals.openingBalance,
            cashSales = totals.cashSales,
            withdrawals = totals.withdrawals,
            expenses = totals.expenses,
            expectedDrawer = totals.expectedDrawer,
            countedBalance = summary?.countedBalance ?: counted,
            difference = difference,
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

    private fun buildPrintPayload(
        session: CashierSessionDto,
        totals: CashierTotalsDto,
        movements: List<CashierMovementDto>,
        difference: Double?,
        countedBalance: Double? = session.countedBalance,
    ): CashierPrintPayload {
        return CashierPrintPayload(
            deviceName = configStore.getDeviceName(),
            producerName = configStore.getEstablishmentName(),
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
        val instant = parseInstant(iso) ?: return iso
        return DATE_LABEL_FORMAT.withZone(zone).format(instant)
    }

    /**
     * Aceita os vários formatos que o backend pode mandar (ISO com `Z`, com offset `+00:00`,
     * Postgres `2026-06-22 03:00:00+00`, ou sem fuso) e devolve sempre um [Instant] em UTC.
     */
    private fun parseInstant(raw: String): Instant? {
        // Normaliza: espaço -> 'T' e offset curto "+00"/"-03" -> "+00:00"/"-03:00".
        val s = raw.trim()
            .replace(' ', 'T')
            .replace(Regex("([+-]\\d{2})$"), "$1:00")
        runCatching { return Instant.parse(s) }
        runCatching { return OffsetDateTime.parse(s).toInstant() }
        // Sem fuso na string: o backend grava em UTC, então assumimos UTC.
        runCatching { return LocalDateTime.parse(s).toInstant(ZoneOffset.UTC) }
        return null
    }

    private companion object {
        const val TAG = "Gate8Cashier"
        val DATE_LABEL_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
    }
}
