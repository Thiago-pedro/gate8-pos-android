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

@Composable
fun HomeScreen(
    onPdv: () -> Unit,
    onCheckin: () -> Unit,
    onPending: () -> Unit,
    onSetup: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Gate8 POS")
        Button(onClick = onPdv, modifier = Modifier.fillMaxWidth()) { Text("PDV — Vendas") }
        Button(onClick = onCheckin) { Text("Check-in") }
        Button(onClick = onPending) { Text("Vendas pendentes") }
        Button(onClick = onSetup) { Text("Configuração") }
        Text(stringResource(R.string.fale_conosco))
    }
}
