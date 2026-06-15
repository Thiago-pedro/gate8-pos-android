package br.com.gate8.pos.ui.config



import androidx.compose.foundation.background

import androidx.compose.foundation.layout.Column

import androidx.compose.foundation.layout.Spacer

import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.foundation.layout.fillMaxWidth

import androidx.compose.foundation.layout.height

import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.rememberScrollState

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.foundation.verticalScroll

import androidx.compose.material3.Text

import androidx.compose.runtime.Composable

import androidx.compose.runtime.collectAsState

import androidx.compose.runtime.getValue

import androidx.compose.ui.Modifier

import androidx.compose.ui.draw.clip

import androidx.compose.ui.graphics.Color

import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.unit.dp

import androidx.compose.ui.unit.sp

import br.com.gate8.pos.ui.common.Gate8BackTopBar
import br.com.gate8.pos.ui.common.Gate8MenuButton
import br.com.gate8.pos.ui.common.Gate8OutlinedTextField
import br.com.gate8.pos.ui.common.Gate8ScreenBackground

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

                state.baseUrl?.let { InfoLine("API", it) }

            }



            Spacer(Modifier.height(20.dp))



            SectionTitle("Operador")

            Spacer(Modifier.height(8.dp))

            Gate8OutlinedTextField(

                value = state.operatorName,

                onValueChange = vm::updateOperator,

                label = "Nome do operador",

                modifier = Modifier.fillMaxWidth(),

            )

            Spacer(Modifier.height(10.dp))

            Gate8MenuButton(

                title = "Salvar operador",

                subtitle = "Nome exibido nos comprovantes",

                onClick = vm::saveOperator,

            )



            state.message?.let {

                Spacer(Modifier.height(12.dp))

                Text(it, color = Gate8Colors.Success, fontSize = 13.sp)

            }

            state.error?.let {

                Spacer(Modifier.height(12.dp))

                Text(it, color = Gate8Colors.Error, fontSize = 13.sp)

            }

            Spacer(Modifier.height(28.dp))



            SectionTitle("Sessão")

            Spacer(Modifier.height(12.dp))

            Gate8MenuButton(

                title = "Sair / trocar produtor",

                subtitle = "Encerra a sessão deste terminal",

                onClick = {

                    vm.logout()

                    onLogout()

                },

            )



            Spacer(Modifier.height(32.dp))

        }

    }

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

            .background(Color.White.copy(alpha = 0.92f))

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


