package br.com.gate8.pos.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.gate8.pos.ui.common.Gate8HeaderLogo
import br.com.gate8.pos.ui.common.Gate8MenuButton
import br.com.gate8.pos.ui.common.Gate8OutlinedTextField
import br.com.gate8.pos.ui.common.Gate8ScreenBackground
import br.com.gate8.pos.ui.theme.Gate8Colors
import org.koin.androidx.compose.koinViewModel

@Composable
fun LoginScreen(
    onHome: () -> Unit,
    onPending: () -> Unit,
    vm: LoginViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsState()

    LaunchedEffect(Unit) {
        vm.navigation.collect { nav ->
            when (nav) {
                LoginNavigation.Home -> onHome()
                LoginNavigation.Pending -> onPending()
            }
        }
    }

    Gate8ScreenBackground {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(40.dp))
            Gate8HeaderLogo(height = 52.dp)
            Spacer(Modifier.height(12.dp))
            Text(
                "POS — código do produtor",
                color = Gate8Colors.TextPrimary.copy(alpha = 0.85f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))

            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.94f))
                    .padding(horizontal = 20.dp, vertical = 24.dp),
            ) {
                Gate8OutlinedTextField(
                    value = state.producerToken,
                    onValueChange = vm::onProducerTokenChange,
                    label = "Token (6 caracteres)",
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                )
                Spacer(Modifier.height(12.dp))
                Gate8OutlinedTextField(
                    value = state.label,
                    onValueChange = vm::onLabelChange,
                    label = "Nome da maquininha (opcional)",
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = "Ex.: Caixa 1",
                )

                state.error?.let {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        it,
                        color = Gate8Colors.Error,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(Modifier.height(20.dp))
                if (state.loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        color = Gate8Colors.AccentBlue,
                    )
                } else {
                    Gate8MenuButton(
                        title = "Entrar",
                        subtitle = "Vincular esta maquininha ao produtor",
                        onClick = vm::login,
                        enabled = state.producerToken.length == 6,
                        centerText = true,
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun LoginPendingScreen(
    onBackToLogin: () -> Unit,
    onHome: () -> Unit,
    vm: LoginViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsState()

    LaunchedEffect(Unit) {
        vm.navigation.collect { nav ->
            when (nav) {
                LoginNavigation.Home -> onHome()
                LoginNavigation.Pending -> Unit
            }
        }
    }

    Gate8ScreenBackground {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))
            Gate8HeaderLogo(height = 52.dp)
            Spacer(Modifier.height(32.dp))

            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.94f))
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Aguardando liberação",
                    color = Gate8Colors.TextOnLight,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    state.pendingDeviceName?.let { "Maquininha: $it" }
                        ?: "O produtor precisa liberar esta maquininha no painel.",
                    color = Gate8Colors.TextOnLight.copy(alpha = 0.75f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
                state.error?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        it,
                        color = Gate8Colors.Error,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(20.dp))
                if (state.loading) {
                    CircularProgressIndicator(color = Gate8Colors.AccentBlue)
                } else {
                    Gate8MenuButton(
                        title = "Verificar",
                        subtitle = "Consultar se já foi liberada",
                        onClick = vm::retryPending,
                        centerText = true,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Voltar",
                        color = Gate8Colors.AccentBlue,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onBackToLogin)
                            .padding(vertical = 14.dp),
                    )
                }
            }
        }
    }
}
