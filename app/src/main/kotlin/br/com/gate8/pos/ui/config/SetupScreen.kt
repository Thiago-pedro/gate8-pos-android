package br.com.gate8.pos.ui.config

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel

@Composable
fun SetupScreen(
    onDone: () -> Unit,
    vm: SetupViewModel = koinViewModel(),
) {
    val baseUrl = remember { mutableStateOf(vm.loadBaseUrl()) }
    val token = remember { mutableStateOf(vm.loadToken()) }
    val operator = remember { mutableStateOf(vm.loadOperator()) }
    val shortId = remember { mutableStateOf(vm.loadShortId()) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Configuração Gate8 POS")
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = baseUrl.value,
            onValueChange = { baseUrl.value = it },
            label = { Text("Base URL") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = token.value,
            onValueChange = { token.value = it },
            label = { Text("device_token (g8pos_...)") },
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = operator.value, onValueChange = { operator.value = it }, label = { Text("Operador") })
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = shortId.value, onValueChange = { shortId.value = it }, label = { Text("ID curto device") })
        Spacer(Modifier.height(16.dp))
        Button(onClick = {
            vm.save(baseUrl.value, token.value, operator.value, shortId.value)
            onDone()
        }) {
            Text("Salvar e continuar")
        }
    }
}
