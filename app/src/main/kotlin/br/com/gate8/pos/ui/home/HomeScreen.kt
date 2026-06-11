package br.com.gate8.pos.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import br.com.gate8.pos.R
import br.com.gate8.pos.ui.config.SetupViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun HomeScreen(
    onPdv: () -> Unit,
    onProducts: () -> Unit,
    onCheckin: () -> Unit,
    onPending: () -> Unit,
    onSetup: () -> Unit,
    vm: SetupViewModel = koinViewModel(),
) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Gate8 POS")
        vm.loadProducerName()?.let { Text("Produtor: $it") }
        vm.loadDeviceName()?.let { Text("Maquininha: $it") }
        Button(onClick = onPdv, modifier = Modifier.fillMaxWidth()) { Text("PDV — Ingressos") }
        Button(onClick = onProducts, modifier = Modifier.fillMaxWidth()) { Text("Produtos — Itens") }
        Button(onClick = onCheckin) { Text("Check-in") }
        Button(onClick = onPending) { Text("Vendas pendentes") }
        Button(onClick = onSetup) { Text("Configuração") }
        Text(stringResource(R.string.fale_conosco))
    }
}
