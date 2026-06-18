package br.com.gate8.pos.ui.refund

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.gate8.pos.domain.model.LastSaleRecord
import br.com.gate8.pos.ui.common.Gate8BackTopBar
import br.com.gate8.pos.ui.common.Gate8ConfirmDialog
import br.com.gate8.pos.ui.common.Gate8ScreenBackground
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

                Spacer(Modifier.height(20.dp))

                Text(
                    "Cancelamento / Estorno",
                    color = Gate8Colors.TextPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Escolha a venda que deseja estornar neste terminal",
                    color = Gate8Colors.TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 6.dp),
                )

                Spacer(Modifier.height(20.dp))

                state.message?.let {
                    Text(it, color = Gate8Colors.Success, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                }
                state.error?.let {
                    Text(it, color = Gate8Colors.Error, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                }

                if (state.loading) {
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
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (state.sales.isEmpty()) {
                        Text(
                            "Nenhuma venda registrada neste terminal.",
                            color = Gate8Colors.TextOnLight,
                            fontSize = 14.sp,
                        )
                    } else {
                        state.sales.forEach { sale ->
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

            state.pendingVoid?.let { pending ->
                Gate8ConfirmDialog(
                    title = "Confirmar estorno",
                    message = "Estornar a venda de R$ ${"%.2f".format(pending.total)} " +
                        "(${pending.paymentLabel})? A operação será enviada à adquirente.",
                    confirmLabel = "Estornar",
                    onConfirm = vm::confirmVoid,
                    onDismiss = vm::dismissConfirm,
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
    val date = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date(sale.createdAt))
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Gate8Colors.CardSurface)
            .padding(16.dp),
    ) {
        Text(date, color = Gate8Colors.TextOnLight, fontSize = 12.sp)
        Text(
            "Total: R$ ${"%.2f".format(sale.total)} · ${sale.paymentLabel}",
            color = Gate8Colors.TextPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
        sale.nsu?.let {
            Text("NSU: $it", color = Gate8Colors.TextOnLight, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
        if (sale.saleId != null) {
            Text("ID: ${sale.saleId}", color = Gate8Colors.TextOnLight, fontSize = 11.sp)
        }
        Spacer(Modifier.height(8.dp))
        sale.lines.forEach { line ->
            Text(
                "${line.quantity}x ${line.description} — R$ ${"%.2f".format(line.lineTotal)}",
                fontSize = 12.sp,
                color = Gate8Colors.TextOnLight,
            )
        }
        Spacer(Modifier.height(12.dp))
        if (sale.voided) {
            Text(
                "ESTORNADA",
                color = Gate8Colors.Error,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
            )
        } else {
            Button(
                onClick = onVoid,
                enabled = enabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Gate8Colors.Error,
                    contentColor = Color.White,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Estornar")
            }
        }
    }
}
