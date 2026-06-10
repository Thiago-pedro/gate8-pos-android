package br.com.gate8.pos.ui.pdv

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.gate8.pos.data.remote.dto.EventCatalogDto
import br.com.gate8.pos.domain.model.CartLine
import br.com.gate8.pos.domain.model.PaymentMethodApi
import coil.compose.AsyncImage
import org.koin.androidx.compose.koinViewModel

@Composable
fun PdvScreen(
    onBack: () -> Unit,
    vm: PdvViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsState()
    val selectedEvent = vm.selectedEvent()

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        PdvTopBar(
            onBack = {
                if (selectedEvent != null) vm.clearSelectedEvent() else onBack()
            },
            onRefresh = { vm.refreshCatalog() },
            backLabel = if (selectedEvent != null) "Eventos" else "Voltar",
        )

        if (state.loading) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        state.error?.let { Text("Erro: $it", color = Color.Red, modifier = Modifier.padding(vertical = 4.dp)) }
        state.message?.let { Text(it, modifier = Modifier.padding(vertical = 4.dp)) }

        if (selectedEvent == null) {
            PdvEventPicker(
                events = state.catalog?.events.orEmpty(),
                onSelect = { vm.selectEvent(it) },
            )
        } else {
            PdvEventSales(
                event = selectedEvent,
                onAddTicket = { batchId, eventId, name, price ->
                    vm.addTicket(batchId, eventId, name, price)
                },
            )
        }

        PdvCartFooter(
            cartSize = state.cart.size,
            cart = state.cart,
            onCheckoutCredit = { vm.checkout(PaymentMethodApi.CREDIT) },
            onCheckoutPix = { vm.checkout(PaymentMethodApi.PIX) },
            onCheckoutCash = { vm.checkout(PaymentMethodApi.CASH) },
            onClearCart = { vm.clearCart() },
        )
    }
}

@Composable
private fun PdvTopBar(
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    backLabel: String,
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Button(onClick = onBack) { Text(backLabel) }
        Button(onClick = onRefresh) { Text("Atualizar") }
    }
}

@Composable
private fun PdvEventPicker(
    events: List<EventCatalogDto>,
    onSelect: (String) -> Unit,
) {
    Text(
        "Escolha o evento",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(vertical = 8.dp),
    )

    if (events.isEmpty()) {
        Text(
            "Nenhum evento publicado no catálogo.",
            modifier = Modifier.padding(16.dp),
        )
        return
    }

    LazyColumn(
        Modifier
            .weight(1f)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(events, key = { it.id }) { event ->
            EventPickerCard(event = event, onClick = { onSelect(event.id) })
        }
    }
}

@Composable
private fun EventPickerCard(
    event: EventCatalogDto,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column {
            if (!event.bannerUrl.isNullOrBlank()) {
                AsyncImage(
                    model = event.bannerUrl,
                    contentDescription = event.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        event.name.take(1).uppercase(),
                        style = MaterialTheme.typography.displayMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Column(Modifier.padding(12.dp)) {
                Text(event.name, style = MaterialTheme.typography.titleMedium)
                event.eventDate?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                event.location?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    "${event.ticketBatches.size} opção(ões) de ingresso",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun PdvEventSales(
    event: EventCatalogDto,
    onAddTicket: (String, String, String, Double) -> Unit,
) {
    Text(
        event.name,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(vertical = 8.dp),
    )

    LazyColumn(
        Modifier
            .weight(1f)
            .fillMaxWidth(),
    ) {
        item { Text("Ingressos", modifier = Modifier.padding(vertical = 8.dp)) }

        if (event.ticketBatches.isEmpty()) {
            item { Text("Nenhum lote disponível para este evento.") }
        } else {
            items(event.ticketBatches, key = { it.id }) { batch ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(batch.name)
                            Text("R$ ${"%.2f".format(batch.price)} · disp: ${batch.available}")
                        }
                        Button(onClick = {
                            onAddTicket(batch.id, batch.eventId, "${event.name} - ${batch.name}", batch.price)
                        }) { Text("+") }
                    }
                }
            }
        }
    }
}

@Composable
private fun PdvCartFooter(
    cartSize: Int,
    cart: List<CartLine>,
    onCheckoutCredit: () -> Unit,
    onCheckoutPix: () -> Unit,
    onCheckoutCash: () -> Unit,
    onClearCart: () -> Unit,
) {
    Text("Carrinho ($cartSize)", modifier = Modifier.padding(top = 8.dp))
    cart.forEach { line ->
        Text("${line.quantity}x ${line.description} R$ ${"%.2f".format(line.lineTotal)}")
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onCheckoutCredit) { Text("Crédito") }
        Button(onClick = onCheckoutPix) { Text("Pix") }
        Button(onClick = onCheckoutCash) { Text("Dinheiro") }
    }
    Button(onClick = onClearCart, modifier = Modifier.fillMaxWidth()) { Text("Limpar carrinho") }
}
