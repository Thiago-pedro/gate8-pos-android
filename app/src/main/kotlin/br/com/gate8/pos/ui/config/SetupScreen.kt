package br.com.gate8.pos.ui.config

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel

@Composable
fun SetupScreen(
    onDone: () -> Unit,
    onLogout: () -> Unit,
    vm: SetupViewModel = koinViewModel(),
) {
    val operator = remember { mutableStateOf(vm.loadOperator()) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Configuração")
        Spacer(Modifier.height(8.dp))
        vm.loadProducerName()?.let {
            Text("Produtor: $it")
        }
        vm.loadDeviceName()?.let {
            Text("Maquininha: $it")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = operator.value,
            onValueChange = { operator.value = it },
            label = { Text("Operador") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                vm.saveOperator(operator.value)
                onDone()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Salvar")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = {
                vm.logout()
                onLogout()
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Sair / trocar produtor")
        }
    }
}
