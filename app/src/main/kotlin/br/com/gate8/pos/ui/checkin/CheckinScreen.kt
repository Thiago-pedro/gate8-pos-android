package br.com.gate8.pos.ui.checkin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import br.com.gate8.pos.domain.model.CheckinOutcome
import br.com.gate8.pos.ui.theme.CheckinInvalid
import br.com.gate8.pos.ui.theme.CheckinOk
import br.com.gate8.pos.ui.theme.CheckinUsed
import br.com.gate8.pos.ui.theme.CheckinWrongEvent
import org.koin.androidx.compose.koinViewModel

@Composable
fun CheckinScreen(onBack: () -> Unit, vm: CheckinViewModel = koinViewModel()) {
    val state by vm.state.collectAsState()
    val bg = when (state.outcome) {
        CheckinOutcome.Ok -> CheckinOk.copy(alpha = 0.2f)
        CheckinOutcome.AlreadyUsed -> CheckinUsed.copy(alpha = 0.2f)
        CheckinOutcome.WrongEvent -> CheckinWrongEvent.copy(alpha = 0.2f)
        CheckinOutcome.Invalid -> CheckinInvalid.copy(alpha = 0.2f)
        else -> Color.Transparent
    }

    Column(
        Modifier.fillMaxSize().background(bg).padding(16.dp),
    ) {
        Button(onClick = onBack) { Text("Voltar") }
        Spacer(Modifier.height(16.dp))
        Text("Check-in — cole o code do QR (32 hex)")
        OutlinedTextField(
            value = state.code,
            onValueChange = vm::onCodeChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("code") },
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = { vm.submit() }, modifier = Modifier.fillMaxWidth()) {
            Text("Validar")
        }
        Spacer(Modifier.height(16.dp))
        Text(state.message)
        state.holderName?.let { Text("Titular: $it") }
    }
}
