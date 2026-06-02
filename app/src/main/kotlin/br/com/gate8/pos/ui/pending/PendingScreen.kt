package br.com.gate8.pos.ui.pending

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel

@Composable
fun PendingScreen(onBack: () -> Unit, vm: PendingViewModel = koinViewModel()) {
    val state by vm.state.collectAsState()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Button(onClick = onBack) { Text("Voltar") }
        Button(onClick = { vm.syncAll() }) { Text("Sincronizar pendentes") }
        state.message?.let { Text(it) }
        LazyColumn {
            items(state.items) { line -> Text(line, modifier = Modifier.padding(8.dp)) }
        }
    }
}
