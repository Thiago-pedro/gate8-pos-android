package br.com.gate8.pos.ui.pdv

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.gate8.pos.domain.model.PaymentMethodApi
import org.koin.androidx.compose.koinViewModel

@Composable
fun PdvScreen(
    onBack: () -> Unit,
    vm: PdvViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsState()

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(onClick = onBack) { Text("Voltar") }
            Button(onClick = { vm.refreshCatalog() }) { Text("Atualizar") }
        }
        if (state.loading) CircularProgressIndicator()
        state.error?.let { Text("Erro: $it", color = androidx.compose.ui.graphics.Color.Red) }
        state.message?.let { Text(it) }

        Text("Produtos", modifier = Modifier.padding(top = 8.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(state.catalog?.products.orEmpty()) { p ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(p.name)
                            Text("R$ ${"%.2f".format(p.price)} · est: ${p.stockQuantity}")
                        }
                        Button(onClick = { vm.addProduct(p.id, p.name, p.price) }) { Text("+") }
                    }
                }
            }
            item { Text("Ingressos (eventos publicados)", modifier = Modifier.padding(vertical = 8.dp)) }
            items(state.catalog?.events.orEmpty()) { ev ->
                Text(ev.name, modifier = Modifier.padding(4.dp))
                ev.ticketBatches.forEach { batch ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text(batch.name)
                                Text("R$ ${"%.2f".format(batch.price)} · disp: ${batch.available}")
                            }
                            Button(onClick = {
                                vm.addTicket(batch.id, batch.eventId, "${ev.name} - ${batch.name}", batch.price)
                            }) { Text("+") }
                        }
                    }
                }
            }
        }

        Text("Carrinho (${state.cart.size})")
        state.cart.forEach { line ->
            Text("${line.quantity}x ${line.description} R$ ${"%.2f".format(line.lineTotal)}")
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.checkout(PaymentMethodApi.CREDIT) }) { Text("Crédito") }
            Button(onClick = { vm.checkout(PaymentMethodApi.PIX) }) { Text("Pix") }
            Button(onClick = { vm.checkout(PaymentMethodApi.CASH) }) { Text("Dinheiro") }
        }
        Button(onClick = { vm.clearCart() }, modifier = Modifier.fillMaxWidth()) { Text("Limpar carrinho") }
    }
}
