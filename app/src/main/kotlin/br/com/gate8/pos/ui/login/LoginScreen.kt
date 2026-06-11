package br.com.gate8.pos.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    Column(
        Modifier
            .fillMaxSize()
            .background(Gate8Colors.ScreenGradient)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Text(
            "gate8",
            color = Color.White,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "POS — código do produtor",
            color = Gate8Colors.TextSecondary,
            fontSize = 14.sp,
        )
        Spacer(Modifier.height(40.dp))

        OutlinedTextField(
            value = state.producerToken,
            onValueChange = vm::onProducerTokenChange,
            label = { Text("Token (6 caracteres)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = state.label,
            onValueChange = vm::onLabelChange,
            label = { Text("Nome da maquininha (opcional)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Ex.: Caixa 1") },
        )

        state.error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = Gate8Colors.Error, textAlign = TextAlign.Center)
        }

        Spacer(Modifier.height(24.dp))
        if (state.loading) {
            CircularProgressIndicator(color = Gate8Colors.AccentBlue)
        } else {
            Button(
                onClick = vm::login,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.producerToken.length == 6,
            ) {
                Text("Entrar")
            }
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

    Column(
        Modifier
            .fillMaxSize()
            .background(Gate8Colors.ScreenGradient)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(64.dp))
        Text(
            "Aguardando liberação",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            state.pendingDeviceName?.let { "Maquininha: $it" }
                ?: "O produtor precisa liberar esta maquininha no painel.",
            color = Gate8Colors.TextSecondary,
            textAlign = TextAlign.Center,
        )
        state.error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = Gate8Colors.Error, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(32.dp))
        if (state.loading) {
            CircularProgressIndicator(color = Gate8Colors.AccentBlue)
        } else {
            Button(onClick = vm::retryPending, modifier = Modifier.fillMaxWidth()) {
                Text("Verificar")
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = onBackToLogin, modifier = Modifier.fillMaxWidth()) {
                Text("Voltar")
            }
        }
    }
}
