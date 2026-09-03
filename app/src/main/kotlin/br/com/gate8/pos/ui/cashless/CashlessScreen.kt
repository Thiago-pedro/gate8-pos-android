package br.com.gate8.pos.ui.cashless

import androidx.compose.foundation.background
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
import br.com.gate8.pos.ui.common.Gate8MenuButton
import br.com.gate8.pos.ui.common.Gate8OutlinedTextField
import br.com.gate8.pos.ui.common.Gate8PaymentMethodsSheet
import br.com.gate8.pos.ui.common.Gate8ScreenBackground
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
                    Gate8MenuButton(
                        title = if (state.waitingCard && state.loading && state.payingMethod == null) {
                            "Aguardando cartão…"
                        } else {
                            "Consultar saldo"
                        },
                        subtitle = "Aproxime o Mifare para ler UID e saldo",
                        onClick = vm::consultBalance,
                        enabled = !state.loading,
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
                            else -> "Adicionar saldo"
                        },
                        onClick = vm::openTopUpPayment,
                        enabled = !state.loading,
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
                        CardInfoPanel(card)
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
}

@Composable
private fun CardInfoPanel(card: CashlessCardSnapshot) {
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
        if (card.isGate8Format && card.balanceReais != null) {
            InfoRow("Saldo", "R$ ${"%.2f".format(card.balanceReais)}", highlight = true)
            InfoRow("Formato", "Gate8 cashless")
        } else {
            InfoRow("Saldo", "—")
            InfoRow("Formato", "Não Gate8 / vazio")
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
        Text(
            "ASCII: ${card.blockAscii}",
            color = Gate8Colors.TextOnLight.copy(alpha = 0.65f),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 6.dp),
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
