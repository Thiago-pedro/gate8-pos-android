package br.com.gate8.pos.ui.kitchen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.gate8.pos.domain.model.KitchenOrder
import br.com.gate8.pos.ui.common.Gate8HeaderLogo
import br.com.gate8.pos.ui.common.Gate8MenuButton
import br.com.gate8.pos.ui.common.Gate8ScreenBackground
import br.com.gate8.pos.ui.theme.Gate8Colors
import org.koin.androidx.compose.koinViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun KitchenScreen(
    onHome: () -> Unit,
    onSetup: () -> Unit,
    vm: KitchenViewModel = koinViewModel(),
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

    LaunchedEffect(state.kitchenMode) {
        if (!state.kitchenMode) onHome()
    }

    BackHandler {
        // Modo cozinha dedicado: o voltar não sai da escuta.
    }

    Gate8ScreenBackground {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
        ) {
            KitchenTopBar(onSetup = onSetup)

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "COZINHA",
                    color = Gate8Colors.TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
                Text(
                    state.status,
                    color = Gate8Colors.TextSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    when (state.apiAvailable) {
                        true -> "Fila do painel conectada"
                        false -> "Sem conexão com a fila"
                        null -> "Conectando…"
                    },
                    color = Gate8Colors.TextSecondary,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                state.error?.let {
                    Text(
                        it,
                        color = Gate8Colors.Error,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                    )
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    "Últimos pedidos",
                    color = Gate8Colors.TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                if (state.recentOrders.isEmpty()) {
                    Text(
                        "Nenhum pedido da cozinha ainda.",
                        color = Gate8Colors.TextSecondary,
                        fontSize = 13.sp,
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.recentOrders.asReversed().forEach { order ->
                            RecentKitchenCard(order)
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            Gate8MenuButton(
                title = "Sair do modo cozinha",
                subtitle = "Esta maquininha volta a vender",
                onClick = vm::exitKitchen,
                centerText = true,
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun RecentKitchenCard(order: KitchenOrder) {
    val shape = RoundedCornerShape(12.dp)
    val items = order.items.joinToString(" · ") { "${it.quantity}x ${it.description}" }
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, Gate8Colors.AccentBlue, shape)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.86f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Pedido ${order.orderNumber}",
                color = Gate8Colors.AccentBlue,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            if (order.terminalName.isNotBlank()) {
                Text(
                    " · ${order.terminalName}",
                    color = Gate8Colors.TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            Text(
                formatKitchenTime(order.soldAtMillis),
                color = Gate8Colors.TextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
            )
        }
        if (items.isNotBlank()) {
            Text(
                items,
                color = Gate8Colors.TextPrimary,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        order.note?.takeIf { it.isNotBlank() }?.let { note ->
            Text(
                "Obs: $note",
                color = Gate8Colors.TextSecondary,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun formatKitchenTime(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(kitchenZone).format(kitchenTimeFmt)

private val kitchenZone: ZoneId = ZoneId.systemDefault()
private val kitchenTimeFmt: DateTimeFormatter =
    DateTimeFormatter.ofPattern("dd/MM HH:mm", Locale("pt", "BR"))

@Composable
private fun KitchenTopBar(onSetup: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
    ) {
        Gate8HeaderLogo(
            modifier = Modifier.align(Alignment.Center),
        )
        IconButton(
            onClick = onSetup,
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            Icon(
                Icons.Outlined.Settings,
                contentDescription = "Configuração",
                tint = Gate8Colors.TextPrimary,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}
