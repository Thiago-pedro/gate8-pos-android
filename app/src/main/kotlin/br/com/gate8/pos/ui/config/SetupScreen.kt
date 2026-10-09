package br.com.gate8.pos.ui.config

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.Contactless
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.gate8.pos.BuildConfig
import br.com.gate8.pos.domain.model.PaymentMethodApi
import br.com.gate8.pos.ui.common.Gate8AlertDialog
import br.com.gate8.pos.ui.common.Gate8BackTopBar
import br.com.gate8.pos.ui.common.Gate8OutlinedTextField
import br.com.gate8.pos.ui.common.Gate8ScreenBackground
import br.com.gate8.pos.ui.common.Gate8SuccessDialog
import br.com.gate8.pos.ui.common.PaymentWaitingOverlay
import br.com.gate8.pos.ui.theme.Gate8Colors
import org.koin.androidx.compose.koinViewModel

@Composable
fun SetupScreen(
    onDone: () -> Unit,
    onLogout: () -> Unit,
    onOpenKitchen: () -> Unit = {},
    vm: SetupViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsState()
    val scrollState = rememberScrollState()
    var showCashierOpenWarning by remember { mutableStateOf(false) }
    var showTerminalDetails by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { vm.cancelManagerEnroll() }
    }

    Gate8ScreenBackground {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
        ) {
            Gate8BackTopBar(onBack = onDone)
            Spacer(Modifier.height(8.dp))
            Text(
                "Configurações",
                color = Gate8Colors.TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Como esta maquininha trabalha neste evento.",
                color = Gate8Colors.TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 2.dp),
            )

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                Spacer(Modifier.height(2.dp))
                IdentityCard(state)
                SectionLabel("Operação")
                SettingsCard {
                    SettingToggle(
                        icon = Icons.Outlined.Print,
                        title = "Modo ficha",
                        description = "Uma ficha por unidade, além do recibo.",
                        enabled = state.convenienceTicketMode,
                        onToggle = vm::setConvenienceTicketMode,
                    )
                    HorizontalDivider(color = Gate8Colors.TextSecondary.copy(alpha = 0.12f))
                    SettingToggle(
                        icon = Icons.Outlined.Restaurant,
                        title = "Modo cozinha",
                        description = "Imprime só os itens da categoria Cozinha.",
                        enabled = state.kitchenMode,
                        onToggle = vm::setKitchenMode,
                    )
                }

                SectionLabel("Estorno")
                SettingsCard {
                    ManagerCardBlock(
                        uid = state.managerCardUid,
                        busy = state.waitingManagerCard,
                        onEnroll = vm::enrollManagerCard,
                        onClear = vm::clearManagerCard,
                    )
                }

                SectionLabel("Operador")
                SettingsCard {
                    Text(
                        "Nome impresso nos comprovantes",
                        color = Gate8Colors.TextSecondary,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Gate8OutlinedTextField(
                        value = state.operatorName,
                        onValueChange = vm::updateOperator,
                        label = "Operador",
                        prefix = "POS - ",
                        placeholder = "Tulio",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (state.operatorMissing) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Informe o operador antes de vender.",
                            color = Gate8Colors.Error,
                            fontSize = 12.sp,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    CompactButton(
                        label = "Salvar operador",
                        onClick = vm::saveOperator,
                    )
                }

                SectionLabel("Terminal")
                SettingsCard {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { showTerminalDetails = !showTerminalDetails }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                state.deviceName ?: "Este terminal",
                                color = Gate8Colors.TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                terminalSummary(state),
                                color = Gate8Colors.TextSecondary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Icon(
                            if (showTerminalDetails) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (showTerminalDetails) "Ocultar dados" else "Ver dados",
                            tint = Gate8Colors.AccentBlue,
                        )
                    }
                    if (showTerminalDetails) {
                        Spacer(Modifier.height(8.dp))
                        HorizontalDivider(color = Gate8Colors.TextSecondary.copy(alpha = 0.12f))
                        DetailRow("Versão", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                        state.producerName?.let { DetailRow("Produtor", it) }
                        state.deviceId?.let { DetailRow("ID", it) }
                        state.terminalManufacturer?.let { DetailRow("Fabricante", it) }
                        state.terminalSerial?.let { DetailRow("Serial", it) }
                        state.baseUrl?.let { DetailRow("API", it) }
                    }
                }

                LogoutRow(
                    onClick = {
                        if (state.cashierOpen) showCashierOpenWarning = true
                        else vm.logout(onLogout)
                    },
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    state.message?.let { msg ->
        val openKitchen = state.openKitchenOnDismiss
        Gate8SuccessDialog(
            title = msg,
            onDismiss = {
                vm.dismissNotice()
                if (openKitchen) onOpenKitchen()
            },
        )
    }
    state.error?.let { err ->
        Gate8AlertDialog(
            title = "Atenção",
            detail = err,
            onDismiss = { vm.dismissNotice() },
        )
    }
    PaymentWaitingOverlay(
        visible = state.waitingManagerCard,
        method = PaymentMethodApi.CASHLESS,
        amount = 0.0,
        titleOverride = "Cartão do gerente",
        messageOverride = "Aproxime o cartão cashless que vai liberar os estornos.",
        showAmount = false,
        onCancel = vm::cancelManagerEnroll,
    )
    if (showCashierOpenWarning) {
        Gate8AlertDialog(
            title = "Caixa aberto",
            reason = "Feche o caixa antes de sair ou trocar de produtor.",
            detail = "Volte à tela inicial e abra \"Caixa\" para fazer o fechamento.",
            buttonLabel = "Entendi",
            onDismiss = { showCashierOpenWarning = false },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IdentityCard(state: SetupUiState) {
    val operator = state.operatorName.trim()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Gate8Colors.AccentBlue)
            .padding(16.dp),
    ) {
        Text(
            state.deviceName ?: "Maquininha Gate8",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        state.producerName?.let {
            Text(
                it,
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            IdentityChip(if (state.cashierOpen) "Caixa aberto" else "Caixa fechado")
            IdentityChip(if (operator.isBlank()) "Sem operador" else "POS - $operator")
            IdentityChip("v${BuildConfig.VERSION_NAME}")
        }
    }
}

@Composable
private fun IdentityChip(text: String) {
    Text(
        text,
        color = Color.White,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = Gate8Colors.TextSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.4.sp,
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Gate8Colors.CardSurface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        content()
    }
}

@Composable
private fun SettingToggle(
    icon: ImageVector,
    title: String,
    description: String,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = Gate8Colors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                description,
                color = Gate8Colors.TextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Gate8Colors.AccentBlue,
                checkedThumbColor = Color.White,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = Gate8Colors.CardSurfaceElevated,
                uncheckedBorderColor = Gate8Colors.TextSecondary.copy(alpha = 0.35f),
            ),
        )
    }
}

@Composable
private fun ManagerCardBlock(
    uid: String?,
    busy: Boolean,
    onEnroll: () -> Unit,
    onClear: () -> Unit,
) {
    val enrolled = !uid.isNullOrBlank()
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBadge(Icons.Filled.Contactless, tint = if (enrolled) Gate8Colors.Success else Gate8Colors.AccentBlue)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Cartão do gerente",
                color = Gate8Colors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                if (enrolled) "Cadastrado · $uid" else "Pendente. Sem ele, o estorno não libera.",
                color = if (enrolled) Gate8Colors.Success else Gate8Colors.TextSecondary,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    Spacer(Modifier.height(12.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CompactButton(
            label = if (enrolled) "Trocar cartão" else "Gravar cartão",
            onClick = onEnroll,
            enabled = !busy,
            modifier = Modifier.weight(1f),
        )
        if (enrolled) {
            CompactButton(
                label = "Remover",
                onClick = onClear,
                enabled = !busy,
                container = Color.Transparent,
                content = Gate8Colors.Error,
                border = Gate8Colors.Error.copy(alpha = 0.45f),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = Gate8Colors.TextSecondary,
            fontSize = 13.sp,
            modifier = Modifier.width(88.dp),
        )
        Text(
            value,
            color = Gate8Colors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun LogoutRow(onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, Gate8Colors.Error.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.AutoMirrored.Outlined.Logout,
            contentDescription = null,
            tint = Gate8Colors.Error,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Sair da sessão",
                color = Gate8Colors.Error,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Encerra este terminal. Feche o caixa antes.",
                color = Gate8Colors.TextSecondary,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun IconBadge(icon: ImageVector, tint: Color = Gate8Colors.AccentBlue) {
    Box(
        Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(tint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun CompactButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    container: Color = Gate8Colors.AccentBlue,
    content: Color = Color.White,
    border: Color? = null,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .height(40.dp)
            .clip(shape)
            .then(if (border != null) Modifier.border(1.dp, border, shape) else Modifier)
            .background(if (enabled) container else container.copy(alpha = 0.4f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (enabled) content else content.copy(alpha = 0.6f),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun terminalSummary(state: SetupUiState): String {
    val bits = listOfNotNull(
        state.terminalManufacturer?.takeIf { it.isNotBlank() },
        state.terminalSerial?.takeIf { it.isNotBlank() }?.let { "serial $it" },
        "v${BuildConfig.VERSION_NAME}",
    )
    return bits.joinToString(" · ")
}
