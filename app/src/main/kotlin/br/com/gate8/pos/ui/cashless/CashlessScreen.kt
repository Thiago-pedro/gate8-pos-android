package br.com.gate8.pos.ui.cashless

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.gate8.pos.cashless.CashlessCardSnapshot
import br.com.gate8.pos.domain.model.PaymentMethodApi
import br.com.gate8.pos.ui.common.Gate8AlertDialog
import br.com.gate8.pos.ui.common.Gate8BackTopBar
import br.com.gate8.pos.ui.common.Gate8ConfirmModal
import br.com.gate8.pos.ui.common.Gate8MenuButton
import br.com.gate8.pos.ui.common.Gate8OutlinedTextField
import br.com.gate8.pos.ui.common.Gate8PaymentMethodsSheet
import br.com.gate8.pos.ui.common.Gate8ScreenBackground
import br.com.gate8.pos.ui.common.Gate8ScreenBackgroundFillWidth
import br.com.gate8.pos.ui.common.PaymentFailedAlert
import br.com.gate8.pos.ui.common.PaymentWaitingOverlay
import br.com.gate8.pos.ui.theme.Gate8Colors
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashlessScreen(
    onBack: () -> Unit,
    vm: CashlessViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val busy = state.loading || state.showConfirmBlock || state.showAskRecover ||
        state.showConfirmZero || state.showRegisterSheet || state.showLostCpfSheet ||
        state.recoverStep != CashlessRecoverStep.Idle

    LaunchedEffect(Unit) {
        vm.onScreenVisible()
    }

    PaymentWaitingOverlay(
        visible = state.loading && state.payingMethod != null && !state.waitingCard,
        method = state.payingMethod,
        amount = state.pendingAmount,
        onCancel = { vm.cancelPayment() },
    )

    if (state.pixExpired) {
        Gate8AlertDialog(
            title = "QR Code expirado",
            reason = "O tempo para pagar o Pix acabou.",
            detail = "Gere um novo QR Code para tentar novamente.",
            onDismiss = { vm.dismissPixExpired() },
        )
    }

    if (state.paymentCancelled) {
        Gate8AlertDialog(
            title = "Pagamento cancelado",
            reason = "A cobrança foi cancelada.",
            detail = "O valor continua na tela para tentar de novo.",
            icon = Icons.Filled.Cancel,
            accent = Gate8Colors.AccentBlue,
            onDismiss = { vm.dismissPaymentCancelled() },
        )
    }

    if (state.paymentFailed) {
        PaymentFailedAlert(
            reason = state.paymentFailedReason,
            onDismiss = { vm.dismissPaymentFailed() },
        )
    }

    if (state.showConfirmBlock) {
        Gate8ConfirmModal(
            title = "Bloquear cartão?",
            message = "UID ${state.recoverOldUid}\n\n" +
                "Saldo R$ ${"%.2f".format(state.recoverBalance)}\n\n" +
                "O cartão deixa de valer para gastar até transferir o saldo.",
            confirmLabel = "Sim, bloquear",
            dismissLabel = "Cancelar",
            onConfirm = { vm.confirmBlockCard() },
            onDismiss = { vm.dismissConfirmBlock() },
        )
    }

    if (state.showAskRecover) {
        Gate8ConfirmModal(
            title = "Recuperar saldo?",
            message = buildString {
                append("Saldo R$ ${"%.2f".format(state.recoverBalance)}")
                state.recoverCpf?.let { append("\nCPF ${formatCpfDisplay(it)}") }
                append("\n\nDeseja passar esse valor para um cartão novo agora?")
            },
            confirmLabel = "Sim, transferir",
            dismissLabel = "Agora não",
            onConfirm = { vm.acceptRecover() },
            onDismiss = { vm.declineRecover() },
        )
    }

    if (state.showConfirmZero) {
        Gate8ConfirmModal(
            title = "Zerar saldo?",
            message = "UID ${state.pendingUid}\n\n" +
                "Saldo atual R$ ${"%.2f".format(state.recoverBalance)}\n\n" +
                "Isso apaga o crédito do cartão. Não dá para desfazer.",
            confirmLabel = "Sim, zerar",
            dismissLabel = "Cancelar",
            onConfirm = { vm.confirmZeroBalance() },
            onDismiss = { vm.dismissConfirmZero() },
        )
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
                    "Cashless",
                    color = Gate8Colors.TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                Text(
                    "Cartão Mifare · consulta e recarga",
                    color = Gate8Colors.TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(16.dp))

                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .fillMaxWidth(),
                ) {
                    if (state.recoverStep != CashlessRecoverStep.Idle) {
                        RecoverStepBanner(
                            step = state.recoverStep,
                            balance = state.recoverBalance,
                            oldUid = state.recoverOldUid,
                            lostMode = state.lostCardMode,
                            onCancel = { vm.cancelRecoverWizard() },
                            onRetryZero = { vm.retryZeroOldCard() },
                            showRetryZero = state.recoverStep == CashlessRecoverStep.WaitingOldZero &&
                                !state.loading &&
                                state.error != null,
                        )
                        Spacer(Modifier.height(14.dp))
                    }

                    Gate8MenuButton(
                        title = if (state.waitingCard && state.loading && state.payingMethod == null &&
                            state.recoverStep == CashlessRecoverStep.Idle &&
                            state.pendingUid == null
                        ) {
                            "Aguardando cartão…"
                        } else {
                            "Consultar saldo"
                        },
                        subtitle = "Aproxime o Mifare para ler UID e saldo",
                        onClick = vm::consultBalance,
                        enabled = !busy,
                        centerText = true,
                    )

                    Spacer(Modifier.height(16.dp))

                    Gate8OutlinedTextField(
                        value = state.amountInput,
                        onValueChange = vm::onAmountChange,
                        label = "Valor a creditar (R$)",
                        placeholder = "10,00",
                        prefix = "R$ ",
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    Spacer(Modifier.height(12.dp))
                    Gate8MenuButton(
                        title = when {
                            state.loading && state.payingMethod != null && !state.waitingCard ->
                                "Processando pagamento…"
                            state.waitingCard && state.payingMethod != null ->
                                "Aguardando cartão…"
                            state.waitingCard && state.pendingAmount > 0 && state.payingMethod == null ->
                                "Identificando cartão…"
                            else -> "Adicionar saldo"
                        },
                        subtitle = "Identifica o cartão · cadastra se for novo · depois cobra",
                        onClick = vm::startTopUp,
                        enabled = !busy,
                        centerText = true,
                    )

                    Spacer(Modifier.height(12.dp))
                    Gate8MenuButton(
                        title = "Zerar saldo",
                        subtitle = "Apaga o crédito do cartão aproximado",
                        onClick = vm::startZeroBalance,
                        enabled = !busy,
                        centerText = true,
                    )

                    Spacer(Modifier.height(12.dp))
                    Gate8MenuButton(
                        title = when (state.recoverStep) {
                            CashlessRecoverStep.ReadingOld -> "Aguardando cartão…"
                            CashlessRecoverStep.WaitingNew -> "Aguardando cartão novo…"
                            CashlessRecoverStep.WaitingOldZero -> "Aguardando cartão antigo…"
                            CashlessRecoverStep.Idle -> "Bloquear cartão"
                        },
                        subtitle = "Com o cartão na mão · opcional transferir depois",
                        onClick = vm::startBlockCard,
                        enabled = !busy,
                        centerText = true,
                    )

                    Spacer(Modifier.height(12.dp))
                    Gate8MenuButton(
                        title = "Cartão perdido",
                        subtitle = "Bloqueia pelo CPF e transfere o saldo para um novo",
                        onClick = vm::openLostCard,
                        enabled = !busy,
                        centerText = true,
                    )

                    state.message?.let {
                        Spacer(Modifier.height(14.dp))
                        Text(
                            it,
                            color = Color(0xFF1B7A3D),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    state.error?.let {
                        Spacer(Modifier.height(14.dp))
                        Text(
                            it,
                            color = Color(0xFFB3261E),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    state.card?.let { card ->
                        Spacer(Modifier.height(18.dp))
                        CardInfoPanel(
                            card = card,
                            cpf = state.accountCpf,
                            phone = state.accountPhone,
                        )
                    }

                    if (state.loading) {
                        Spacer(Modifier.height(20.dp))
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Gate8Colors.AccentBlue)
                        }
                    }

                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    if (state.showPaymentSheet) {
        ModalBottomSheet(
            onDismissRequest = { vm.dismissPaymentSheet() },
            sheetState = sheetState,
            containerColor = Color.Transparent,
        ) {
            Gate8PaymentMethodsSheet(
                title = "Recarga cashless",
                amountLabel = "Total R$ ${"%.2f".format(state.pendingAmount)}",
                onPayDebit = { vm.checkout(PaymentMethodApi.DEBIT) },
                onPayCredit = { vm.checkout(PaymentMethodApi.CREDIT) },
                onPayPix = { vm.checkout(PaymentMethodApi.PIX) },
                onPayCash = { vm.checkout(PaymentMethodApi.CASH) },
                cashEnabled = state.cashierOpen,
            )
        }
    }

    if (state.showRegisterSheet) {
        ModalBottomSheet(
            onDismissRequest = { vm.dismissRegisterSheet() },
            containerColor = Color.Transparent,
        ) {
            Gate8ScreenBackgroundFillWidth {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .padding(bottom = 32.dp),
                ) {
                    Text(
                        "Cadastro do cartão",
                        color = Gate8Colors.TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "UID ${state.pendingUid ?: "—"}",
                        color = Gate8Colors.TextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
                    )
                    Gate8OutlinedTextField(
                        value = state.registerCpfInput,
                        onValueChange = vm::onRegisterCpfChange,
                        label = "CPF",
                        placeholder = "00000000000",
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    Spacer(Modifier.height(12.dp))
                    Gate8OutlinedTextField(
                        value = state.registerPhoneInput,
                        onValueChange = vm::onRegisterPhoneChange,
                        label = "Telefone / WhatsApp",
                        placeholder = "11999999999",
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    )
                    Spacer(Modifier.height(16.dp))
                    Gate8MenuButton(
                        title = "Salvar e continuar",
                        subtitle = "Depois você escolhe como pagar a recarga",
                        onClick = vm::submitRegister,
                        centerText = true,
                    )
                }
            }
        }
    }

    if (state.showLostCpfSheet) {
        ModalBottomSheet(
            onDismissRequest = { vm.dismissLostCpfSheet() },
            containerColor = Color.Transparent,
        ) {
            Gate8ScreenBackgroundFillWidth {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp)
                        .padding(bottom = 32.dp),
                ) {
                    Text(
                        "Cartão perdido",
                        color = Gate8Colors.TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Informe o CPF cadastrado na recarga. Vamos bloquear e oferecer a transferência.",
                        color = Gate8Colors.TextSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
                    )
                    Gate8OutlinedTextField(
                        value = state.lostCpfInput,
                        onValueChange = vm::onLostCpfChange,
                        label = "CPF",
                        placeholder = "00000000000",
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    Spacer(Modifier.height(16.dp))
                    Gate8MenuButton(
                        title = "Buscar e bloquear",
                        onClick = vm::searchLostByCpf,
                        centerText = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecoverStepBanner(
    step: CashlessRecoverStep,
    balance: Double,
    oldUid: String?,
    lostMode: Boolean,
    onCancel: () -> Unit,
    onRetryZero: () -> Unit,
    showRetryZero: Boolean,
) {
    val title = when (step) {
        CashlessRecoverStep.ReadingOld -> "1 · Cartão a bloquear"
        CashlessRecoverStep.WaitingNew -> if (lostMode) "Cartão novo" else "2 · Cartão novo"
        CashlessRecoverStep.WaitingOldZero -> "3 · Zerar o antigo"
        CashlessRecoverStep.Idle -> ""
    }
    val body = when (step) {
        CashlessRecoverStep.ReadingOld -> "Aproxime o cartão do cliente."
        CashlessRecoverStep.WaitingNew ->
            "Saldo R$ ${"%.2f".format(balance)}.\nAproxime o cartão NOVO."
        CashlessRecoverStep.WaitingOldZero ->
            "Aproxime o cartão ANTIGO (${oldUid ?: "—"}) para zerar."
        CashlessRecoverStep.Idle -> ""
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Gate8Colors.AccentBlue.copy(alpha = 0.12f))
            .padding(14.dp),
    ) {
        Text(title, color = Gate8Colors.AccentBlue, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(Modifier.height(6.dp))
        Text(body, color = Gate8Colors.TextPrimary, fontSize = 13.sp, lineHeight = 18.sp)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            if (showRetryZero) {
                Text(
                    "Tentar zerar de novo",
                    color = Gate8Colors.AccentBlue,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onRetryZero)
                        .padding(vertical = 4.dp),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            Text(
                "Cancelar",
                color = Gate8Colors.TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier
                    .clickable(onClick = onCancel)
                    .padding(vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun CardInfoPanel(
    card: CashlessCardSnapshot,
    cpf: String?,
    phone: String?,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Gate8Colors.CardSurface)
            .padding(16.dp),
    ) {
        Text(
            "Cartão",
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            color = Gate8Colors.TextOnLight,
        )
        Spacer(Modifier.height(10.dp))
        InfoRow("UID", card.uidHex)
        cpf?.let { InfoRow("CPF", formatCpfDisplay(it)) }
        phone?.let { InfoRow("Telefone", it) }
        when {
            card.isBlocked -> {
                InfoRow(
                    "Saldo",
                    "R$ ${"%.2f".format(card.balanceReais ?: 0.0)}",
                    highlight = true,
                )
                InfoRow("Status", "Bloqueado")
            }
            card.isGate8Format && card.balanceReais != null -> {
                InfoRow("Saldo", "R$ ${"%.2f".format(card.balanceReais)}", highlight = true)
                InfoRow("Status", "Pronto para usar")
            }
            else -> {
                InfoRow("Saldo", "R$ 0,00", highlight = true)
                InfoRow("Status", "Em branco · zerado")
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Bloco (hex)",
            color = Gate8Colors.TextOnLight.copy(alpha = 0.7f),
            fontSize = 12.sp,
        )
        Text(
            card.blockHex,
            color = Gate8Colors.TextOnLight,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String, highlight: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = Gate8Colors.TextOnLight.copy(alpha = 0.7f),
            fontSize = 13.sp,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            color = if (highlight) Gate8Colors.AccentBlue else Gate8Colors.TextOnLight,
            fontSize = 13.sp,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

private fun formatCpfDisplay(cpf: String): String {
    val d = cpf.filter { it.isDigit() }
    if (d.length != 11) return cpf
    return "${d.substring(0, 3)}.${d.substring(3, 6)}.${d.substring(6, 9)}-${d.substring(9)}"
}
