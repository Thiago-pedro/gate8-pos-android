package br.com.gate8.pos.ui.config



import androidx.compose.foundation.background

import androidx.compose.foundation.layout.Box

import androidx.compose.foundation.layout.Column

import androidx.compose.foundation.layout.Row

import androidx.compose.foundation.layout.Spacer

import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.foundation.layout.fillMaxWidth

import androidx.compose.foundation.layout.height

import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.layout.width

import androidx.compose.foundation.rememberScrollState

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.foundation.verticalScroll

import androidx.compose.material3.Switch

import androidx.compose.material3.Text

import androidx.compose.ui.Alignment

import androidx.compose.runtime.Composable

import androidx.compose.runtime.collectAsState

import androidx.compose.runtime.getValue

import androidx.compose.runtime.mutableStateOf

import androidx.compose.runtime.remember

import androidx.compose.runtime.setValue

import androidx.compose.ui.Modifier

import androidx.compose.ui.draw.clip

import androidx.compose.ui.graphics.Color

import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.unit.dp

import androidx.compose.ui.unit.sp

import br.com.gate8.pos.ui.common.Gate8AlertDialog
import br.com.gate8.pos.ui.common.Gate8BackTopBar
import br.com.gate8.pos.ui.common.Gate8MenuButton
import br.com.gate8.pos.ui.common.Gate8OutlinedTextField
import br.com.gate8.pos.ui.common.Gate8ScreenBackground
import br.com.gate8.pos.ui.common.Gate8SuccessDialog

import br.com.gate8.pos.ui.theme.Gate8Colors

import org.koin.androidx.compose.koinViewModel



@Composable

fun SetupScreen(

    onDone: () -> Unit,

    onLogout: () -> Unit,

    vm: SetupViewModel = koinViewModel(),

) {

    val state by vm.state.collectAsState()

    val scrollState = rememberScrollState()

    var showCashierOpenWarning by remember { mutableStateOf(false) }



    Gate8ScreenBackground {

    Column(

        Modifier

            .fillMaxSize()

            .padding(horizontal = 24.dp),

    ) {

        Gate8BackTopBar(onBack = onDone)



        Spacer(Modifier.height(16.dp))



        Text(

            "Configurações",

            color = Gate8Colors.TextPrimary,

            fontSize = 24.sp,

            fontWeight = FontWeight.Bold,

        )



        Column(

            Modifier

                .weight(1f)

                .verticalScroll(scrollState),

        ) {

            Spacer(Modifier.height(20.dp))



            SectionTitle("Terminal")

            Spacer(Modifier.height(8.dp))

            InfoPanel {

                state.producerName?.let { InfoLine("Produtor", it) }

                state.deviceName?.let { InfoLine("Maquininha", it) }

                state.deviceId?.let { InfoLine("ID dispositivo", it) }

                state.terminalManufacturer?.let { InfoLine("Fabricante", it) }

                state.terminalSerial?.let { InfoLine("Serial POS", it) }

                state.baseUrl?.let { InfoLine("API", it) }

            }



            Spacer(Modifier.height(20.dp))



            SectionTitle("Operador")

            Spacer(Modifier.height(8.dp))

            Gate8OutlinedTextField(

                value = state.operatorName,

                onValueChange = vm::updateOperator,

                label = "Nome do operador",

                prefix = "POS - ",

                placeholder = "Tulio",

                modifier = Modifier.fillMaxWidth(),

            )

            Spacer(Modifier.height(10.dp))

            Gate8MenuButton(

                title = "Salvar operador",

                subtitle = "Nome exibido nos comprovantes",

                onClick = vm::saveOperator,

            )



            if (state.showTerminalSection) {
                Spacer(Modifier.height(20.dp))

                SectionTitle("Mercado Pago Point")

                Spacer(Modifier.height(8.dp))

                Gate8OutlinedTextField(
                    value = state.terminalId,
                    onValueChange = vm::updateTerminalId,
                    label = "Terminal ID",
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(6.dp))
                Text(
                    "ID do terminal Point em modo PDV (ex.: NEWLAND_N950__N950NCB801293324).",
                    color = Gate8Colors.TextSecondary,
                    fontSize = 12.sp,
                )

                Spacer(Modifier.height(10.dp))

                Gate8MenuButton(
                    title = if (state.terminalSaving) "Salvando…" else "Salvar terminal",
                    subtitle = "Necessário para cobranças via API de Orders",
                    onClick = vm::saveTerminalId,
                )
            }

            Spacer(Modifier.height(28.dp))



            SectionTitle("Conveniência")

            Spacer(Modifier.height(8.dp))

            ConvenienceTicketToggle(
                enabled = state.convenienceTicketMode,
                onToggle = vm::setConvenienceTicketMode,
            )

            Spacer(Modifier.height(28.dp))

            SectionTitle("Sessão")

            Spacer(Modifier.height(12.dp))

            Gate8MenuButton(

                title = "Sair / trocar produtor",

                subtitle = "Encerra a sessão deste terminal",

                onClick = {

                    if (state.cashierOpen) {

                        showCashierOpenWarning = true

                    } else {

                        vm.logout()

                        onLogout()

                    }

                },

            )



            Spacer(Modifier.height(32.dp))

        }

    }

    }

    state.message?.let { msg ->
        Gate8SuccessDialog(
            title = msg,
            onDismiss = { vm.dismissNotice() },
        )
    }

    state.error?.let { err ->
        Gate8AlertDialog(
            title = "Atenção",
            detail = err,
            onDismiss = { vm.dismissNotice() },
        )
    }

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



@Composable
private fun ConvenienceTicketToggle(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Gate8Colors.CardSurface)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Modo ficha",
                    color = Gate8Colors.TextOnLight,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (enabled) Gate8Colors.AccentBlue
                            else Gate8Colors.TextOnLight.copy(alpha = 0.25f),
                        )
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        if (enabled) "LIGADO" else "DESLIGADO",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Além dos comprovantes, sai uma ficha separada por unidade de item (ex.: 2 copões = 2 fichas). Desligado, sai só o recibo único.",
                color = Gate8Colors.TextOnLight.copy(alpha = 0.7f),
                fontSize = 12.sp,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = enabled, onCheckedChange = onToggle)
    }
}

@Composable

private fun SectionTitle(text: String) {

    Text(

        text,

        color = Gate8Colors.TextPrimary,

        fontSize = 16.sp,

        fontWeight = FontWeight.SemiBold,

        modifier = Modifier.padding(bottom = 2.dp),

    )

}



@Composable

private fun InfoPanel(content: @Composable () -> Unit) {

    Column(

        Modifier

            .fillMaxWidth()

            .clip(RoundedCornerShape(12.dp))

            .background(Gate8Colors.CardSurface)

            .padding(16.dp),

    ) {

        content()

    }

}



@Composable

private fun InfoLine(label: String, value: String) {

    Text(

        label,

        color = Gate8Colors.TextOnLight.copy(alpha = 0.7f),

        fontSize = 11.sp,

    )

    Text(

        value,

        color = Gate8Colors.TextOnLight,

        fontSize = 14.sp,

        modifier = Modifier.padding(bottom = 10.dp),

    )

}


