package br.com.gate8.pos.ui.cashier

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import br.com.gate8.pos.data.remote.dto.CashierMovementDto
import br.com.gate8.pos.data.remote.dto.CashierTotalsDto
import br.com.gate8.pos.ui.common.Gate8BackTopBar
import br.com.gate8.pos.ui.common.Gate8ConfirmDialog
import br.com.gate8.pos.ui.common.Gate8MenuButton
import br.com.gate8.pos.ui.common.Gate8OutlinedTextField
import br.com.gate8.pos.ui.common.Gate8ScreenBackground
import br.com.gate8.pos.ui.common.Gate8SuccessDialog
import br.com.gate8.pos.ui.theme.Gate8Colors
import org.koin.androidx.compose.koinViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun CashierScreen(
    onBack: () -> Unit,
    vm: CashierViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.onScreenVisible()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Gate8ScreenBackground {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
            ) {
                Gate8BackTopBar(onBack = onBack)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Caixa",
                    color = Gate8Colors.TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                Text(
                    if (state.open) "Aberto · operador ${state.operatorLabel.ifBlank { "—" }}"
                    else "Fechado — abra o caixa para vender em dinheiro",
                    color = if (state.open) Gate8Colors.TextPrimary else Gate8Colors.TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    textAlign = TextAlign.Center,
                )

                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Spacer(Modifier.height(16.dp))
                    if (state.loading && state.session == null && !state.open) {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Gate8Colors.AccentBlue)
                        }
                    } else if (state.open) {
                        state.totals?.let { totals ->
                            CashierTotalsCard(totals, state.session?.openedAt)
                            Spacer(Modifier.height(12.dp))
                        }
                        if (state.movements.isNotEmpty()) {
                            MovementsCard(state.movements)
                            Spacer(Modifier.height(12.dp))
                        }
                        Gate8MenuButton(
                            title = "Sangria",
                            subtitle = "Retirar dinheiro da gaveta",
                            onClick = { vm.showDialog(CashierDialog.WITHDRAWAL) },
                            centerText = true,
                        )
                        Spacer(Modifier.height(8.dp))
                        Gate8MenuButton(
                            title = "Despesa",
                            subtitle = "Pagar conta com dinheiro do caixa",
                            onClick = { vm.showDialog(CashierDialog.EXPENSE) },
                            centerText = true,
                        )
                        Spacer(Modifier.height(8.dp))
                        Gate8MenuButton(
                            title = "Fechar caixa",
                            subtitle = "Contagem física e encerrar turno",
                            onClick = { vm.showDialog(CashierDialog.CLOSE) },
                            centerText = true,
                        )
                        Spacer(Modifier.height(8.dp))
                        Gate8MenuButton(
                            title = "Imprimir relatório",
                            subtitle = "Cupom do turno atual",
                            onClick = vm::printCurrentSummary,
                            centerText = true,
                        )
                    } else if (!state.open) {
                        Gate8MenuButton(
                            title = "Abrir caixa",
                            subtitle = "Informe o troco inicial na gaveta",
                            onClick = { vm.showDialog(CashierDialog.OPEN) },
                            centerText = true,
                        )
                        if (state.closeSummary != null || state.lastClosePayload != null) {
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "Último fechamento",
                                color = Gate8Colors.TextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp,
                            )
                            Spacer(Modifier.height(8.dp))
                            Gate8MenuButton(
                                title = "Reimprimir último fechamento",
                                subtitle = "Relatório do turno encerrado",
                                onClick = vm::printCurrentSummary,
                                centerText = true,
                            )
                        }
                    }

                    state.message?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(it, color = Color(0xFF1B7A3D), fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                    state.error?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(it, color = Color(0xFFB3261E), fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }

            when (state.dialog) {
                CashierDialog.OPEN -> CashierAmountDialog(
                    title = "Abrir caixa",
                    message = "Informe o troco inicial (fundo de gaveta).",
                    amountLabel = "Troco inicial (R$)",
                    amount = state.amountInput,
                    showDescription = false,
                    showNotes = true,
                    notes = state.notesInput,
                    confirmLabel = "Abrir caixa",
                    error = state.error,
                    loading = state.loading,
                    onAmountChange = vm::updateAmount,
                    onNotesChange = vm::updateNotes,
                    onConfirm = vm::confirmOpen,
                    onDismiss = vm::dismissDialog,
                )
                CashierDialog.WITHDRAWAL -> CashierAmountDialog(
                    title = "Sangria",
                    message = "Retirada de dinheiro da gaveta.",
                    amountLabel = "Valor (R$)",
                    amount = state.amountInput,
                    description = state.descriptionInput,
                    showDescription = true,
                    descriptionLabel = "Motivo",
                    confirmLabel = "Confirmar sangria",
                    error = state.error,
                    loading = state.loading,
                    onAmountChange = vm::updateAmount,
                    onDescriptionChange = vm::updateDescription,
                    onConfirm = vm::confirmWithdrawal,
                    onDismiss = vm::dismissDialog,
                )
                CashierDialog.EXPENSE -> CashierAmountDialog(
                    title = "Despesa",
                    message = "Pagamento de conta com dinheiro do caixa.",
                    amountLabel = "Valor (R$)",
                    amount = state.amountInput,
                    description = state.descriptionInput,
                    showDescription = true,
                    descriptionLabel = "Descrição",
                    confirmLabel = "Registrar despesa",
                    error = state.error,
                    loading = state.loading,
                    onAmountChange = vm::updateAmount,
                    onDescriptionChange = vm::updateDescription,
                    onConfirm = vm::confirmExpense,
                    onDismiss = vm::dismissDialog,
                )
                CashierDialog.CLOSE -> CashierAmountDialog(
                    title = "Fechar caixa",
                    message = "Conte o dinheiro na gaveta e informe o total.",
                    amountLabel = "Valor contado (R$)",
                    amount = state.amountInput,
                    showDescription = false,
                    showNotes = true,
                    notes = state.notesInput,
                    notesLabel = "Observações (sobra/falta)",
                    confirmLabel = "Fechar caixa",
                    error = state.error,
                    loading = state.loading,
                    onAmountChange = vm::updateAmount,
                    onNotesChange = vm::updateNotes,
                    onConfirm = vm::confirmClose,
                    onDismiss = vm::dismissDialog,
                )
                CashierDialog.NONE -> Unit
            }

            state.successModal?.let { success ->
                Gate8SuccessDialog(
                    title = success.title,
                    detail = success.detail,
                    onDismiss = { vm.dismissSuccessModal() },
                )
            }
        }
    }
}

@Composable
private fun CashierTotalsCard(totals: CashierTotalsDto, openedAt: String?) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Gate8Colors.CardSurface.copy(alpha = 0.9f))
            .padding(16.dp),
    ) {
        openedAt?.let {
            Text("Desde ${formatTime(it)}", color = Gate8Colors.TextSecondary, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
        }
        TotalRow("Troco inicial", totals.openingBalance)
        TotalRow("Vendas em dinheiro", totals.cashSales)
        TotalRow("Sangrias", totals.withdrawals, negative = true)
        TotalRow("Despesas", totals.expenses, negative = true)
        Spacer(Modifier.height(8.dp))
        TotalRow("Esperado na gaveta", totals.expectedDrawer, highlight = true)
        Spacer(Modifier.height(8.dp))
        Text(
            "${totals.saleCount} vendas · total R$ ${"%.2f".format(totals.grandTotal)}",
            color = Gate8Colors.TextSecondary,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun MovementsCard(movements: List<CashierMovementDto>) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Gate8Colors.CardSurface.copy(alpha = 0.85f))
            .padding(16.dp),
    ) {
        Text("Movimentos", color = Gate8Colors.TextPrimary, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        movements.forEach { m ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (m.type == "expense") "Despesa" else "Sangria",
                        color = Gate8Colors.TextPrimary,
                        fontSize = 14.sp,
                    )
                    m.description?.let {
                        Text(it, color = Gate8Colors.TextSecondary, fontSize = 12.sp)
                    }
                }
                Text(
                    "R$ ${"%.2f".format(m.amount)}",
                    color = Gate8Colors.AccentBlue,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun TotalRow(label: String, value: Double, negative: Boolean = false, highlight: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = Gate8Colors.TextSecondary, fontSize = 14.sp)
        Text(
            "${if (negative) "- " else ""}R$ ${"%.2f".format(value)}",
            color = if (highlight) Gate8Colors.TextPrimary else Gate8Colors.TextSecondary,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
            fontSize = if (highlight) 16.sp else 14.sp,
        )
    }
}

@Composable
private fun CashierAmountDialog(
    title: String,
    message: String,
    amountLabel: String,
    amount: String,
    showDescription: Boolean = false,
    description: String = "",
    descriptionLabel: String = "Descrição",
    showNotes: Boolean = false,
    notes: String = "",
    notesLabel: String = "Observações",
    confirmLabel: String,
    error: String?,
    loading: Boolean,
    onAmountChange: (String) -> Unit,
    onDescriptionChange: (String) -> Unit = {},
    onNotesChange: (String) -> Unit = {},
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Gate8ConfirmDialog(
        title = title,
        message = message,
        confirmLabel = if (loading) "Aguarde…" else confirmLabel,
        dismissLabel = "Cancelar",
        onConfirm = { if (!loading) onConfirm() },
        onDismiss = onDismiss,
        content = {
            Column(Modifier.fillMaxWidth()) {
                Gate8OutlinedTextField(
                    value = amount,
                    onValueChange = onAmountChange,
                    label = amountLabel,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                if (showDescription) {
                    Spacer(Modifier.height(10.dp))
                    Gate8OutlinedTextField(
                        value = description,
                        onValueChange = onDescriptionChange,
                        label = descriptionLabel,
                        singleLine = false,
                    )
                }
                if (showNotes) {
                    Spacer(Modifier.height(10.dp))
                    Gate8OutlinedTextField(
                        value = notes,
                        onValueChange = onNotesChange,
                        label = notesLabel,
                        singleLine = false,
                    )
                }
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        it,
                        color = Color(0xFFB3261E),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
    )
}

private fun formatTime(iso: String): String = runCatching {
    DateTimeFormatter.ofPattern("dd/MM HH:mm")
        .withZone(ZoneId.of("America/Sao_Paulo"))
        .format(Instant.parse(iso))
}.getOrDefault(iso)
