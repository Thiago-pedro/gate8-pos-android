package br.com.gate8.pos.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.ConfirmationNumber
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShoppingBasket
import androidx.compose.material.icons.outlined.Undo
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.gate8.pos.BuildConfig
import br.com.gate8.pos.R
import br.com.gate8.pos.ui.common.Gate8ConfirmDialog
import br.com.gate8.pos.ui.common.Gate8HeaderLogo
import br.com.gate8.pos.ui.common.Gate8ScreenBackground
import br.com.gate8.pos.ui.common.Gate8MenuButton
import br.com.gate8.pos.ui.common.Gate8OutlinedTextField
import br.com.gate8.pos.ui.config.SetupViewModel
import br.com.gate8.pos.ui.theme.Gate8Colors
import org.koin.androidx.compose.koinViewModel

@Composable
fun HomeScreen(
    onPdv: () -> Unit,
    onProducts: () -> Unit,
    onCashier: () -> Unit,
    onCashless: () -> Unit = {},
    onRefund: () -> Unit,
    onReports: () -> Unit,
    onSetup: () -> Unit,
    onKitchen: () -> Unit = {},
    vm: SetupViewModel = koinViewModel(),
) {
    val setupState by vm.state.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                vm.onScreenVisible()
                if (vm.isKitchenMode()) onKitchen()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        if (vm.isKitchenMode()) onKitchen()
    }

    BackHandler {
        // Na home da maquininha o voltar do sistema não deve fechar o app.
    }

    Gate8ScreenBackground {
        Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
        ) {
            HomeTopBar(onSetup = onSetup)

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(20.dp))

                HomeIdentityBlock(
                    establishment = setupState.merchantName ?: setupState.producerName,
                    device = setupState.deviceName,
                    operator = setupState.operatorName,
                )

                Spacer(Modifier.height(22.dp))

                HomeMenuCard(
                    title = "Bilheteria",
                    subtitle = "Vender ingressos dos eventos",
                    icon = Icons.Outlined.ConfirmationNumber,
                    onClick = onPdv,
                    highlighted = true,
                )
                Spacer(Modifier.height(12.dp))
                HomeMenuCard(
                    title = "Conveniência",
                    subtitle = "Vender bebidas, comidas e acessórios",
                    icon = Icons.Outlined.ShoppingBasket,
                    onClick = onProducts,
                )
                Spacer(Modifier.height(12.dp))
                HomeMenuCard(
                    title = "Caixa",
                    subtitle = "Abertura, fechamento e movimentações",
                    icon = Icons.Outlined.PointOfSale,
                    onClick = onCashier,
                )
                if (BuildConfig.FLAVOR.equals("cielo", ignoreCase = true)) {
                    Spacer(Modifier.height(12.dp))
                    HomeMenuCard(
                        title = "Cashless",
                        subtitle = "Consultar e adicionar saldo no cartão",
                        icon = Icons.Outlined.CreditCard,
                        onClick = onCashless,
                    )
                }
                Spacer(Modifier.height(12.dp))
                HomeMenuCard(
                    title = "Cancelamento / Estorno",
                    subtitle = "Estornar o pagamento da última venda",
                    icon = Icons.Outlined.Undo,
                    onClick = onRefund,
                )
                Spacer(Modifier.height(12.dp))
                HomeMenuCard(
                    title = "Relatórios",
                    subtitle = "Vendas por período, pagamento e bandeira",
                    icon = Icons.Outlined.Assessment,
                    onClick = onReports,
                )
                Spacer(Modifier.height(12.dp))
                HomeMenuCard(
                    title = "Reimprimir comprovante",
                    subtitle = if (setupState.lastSale != null) {
                        "Última venda · R$ ${"%.2f".format(setupState.lastSale?.total)}"
                    } else {
                        "Nenhuma venda registrada ainda"
                    },
                    icon = Icons.Outlined.Print,
                    onClick = vm::reprintLast,
                    enabled = setupState.lastSale != null,
                )

                setupState.message?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        it,
                        color = Color(0xFF1B7A3D),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (setupState.pendingSyncCount > 0) {
                    Spacer(Modifier.height(12.dp))
                    setupState.error?.let { err ->
                        Text(
                            err,
                            color = Color(0xFFB3261E),
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                    HomeMenuCard(
                        title = if (setupState.syncing) "Enviando vendas…" else "Enviar vendas ao servidor",
                        subtitle = "${setupState.pendingSyncCount} na fila local (pagamento já feito)",
                        icon = Icons.Outlined.Print,
                        onClick = vm::syncPendingSales,
                        enabled = !setupState.syncing,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Limpar fila de teste",
                        color = Gate8Colors.AccentBlue,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = vm::requestClearPendingQueue)
                            .padding(vertical = 10.dp),
                    )
                } else {
                    setupState.error?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            it,
                            color = Color(0xFFB3261E),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))
            }

            Text(
                stringResource(R.string.fale_conosco),
            color = Gate8Colors.TextSecondary,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
            )
        }

        if (setupState.showClearPendingConfirm) {
            Gate8ConfirmDialog(
                title = "Limpar fila local?",
                message = "Remove ${setupState.pendingSyncCount} venda(s) que não subiram ao servidor. " +
                    "Use se foram testes com erro de estoque. Não desfaz pagamentos reais na adquirente.",
                confirmLabel = "Limpar fila",
                dismissLabel = "Cancelar",
                onConfirm = vm::confirmClearPendingQueue,
                onDismiss = vm::dismissClearPendingConfirm,
            )
        }

        if (setupState.operatorMissing) {
            OperatorRequiredDialog(
                value = setupState.operatorName,
                onValueChange = vm::updateOperator,
                onSave = vm::saveOperator,
            )
        }
        }
    }
}

@Composable
private fun OperatorRequiredDialog(
    value: String,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(horizontal = 28.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White.copy(alpha = 0.97f))
                .padding(horizontal = 22.dp, vertical = 24.dp),
        ) {
            Text(
                "Operador obrigatório",
                color = Gate8Colors.TextOnLight,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Informe o nome do operador para começar a operar. Ele aparece nos comprovantes e nos relatórios desta sessão.",
                color = Gate8Colors.TextOnLight.copy(alpha = 0.75f),
                fontSize = 14.sp,
                lineHeight = 20.sp,
            )
            Spacer(Modifier.height(16.dp))
            Gate8OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = "Nome do operador",
                prefix = "POS - ",
                placeholder = "Tulio",
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))
            Gate8MenuButton(
                title = "Salvar e continuar",
                subtitle = "Vincula o operador a esta sessão",
                onClick = onSave,
                enabled = value.isNotBlank(),
                centerText = true,
            )
        }
    }
}

@Composable
private fun HomeIdentityBlock(
    establishment: String?,
    device: String?,
    operator: String,
) {
    val operatorLabel = operator.takeIf { it.isNotBlank() }?.let { "POS - $it" } ?: "—"
    val lineStyle = TextStyle(
        color = Gate8Colors.TextSecondary,
        fontSize = 14.sp,
        lineHeight = 16.sp,
        textAlign = TextAlign.Center,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (!establishment.isNullOrBlank()) {
            Text("Estabelecimento: $establishment", style = lineStyle)
        }
        if (!device.isNullOrBlank()) {
            Text("Dispositivo: $device", style = lineStyle)
        }
        Text("Operador: $operatorLabel", style = lineStyle)
    }
}

@Composable
private fun HomeMenuCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    highlighted: Boolean = false,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(16.dp)
    val alpha = if (enabled) 1f else 0.45f
    val titleColor = if (highlighted) Color.White else Gate8Colors.TextPrimary
    val subtitleColor = if (highlighted) Color.White.copy(alpha = 0.9f) else Gate8Colors.TextSecondary
    val accent = if (highlighted) Color.White else Gate8Colors.AccentBlue
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (highlighted) Gate8Colors.AccentBlue else Color.White)
            .then(
                if (highlighted) {
                    Modifier
                } else {
                    Modifier.border(1.5.dp, Gate8Colors.AccentBlue.copy(alpha = alpha), shape)
                },
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = accent.copy(alpha = alpha),
            modifier = Modifier.size(28.dp),
        )
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 14.dp),
        ) {
            Text(
                title,
                color = titleColor.copy(alpha = alpha),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                subtitle,
                color = subtitleColor.copy(alpha = alpha),
                fontSize = 13.sp,
                lineHeight = 17.sp,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = accent.copy(alpha = alpha),
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun HomeTopBar(onSetup: () -> Unit) {
    androidx.compose.foundation.layout.Box(
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
