package br.com.gate8.pos.ui.refund

import androidx.compose.foundation.background
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
import br.com.gate8.pos.ui.common.Gate8BackTopBar
import br.com.gate8.pos.ui.common.Gate8ConfirmDialog
import br.com.gate8.pos.ui.common.Gate8MenuButton
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
            "Estorne o pagamento da última venda na maquininha",
            color = Gate8Colors.TextSecondary,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 6.dp),
        )

        Spacer(Modifier.height(24.dp))

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            val sale = state.lastSale
            if (sale == null) {
                Text(
                    "Nenhuma venda registrada neste terminal.",
                    color = Gate8Colors.TextOnLight,
                    fontSize = 14.sp,
                )
            } else {
                LastSaleCard(sale)
            }

            state.message?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = Color(0xFF1B7A3D), fontSize = 13.sp)
            }
            state.error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = Color(0xFFB3261E), fontSize = 13.sp)
            }

            if (state.loading) {
                Spacer(Modifier.height(16.dp))
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    color = Gate8Colors.AccentBlue,
                )
            }
        }

        val canVoid = state.lastSale != null && state.lastSale?.voided != true && !state.loading
        Gate8MenuButton(
            title = "Estornar última venda",
            subtitle = when {
                state.lastSale == null -> "Faça uma venda antes de estornar"
                state.lastSale?.voided == true -> "Venda já estornada"
                else -> "Cancela o pagamento na adquirente"
            },
            onClick = vm::requestVoid,
            enabled = canVoid,
        )
        Spacer(Modifier.height(24.dp))
    }

    if (state.showConfirm) {
        Gate8ConfirmDialog(
            title = "Confirmar estorno",
            message = "Estornar a última venda de R$ ${"%.2f".format(state.lastSale?.total ?: 0.0)}? " +
                "A operação será enviada à adquirente.",
            confirmLabel = "Estornar",
            onConfirm = vm::confirmVoid,
            onDismiss = vm::dismissConfirm,
        )
    }
    }
    }
}

@Composable
private fun LastSaleCard(sale: br.com.gate8.pos.domain.model.LastSaleRecord) {
    val date = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("pt", "BR")).format(Date(sale.createdAt))
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.92f))
            .padding(16.dp),
    ) {
        Text("Última venda", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Text(date, color = Gate8Colors.TextOnLight, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        if (sale.saleId != null) {
            Text("ID: ${sale.saleId}", color = Gate8Colors.TextOnLight, fontSize = 12.sp)
        }
        Text(
            "Total: R$ ${"%.2f".format(sale.total)} · ${sale.paymentLabel}",
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 8.dp),
        )
        sale.nsu?.let {
            Text("NSU: $it", color = Gate8Colors.TextOnLight, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
        if (sale.voided) {
            Text(
                "ESTORNADA",
                color = Color(0xFFB3261E),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        sale.lines.forEach { line ->
            Text(
                "${line.quantity}x ${line.description} — R$ ${"%.2f".format(line.lineTotal)}",
                fontSize = 12.sp,
                color = Gate8Colors.TextOnLight,
            )
        }
    }
}
