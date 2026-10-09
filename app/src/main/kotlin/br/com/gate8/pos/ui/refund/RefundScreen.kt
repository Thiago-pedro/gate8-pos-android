package br.com.gate8.pos.ui.refund

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.gate8.pos.domain.model.LastSaleRecord
import br.com.gate8.pos.domain.model.PaymentMethodApi
import br.com.gate8.pos.ui.common.Gate8BackTopBar
import br.com.gate8.pos.ui.common.Gate8OutlinedTextField
import br.com.gate8.pos.ui.common.Gate8ScreenBackground
import br.com.gate8.pos.ui.common.Gate8SuccessDialog
import br.com.gate8.pos.ui.common.PaymentWaitingOverlay
import br.com.gate8.pos.ui.theme.Gate8Colors
import org.koin.androidx.compose.koinViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RefundScreen(
    onBack: () -> Unit,
    vm: RefundViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                vm.onScreenVisible()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            vm.cancelManagerWait()
        }
    }

    Gate8ScreenBackground {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
            ) {
                Gate8BackTopBar(onBack = onBack)

                Spacer(Modifier.height(20.dp))

                Text(
                    "Cancelamento / Estorno",
                    color = Gate8Colors.TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Vendas de hoje neste terminal. Estornos de dias anteriores devem ser feitos pelo painel.",
                    color = Gate8Colors.TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )

                Spacer(Modifier.height(16.dp))

                Gate8OutlinedTextField(
                    value = state.query,
                    onValueChange = vm::onQueryChange,
                    label = "Buscar por NSU, valor ou código",
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(16.dp))

                state.message?.let {
                    Text(it, color = Gate8Colors.Success, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                }
                state.error?.let {
                    Text(it, color = Gate8Colors.Error, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                }

                if (state.loading && !state.waitingCashlessCard) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(vertical = 8.dp),
                        color = Gate8Colors.AccentBlue,
                    )
                }

                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val visible = state.visibleSales
                    if (visible.isEmpty()) {
                        Text(
                            if (state.sales.isEmpty()) {
                                "Nenhuma venda registrada hoje neste terminal."
                            } else {
                                "Nenhuma venda encontrada para a busca."
                            },
                            color = Gate8Colors.TextOnLight,
                            fontSize = 14.sp,
                        )
                    } else {
                        visible.forEach { sale ->
                            SaleCard(
                                sale = sale,
                                enabled = !state.loading && !sale.voided,
                                onVoid = { vm.requestVoid(sale) },
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }

            PaymentWaitingOverlay(
                visible = state.waitingManagerCard,
                method = PaymentMethodApi.CASHLESS,
                amount = state.pendingVoid?.total ?: 0.0,
                titleOverride = "Cartão do gerente",
                messageOverride = "Aproxime o cartão do gerente para liberar o estorno.",
                amountCaption = "Estorno",
                onCancel = vm::cancelManagerWait,
            )

            PaymentWaitingOverlay(
                visible = state.waitingCashlessCard,
                method = PaymentMethodApi.CASHLESS,
                amount = state.waitingCashlessAmount,
                titleOverride = "Estorno cashless",
                messageOverride = "Aproxime o mesmo cartão da venda para devolver o saldo.",
                amountCaption = "Valor a devolver",
            )

            if (state.voidSuccess) {
                Gate8SuccessDialog(
                    title = "Estorno concluído com sucesso!",
                    onDismiss = vm::dismissVoidSuccess,
                )
            }
        }
    }
}

@Composable
private fun SaleCard(
    sale: LastSaleRecord,
    enabled: Boolean,
    onVoid: () -> Unit,
) {
    val date = SimpleDateFormat("dd/MM HH:mm", Locale("pt", "BR")).format(Date(sale.createdAt))
    val payment = sale.paymentLabel
        .replace('\n', ' ')
        .replace(Regex("\\s+"), " ")
        .trim()
    val meta = listOfNotNull(
        sale.nsu?.takeIf { it.isNotBlank() }?.let { "NSU $it" },
        sale.cashlessUid?.takeIf { it.isNotBlank() }?.let { "UID $it" },
        sale.saleId?.takeIf { it.isNotBlank() }?.let { "ID $it" },
    ).joinToString(" · ")
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.5.dp, Gate8Colors.AccentBlue, RoundedCornerShape(12.dp))
            .background(Gate8Colors.CardSurface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                date,
                color = Gate8Colors.TextSecondary,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            Text(
                "R$ ${"%.2f".format(sale.total)}",
                color = Gate8Colors.AccentBlue,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
        }
        Text(
            payment,
            color = Gate8Colors.TextPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (meta.isNotBlank()) {
            Text(
                meta,
                color = Gate8Colors.TextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        sale.lines.forEach { line ->
            Text(
                "${line.quantity}x ${line.description} — R$ ${"%.2f".format(line.lineTotal)}",
                fontSize = 12.sp,
                lineHeight = 15.sp,
                color = Gate8Colors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(6.dp))
        if (sale.voided) {
            Text(
                "ESTORNADA",
                color = Gate8Colors.Error,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
            )
        } else {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (enabled) Gate8Colors.Error else Gate8Colors.Error.copy(alpha = 0.35f),
                    )
                    .clickable(enabled = enabled, onClick = onVoid),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Estornar",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
