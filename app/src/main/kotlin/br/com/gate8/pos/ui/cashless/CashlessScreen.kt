package br.com.gate8.pos.ui.cashless

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import br.com.gate8.pos.R
import br.com.gate8.pos.cashless.CashlessCardSnapshot
import br.com.gate8.pos.domain.model.PaymentMethodApi
import br.com.gate8.pos.ui.common.Gate8AlertDialog
import br.com.gate8.pos.ui.common.Gate8BackTopBar
import br.com.gate8.pos.ui.common.Gate8ConfirmModal
import br.com.gate8.pos.ui.common.Gate8MenuButton
import br.com.gate8.pos.ui.common.CpfVisualTransformation
import br.com.gate8.pos.ui.common.Gate8OutlinedTextField
import br.com.gate8.pos.ui.common.Gate8PaymentMethodsSheet
import br.com.gate8.pos.ui.common.PhoneVisualTransformation
import br.com.gate8.pos.ui.common.Gate8ScreenBackground
import br.com.gate8.pos.ui.common.Gate8ScreenBackgroundFillWidth
import br.com.gate8.pos.ui.common.Gate8SuccessDialog
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
        state.showConsultResult ||
        state.showCardOptions ||
        state.pendingChipCredit != null ||
        state.recoverStep != CashlessRecoverStep.Idle

    val waitModal = cashlessWaitModalUi(state)

    LaunchedEffect(Unit) {
        vm.onScreenVisible()
    }

    PaymentWaitingOverlay(
        visible = state.loading && state.payingMethod != null && !state.waitingCard,
        method = state.payingMethod,
        amount = state.pendingAmount,
        onCancel = { vm.cancelPayment() },
    )

    CashlessWaitingCardModal(
        ui = waitModal,
        onCancel = {
            if (state.recoverStep != CashlessRecoverStep.Idle) {
                vm.cancelRecoverWizard()
            }
        },
        onRetry = vm::retryZeroOldCard,
    )

    val feedbackBlockedByOtherUi = waitModal != null ||
        state.showConfirmBlock ||
        state.showAskRecover ||
        state.showConfirmZero ||
        state.showConsultResult ||
        state.showCardOptions ||
        state.paymentFailed ||
        state.paymentCancelled ||
        state.pixExpired

    if (!feedbackBlockedByOtherUi && !state.error.isNullOrBlank()) {
        Gate8AlertDialog(
            title = "Atenção",
            reason = state.error,
            accent = Gate8Colors.Error,
            onDismiss = { vm.dismissFeedback() },
            onDark = true,
        )
    } else if (
        !feedbackBlockedByOtherUi &&
        !state.showPaymentSheet &&
        !state.showRegisterSheet &&
        !state.showLostCpfSheet &&
        !state.message.isNullOrBlank()
    ) {
        Gate8SuccessDialog(
            title = cashlessFeedbackTitle(state.message),
            detail = state.message,
            buttonLabel = "OK",
            onDismiss = { vm.dismissFeedback() },
            onDark = true,
        )
    }

    val consultBalance = state.consultBalanceReais
    val consultCard = state.card
    if (state.showConsultResult && consultBalance != null && consultCard != null &&
        (state.consultResultTitle == "Consulta de saldo" || state.consultResultTitle == "Cartão bloqueado")
    ) {
        CashlessBalanceConsultDialog(
            title = state.consultResultTitle ?: "Consulta de saldo",
            balanceReais = consultBalance,
            uidHex = consultCard.uidHex,
            status = state.consultStatus ?: "Pronto para usar",
            name = state.accountName,
            cpf = state.accountCpf,
            phone = state.accountPhone,
            note = state.consultNote,
            success = !state.accountBlocked && state.consultStatus == "Pronto para usar",
            onDismiss = { vm.dismissConsultResult() },
        )
    } else if (state.showConsultResult) {
        Gate8SuccessDialog(
            title = state.consultResultTitle ?: "Consulta",
            detail = state.consultResultDetail,
            buttonLabel = "OK",
            onDismiss = { vm.dismissConsultResult() },
            onDark = true,
        )
    }

    if (state.pixExpired) {
        Gate8AlertDialog(
            title = "QR Code expirado",
            reason = "O tempo para pagar o Pix acabou.",
            detail = "Gere um novo QR Code para tentar novamente.",
            onDismiss = { vm.dismissPixExpired() },
            onDark = true,
        )
    }

    if (state.paymentCancelled) {
        Gate8AlertDialog(
            title = "Pagamento não concluído",
            reason = "A cobrança não foi finalizada na maquininha.",
            detail = "O valor continua na tela para tentar de novo.",
            icon = Icons.Filled.Cancel,
            accent = Gate8Colors.AccentBlue,
            onDismiss = { vm.dismissPaymentCancelled() },
            onDark = true,
        )
    }

    if (state.paymentFailed) {
        PaymentFailedAlert(
            reason = state.paymentFailedReason,
            onDismiss = { vm.dismissPaymentFailed() },
            onDark = true,
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
            onDark = true,
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
            onDark = true,
        )
    }

    if (state.showConfirmZero) {
        Gate8ConfirmModal(
            title = "Zerar e encerrar cartão?",
            message = buildString {
                append("UID ${state.pendingUid}\n\n")
                state.accountName?.takeIf { it.isNotBlank() }?.let { append("Nome: $it\n") }
                state.accountCpf?.takeIf { it.isNotBlank() }?.let { append("CPF: $it\n") }
                append("Saldo no chip: R$ ${"%.2f".format(state.recoverBalance)}\n\n")
                append("Isso apaga o saldo do chip, remove os dados do cliente ")
                append("e encerra o cartão no sistema (não fica mais ativo).\n")
                append("Não dá para desfazer.")
            },
            confirmLabel = "Sim, zerar e encerrar",
            dismissLabel = "Cancelar",
            onConfirm = { vm.confirmZeroBalance() },
            onDismiss = { vm.dismissConfirmZero() },
            onDark = true,
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
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                Text(
                    "Cartão cashless · consulta e recarga",
                    color = Color(0xFFD6E4FF),
                    fontSize = 13.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    textAlign = TextAlign.Center,
                )

                state.card?.let { card ->
                    val balance = card.balanceReais ?: 0.0
                    val who = state.accountName?.takeIf { it.isNotBlank() }
                    val status = when {
                        card.isBlocked || state.accountBlocked -> "Bloqueado"
                        who != null -> who
                        else -> "Cartão lido"
                    }
                    Text(
                        "Saldo R$ ${"%.2f".format(balance)} · $status",
                        color = Color.White,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                    )
                }

                Spacer(Modifier.height(12.dp))

                Gate8OutlinedTextField(
                    value = state.amountInput,
                    onValueChange = vm::onAmountChange,
                    label = "Valor a creditar (R$)",
                    placeholder = "10,00",
                    prefix = "R$ ",
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    onDark = true,
                )

                state.pendingChipCredit?.let { pending ->
                    Spacer(Modifier.height(10.dp))
                    Gate8MenuButton(
                        title = "Creditar cartão",
                        subtitle = "Pagamento OK · aproxime ${pending.requireUid} · " +
                            "R$ ${"%.2f".format(pending.amount)}",
                        onClick = vm::retryChipCredit,
                        enabled = !state.loading,
                        dimWhenDisabled = false,
                        centerText = true,
                    )
                }

                Spacer(Modifier.height(12.dp))

                CashlessActionGrid(
                    enabled = !busy,
                    onTopUp = vm::startTopUp,
                    onConsult = vm::consultBalance,
                    onPrint = vm::printStatement,
                    onOptions = vm::openCardOptions,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (state.showCardOptions) {
        CashlessCardOptionsModal(
            onLostOrStolen = vm::chooseLostOrStolen,
            onBlock = vm::chooseBlockCard,
            onUnblock = vm::chooseUnblockCard,
            onRecover = vm::chooseRecoverBalance,
            onZeroBalance = vm::chooseZeroBalance,
            onDismiss = vm::dismissCardOptions,
        )
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
                onDark = true,
            )
        }
    }

    if (state.showRegisterSheet) {
        CashlessRegisterCardModal(
            uidHex = state.pendingUid,
            name = state.registerNameInput,
            cpf = state.registerCpfInput,
            phone = state.registerPhoneInput,
            onNameChange = vm::onRegisterNameChange,
            onCpfChange = vm::onRegisterCpfChange,
            onPhoneChange = vm::onRegisterPhoneChange,
            onSubmit = vm::submitRegister,
            onDismiss = vm::dismissRegisterSheet,
        )
    }

    if (state.showLostCpfSheet) {
        ModalBottomSheet(
            onDismissRequest = { vm.dismissLostCpfSheet() },
            containerColor = Color.Transparent,
        ) {
            Gate8ScreenBackgroundFillWidth {
                Column(
                    cashlessModalModifier(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                            .padding(bottom = 32.dp),
                    ),
                ) {
                    Text(
                        "Cartão perdido",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Informe o CPF cadastrado na recarga. Vamos bloquear e oferecer a transferência.",
                        color = Color(0xFFD6E4FF),
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
                        onDark = true,
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
private fun CashlessRegisterCardModal(
    uidHex: String?,
    name: String,
    cpf: String,
    phone: String,
    onNameChange: (String) -> Unit,
    onCpfChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.92f).dp
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                cashlessModalModifier(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .heightIn(max = maxHeight),
                )
                    .clickable(enabled = false, onClick = {})
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Cadastro do cartão",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    "UID ${uidHex ?: "—"}",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0x33081428))
                        .border(1.dp, Color(0xFF0000FE).copy(alpha = 0.85f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
                Spacer(Modifier.height(20.dp))
                Gate8OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = "Nome (opcional)",
                    placeholder = "Nome do cliente",
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    onDark = true,
                )
                Spacer(Modifier.height(12.dp))
                Gate8OutlinedTextField(
                    value = cpf,
                    onValueChange = onCpfChange,
                    label = "CPF",
                    placeholder = "000.000.000-00",
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = CpfVisualTransformation,
                    onDark = true,
                )
                Spacer(Modifier.height(12.dp))
                Gate8OutlinedTextField(
                    value = phone,
                    onValueChange = onPhoneChange,
                    label = "Celular (opcional)",
                    placeholder = "(00) 00000-0000",
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    visualTransformation = PhoneVisualTransformation,
                    onDark = true,
                )
                Spacer(Modifier.height(20.dp))
                Gate8MenuButton(
                    title = "Salvar e continuar",
                    subtitle = "Depois você escolhe como pagar a recarga",
                    onClick = onSubmit,
                    centerText = true,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Cancelar",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun CashlessCardOptionsModal(
    onLostOrStolen: () -> Unit,
    onBlock: () -> Unit,
    onUnblock: () -> Unit,
    onRecover: () -> Unit,
    onZeroBalance: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val maxHeight = (LocalConfiguration.current.screenHeightDp * 0.88f).dp
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                cashlessModalModifier(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .heightIn(max = maxHeight),
                )
                    .clickable(enabled = false, onClick = {})
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Opções do cartão",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Escolha o que deseja fazer",
                    color = Color(0xFFD6E4FF),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(18.dp))
                Gate8MenuButton(
                    title = "Perda / roubo",
                    subtitle = "Bloqueia pelo CPF e pode transferir o saldo",
                    onClick = onLostOrStolen,
                    centerText = true,
                )
                Spacer(Modifier.height(10.dp))
                Gate8MenuButton(
                    title = "Bloquear",
                    subtitle = "Com o cartão na mão · opcional transferir depois",
                    onClick = onBlock,
                    centerText = true,
                )
                Spacer(Modifier.height(10.dp))
                Gate8MenuButton(
                    title = "Desbloquear",
                    subtitle = "Libera de novo o cartão aproximado",
                    onClick = onUnblock,
                    centerText = true,
                )
                Spacer(Modifier.height(10.dp))
                Gate8MenuButton(
                    title = "Zerar / encerrar cartão",
                    subtitle = "Limpa o chip e encerra o cadastro no sistema",
                    onClick = onZeroBalance,
                    centerText = true,
                )
                Spacer(Modifier.height(10.dp))
                Gate8MenuButton(
                    title = "Recuperar / ativar saldo",
                    subtitle = "Cartão bloqueado na mão · passa o saldo para um novo",
                    onClick = onRecover,
                    centerText = true,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "Cancelar",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 8.dp),
                )
            }
        }
    }
}

private val CashlessModalShape = RoundedCornerShape(22.dp)

private fun cashlessModalModifier(modifier: Modifier): Modifier =
    modifier
        .clip(CashlessModalShape)
        .background(Color(0xFF0C1E4A))
        .border(1.5.dp, Color(0xFF0000FE).copy(alpha = 0.9f), CashlessModalShape)
        .padding(horizontal = 22.dp, vertical = 24.dp)

private data class CashlessWaitModalUi(
    val title: String,
    val detail: String,
    val showProgress: Boolean = true,
    val showCancel: Boolean = false,
    val showRetry: Boolean = false,
    val uidHint: String? = null,
)

private fun cashlessWaitModalUi(state: CashlessUiState): CashlessWaitModalUi? {
    if (state.waitingCard && state.loading) {
        val title = when {
            state.payingMethod != null -> "Pagamento OK"
            state.message?.contains("desbloque", ignoreCase = true) == true -> "Desbloquear cartão"
            state.message?.contains("recuperar", ignoreCase = true) == true -> "Recuperar saldo"
            state.recoverStep == CashlessRecoverStep.ReadingOld -> "Bloquear cartão"
            state.recoverStep == CashlessRecoverStep.WaitingNew -> "Cartão novo"
            state.recoverStep == CashlessRecoverStep.WaitingOldZero -> "Zerar cartão antigo"
            state.message?.contains("extrato", ignoreCase = true) == true -> "Imprimir extrato"
            state.message?.contains("zerar", ignoreCase = true) == true -> "Zerar saldo"
            state.message?.contains("identificar", ignoreCase = true) == true ||
                (state.pendingAmount > 0 && state.payingMethod == null) -> "Identificar cartão"
            else -> "Consultar saldo"
        }
        return CashlessWaitModalUi(
            title = title,
            detail = state.message
                ?: "Aproxime o cartão na maquininha",
            showProgress = true,
            showCancel = state.recoverStep != CashlessRecoverStep.Idle,
            uidHint = state.pendingUid ?: state.recoverOldUid,
        )
    }
    // Falhou ao zerar o antigo: mantém modal com retry (sem deixar botões “piscar”).
    if (state.recoverStep == CashlessRecoverStep.WaitingOldZero && !state.loading) {
        return CashlessWaitModalUi(
            title = "Zerar cartão antigo",
            detail = state.error
                ?: state.message
                ?: "Aproxime o cartão ANTIGO (${state.recoverOldUid ?: "—"}) para zerar.",
            showProgress = false,
            showCancel = true,
            showRetry = true,
            uidHint = state.recoverOldUid,
        )
    }
    return null
}

@Composable
private fun CashlessWaitingCardModal(
    ui: CashlessWaitModalUi?,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
) {
    if (ui == null) return
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                cashlessModalModifier(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp),
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_cashless_nfc),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(108.dp),
                    contentScale = ContentScale.Fit,
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    ui.title,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    ui.detail,
                    color = Color(0xFFD6E4FF),
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                ui.uidHint?.takeIf { it.isNotBlank() }?.let { uid ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "UID $uid",
                        color = Color(0xFF9AD4FF),
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                    )
                }

                if (ui.showProgress) {
                    Spacer(Modifier.height(18.dp))
                    CircularProgressIndicator(
                        color = Color(0xFF3D7CFF),
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(36.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Aguardando cartão…",
                        color = Color(0xFF9AD4FF),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                if (ui.showRetry || ui.showCancel) {
                    Spacer(Modifier.height(22.dp))
                    if (ui.showRetry) {
                        Gate8MenuButton(
                            title = "Tentar de novo",
                            onClick = onRetry,
                            centerText = true,
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    if (ui.showCancel) {
                        Text(
                            "Cancelar",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clickable(onClick = onCancel)
                                .padding(vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

private data class CashlessTile(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val highlighted: Boolean = false,
)

@Composable
private fun CashlessActionGrid(
    enabled: Boolean,
    onTopUp: () -> Unit,
    onConsult: () -> Unit,
    onPrint: () -> Unit,
    onOptions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tiles = listOf(
        CashlessTile("Adicionar saldo", Icons.Outlined.CreditCard, onTopUp, highlighted = true),
        CashlessTile("Consultar saldo", Icons.Outlined.AccountBalanceWallet, onConsult),
        CashlessTile("Imprimir extrato", Icons.Outlined.Print, onPrint),
        CashlessTile("Opções do cartão", Icons.Outlined.Settings, onOptions),
    )
    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        tiles.chunked(2).forEach { row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.forEach { tile ->
                    CashlessTileButton(tile, enabled, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CashlessTileButton(
    tile: CashlessTile,
    enabled: Boolean,
    modifier: Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    val highlighted = tile.highlighted && enabled
    val background = when {
        highlighted -> Gate8Colors.AccentBlue
        enabled -> Color(0x33081428)
        else -> Color(0x22081428)
    }
    val content = if (enabled) Color.White else Color.White.copy(alpha = 0.4f)
    Column(
        modifier
            .fillMaxSize()
            .clip(shape)
            .background(background)
            .then(
                if (highlighted) {
                    Modifier
                } else {
                    Modifier.border(
                        1.5.dp,
                        Color(0xFF0000FE).copy(alpha = if (enabled) 0.9f else 0.35f),
                        shape,
                    )
                },
            )
            .clickable(enabled = enabled, onClick = tile.onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            tile.icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(32.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            tile.label,
            color = content,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp,
        )
    }
}

@Composable
private fun CardInfoCard(
    card: CashlessCardSnapshot,
    name: String?,
    cpf: String?,
    phone: String?,
    accountBlocked: Boolean = false,
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
        name?.takeIf { it.isNotBlank() }?.let { InfoRow("Nome", it) }
        cpf?.let { InfoRow("CPF", maskCpfDisplay(it)) }
        phone?.let { InfoRow("Telefone", it) }
        val balance = card.balanceReais
        when {
            !card.balanceConfirmed || balance == null -> {
                InfoRow("Saldo", "—", highlight = true)
                InfoRow("Status", "Leitura não confirmada")
            }
            card.isBlocked || accountBlocked -> {
                InfoRow("Saldo", "R$ ${"%.2f".format(balance)}", highlight = true)
                InfoRow("Status", "Bloqueado · não liberado para uso")
            }
            !card.isGate8Format || balance <= 0.009 -> {
                InfoRow("Saldo", "R$ ${"%.2f".format(balance)}", highlight = true)
                InfoRow("Status", "Em branco · zerado")
            }
            else -> {
                InfoRow("Saldo", "R$ ${"%.2f".format(balance)}", highlight = true)
                InfoRow("Status", "Pronto para usar")
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

/** LGPD: início + fim (ex.: 309.***.***-57). */
private fun maskCpfDisplay(cpf: String): String {
    val d = cpf.filter { it.isDigit() }
    if (d.length != 11) return "***"
    return "${d.take(3)}.***.***-${d.takeLast(2)}"
}

private fun cashlessFeedbackTitle(message: String?): String {
    val m = message.orEmpty().lowercase()
    return when {
        m.contains("bloqueado") -> "Cartão bloqueado"
        m.contains("zerado") -> "Saldo zerado"
        m.contains("recarga") || m.contains("crédito") || m.contains("creditado") -> "Recarga concluída"
        m.contains("pronto") || m.contains("transfer") -> "Transferência concluída"
        m.contains("extrato") -> "Extrato"
        else -> "Cashless"
    }
}
