package br.com.gate8.pos.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import br.com.gate8.pos.domain.model.PaymentMethodApi
import br.com.gate8.pos.payment.MoneyCents
import br.com.gate8.pos.payment.SplitPaymentUi
import br.com.gate8.pos.payment.SplitPayments
import br.com.gate8.pos.payment.SplitStep
import br.com.gate8.pos.ui.theme.Gate8Colors

@Composable
fun SplitPaymentDialog(
    ui: SplitPaymentUi,
    cashEnabled: Boolean,
    onAdd: () -> Unit,
    onPick: (PaymentMethodApi) -> Unit,
    onAmountChange: (String) -> Unit,
    onConfirmAmount: () -> Unit,
    onBack: () -> Unit,
    onRequestCancel: () -> Unit,
    onConfirmCancel: () -> Unit,
    onDismissCancel: () -> Unit,
    onDismissReport: () -> Unit,
) {
    val session = ui.session
    val paid = session.paidCents()
    val remaining = session.remainingCents()

    if (ui.confirmingCancel) {
        Gate8ConfirmModal(
            title = "Cancelar venda?",
            message = "Recebido: R$ ${MoneyCents.format(paid)}\nRestante: R$ ${MoneyCents.format(remaining)}\n\n" +
                "A venda não será concluída. Cobrança de Pix ou cartão já aprovada será estornada, se a Cielo permitir.",
            confirmLabel = "Cancelar venda",
            dismissLabel = "Continuar pagamento",
            onConfirm = onConfirmCancel,
            onDismiss = onDismissCancel,
            onDark = true,
        )
        return
    }

    if (ui.cancelReport != null) {
        Gate8AlertDialog(
            title = "Pagamento incompleto",
            reason = "Recebido: R$ ${MoneyCents.format(session.paidCents())}",
            detail = ui.cancelReport,
            accent = Gate8Colors.AccentBlue,
            onDismiss = onDismissReport,
            onDark = true,
        )
        return
    }

    Dialog(
        onDismissRequest = onBack,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            val shape = RoundedCornerShape(22.dp)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(Color(0xFF0C1E4A))
                    .border(1.5.dp, Color(0xFF0000FE).copy(alpha = 0.9f), shape)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 22.dp),
            ) {
                Text(
                    "Dividir pagamento",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                AmountLine("Total", session.totalCents)
                AmountLine("Pago", paid)
                AmountLine("Restante", remaining, highlight = remaining > 0L)
                ui.error?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, color = Color(0xFFFF8A80), fontSize = 14.sp)
                }
                Spacer(Modifier.height(16.dp))
                when (ui.step) {
                    SplitStep.Summary -> SplitSummary(
                        ui = ui,
                        remaining = remaining,
                        onAdd = onAdd,
                        onRequestCancel = onRequestCancel,
                    )
                    SplitStep.PickMethod -> {
                        Text(
                            "Forma desta parte",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                        )
                        Spacer(Modifier.height(10.dp))
                        PaymentMethodChoice("Pix", { onPick(PaymentMethodApi.PIX) })
                        Spacer(Modifier.height(8.dp))
                        PaymentMethodChoice("Débito", { onPick(PaymentMethodApi.DEBIT) })
                        Spacer(Modifier.height(8.dp))
                        PaymentMethodChoice("Crédito", { onPick(PaymentMethodApi.CREDIT) })
                        Spacer(Modifier.height(8.dp))
                        PaymentMethodChoice(
                            label = if (cashEnabled) "Dinheiro" else "Dinheiro (caixa fechado)",
                            onClick = { onPick(PaymentMethodApi.CASH) },
                            enabled = cashEnabled,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Voltar",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onBack)
                                .padding(vertical = 12.dp),
                        )
                    }
                    SplitStep.EnterAmount -> {
                        val method = ui.draftMethod?.displayLabel() ?: "Pagamento"
                        Text(
                            method,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        Gate8OutlinedTextField(
                            value = ui.draftAmount,
                            onValueChange = onAmountChange,
                            label = "Valor desta parte",
                            placeholder = "0,00",
                            prefix = "R$",
                            modifier = Modifier.fillMaxWidth(),
                            onDark = true,
                        )
                        Spacer(Modifier.height(14.dp))
                        PaymentMethodChoice(
                            label = if (ui.draftMethod == PaymentMethodApi.CASH) {
                                "Confirmar dinheiro recebido"
                            } else {
                                "Continuar"
                            },
                            onClick = onConfirmAmount,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Voltar",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onBack)
                                .padding(vertical = 12.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SplitSummary(
    ui: SplitPaymentUi,
    remaining: Long,
    onAdd: () -> Unit,
    onRequestCancel: () -> Unit,
) {
    if (ui.session.parts.isEmpty()) {
        Text(
            "Nenhuma parte adicionada.",
            color = Color(0xFFD6E4FF),
            fontSize = 14.sp,
        )
    } else {
        ui.session.parts.forEach { part ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF132A58))
                    .border(1.dp, Color(0xFF0000FE).copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        PaymentMethodApi.fromApiValue(part.method).displayLabel(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    )
                    Text(
                        SplitPayments.statusLabel(part),
                        color = Color(0xFF86EFAC),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    "R$ ${MoneyCents.format(part.amountCents)}",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    if (remaining > 0L) {
        PaymentMethodChoice("+ Adicionar forma de pagamento", onAdd)
    }
    Spacer(Modifier.height(8.dp))
    Text(
        if (ui.session.parts.isEmpty()) "Voltar" else "Cancelar venda",
        color = Color.White,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onRequestCancel)
            .padding(vertical = 12.dp),
    )
}

@Composable
private fun AmountLine(label: String, cents: Long, highlight: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = Color(0xFFD6E4FF),
            fontSize = 15.sp,
        )
        Canvas(
            Modifier
                .weight(1f)
                .padding(horizontal = 10.dp)
                .height(2.dp),
        ) {
            drawLine(
                color = Color(0xFF7EE7FF).copy(alpha = 0.55f),
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 7f), 0f),
            )
        }
        Text(
            "R$ ${MoneyCents.format(cents)}",
            color = if (highlight) Color(0xFF7EE7FF) else Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
