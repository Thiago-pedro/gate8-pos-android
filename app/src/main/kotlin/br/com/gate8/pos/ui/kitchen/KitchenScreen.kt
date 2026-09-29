package br.com.gate8.pos.ui.kitchen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.gate8.pos.ui.common.Gate8HeaderLogo
import br.com.gate8.pos.ui.common.Gate8MenuButton
import br.com.gate8.pos.ui.common.Gate8ScreenBackground
import br.com.gate8.pos.ui.theme.Gate8Colors
import org.koin.androidx.compose.koinViewModel

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
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(36.dp))
                Text(
                    "COZINHA",
                    color = Gate8Colors.TextPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    state.status,
                    color = Gate8Colors.TextSecondary,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    when (state.apiAvailable) {
                        true -> "Fila do painel conectada"
                        false -> "Fila neste aparelho. Duas maquininhas precisam do endpoint no painel."
                        null -> "Conectando…"
                    },
                    color = Gate8Colors.TextSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                state.lastOrderNumber?.let { number ->
                    Spacer(Modifier.height(28.dp))
                    Text(
                        "Pedido $number",
                        color = Gate8Colors.TextPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    state.lastItemsLabel?.let { items ->
                        Spacer(Modifier.height(6.dp))
                        Text(
                            items,
                            color = Gate8Colors.TextSecondary,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                state.error?.let {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        it,
                        color = Gate8Colors.TextSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    )
                }

                Spacer(Modifier.height(36.dp))
                Gate8MenuButton(
                    title = if (state.printing) "Imprimindo…" else "Pedido teste",
                    subtitle = "Imprime PEDIDO com X-Burger e batata",
                    onClick = vm::printTestOrder,
                    enabled = !state.printing,
                    centerText = true,
                )
                Spacer(Modifier.height(12.dp))
                Gate8MenuButton(
                    title = "Sair do modo cozinha",
                    subtitle = "Esta maquininha volta a vender",
                    onClick = vm::exitKitchen,
                    centerText = true,
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

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
