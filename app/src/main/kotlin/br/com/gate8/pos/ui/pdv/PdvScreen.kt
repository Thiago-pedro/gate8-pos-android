package br.com.gate8.pos.ui.pdv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.gate8.pos.data.remote.dto.EventCatalogDto
import br.com.gate8.pos.data.remote.dto.TicketBatchDto
import br.com.gate8.pos.data.remote.dto.canAdd
import br.com.gate8.pos.data.remote.dto.isSoldOut
import br.com.gate8.pos.domain.model.CartLine
import br.com.gate8.pos.domain.model.PaymentMethodApi
import br.com.gate8.pos.ui.common.Gate8AlertDialog
import br.com.gate8.pos.ui.common.PaymentFailedAlert
import br.com.gate8.pos.ui.common.Gate8CartLineUi
import br.com.gate8.pos.ui.common.Gate8CartScreenRoot
import br.com.gate8.pos.ui.common.Gate8ScreenBackground
import br.com.gate8.pos.ui.common.Gate8CartSheet
import br.com.gate8.pos.ui.common.Gate8ConfirmModal
import br.com.gate8.pos.ui.common.Gate8SuccessDialog
import br.com.gate8.pos.ui.common.PaymentWaitingOverlay
import br.com.gate8.pos.ui.common.paymentLoadingMessage
import br.com.gate8.pos.ui.common.Gate8QuantitySelector
import br.com.gate8.pos.ui.common.Gate8ScreenTopBar
import br.com.gate8.pos.ui.common.SplitPaymentDialog
import br.com.gate8.pos.ui.theme.Gate8Colors
import coil.compose.AsyncImage
import coil.request.ImageRequest
import org.koin.androidx.compose.koinViewModel
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun PdvScreen(
    onBack: () -> Unit,
    vm: PdvViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.onScreenVisible()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val selectedEvent = state.selectedEventId?.let { id ->
        state.catalog?.events?.firstOrNull { it.id == id }
    }
    val events = state.catalog?.events.orEmpty()
    val cartItemCount = state.cart.sumOf { it.quantity }
    val cartTotal = state.cart.sumOf { it.lineTotal }

    PaymentWaitingOverlay(
        visible = state.loading,
        method = state.payingMethod,
        amount = cartTotal,
        onCancel = { vm.cancelPayment() },
    )

    if (state.pendingClientCopy != null) {
        Gate8ConfirmModal(
            title = "Imprimir via do cliente?",
            message = "A via do lojista já saiu.\n\nImprimir também a via do cliente? Em seguida sai o ingresso.",
            confirmLabel = "Sim, imprimir",
            dismissLabel = "Não",
            onConfirm = { vm.answerClientCopy(true) },
            onDismiss = { vm.answerClientCopy(false) },
        )
    }

    state.saleSuccessMessage?.let { msg ->
        Gate8SuccessDialog(
            title = msg,
            onDismiss = { vm.dismissSaleSuccess() },
        )
    }

    if (state.pixExpired) {
        Gate8AlertDialog(
            title = "QR Code expirado",
            reason = "O tempo para pagar o Pix acabou.",
            detail = "Gere um novo QR Code para tentar novamente.",
            onDismiss = { vm.dismissPixExpired() },
        )
    }

    if (state.paymentCancelled) {
        Gate8AlertDialog(
            title = "Pagamento não concluído",
            reason = "A cobrança não foi finalizada na maquininha.",
            detail = "Os itens continuam no carrinho. Tente novamente.",
            icon = Icons.Filled.Cancel,
            accent = Gate8Colors.AccentBlue,
            onDismiss = { vm.dismissPaymentCancelled() },
        )
    }

    if (state.paymentFailed) {
        PaymentFailedAlert(
            reason = state.paymentFailedReason,
            onDismiss = { vm.dismissPaymentFailed() },
        )
    }

    val split = state.split
    if (split != null && !state.loading) {
        SplitPaymentDialog(
            ui = split,
            cashEnabled = state.cashierOpen,
            onAdd = vm::splitAddPart,
            onPick = vm::splitPickMethod,
            onAmountChange = vm::splitAmountChange,
            onConfirmAmount = vm::confirmSplitAmount,
            onBack = vm::splitBack,
            onRequestCancel = vm::requestCancelSplit,
            onConfirmCancel = vm::confirmCancelSplit,
            onDismissCancel = vm::dismissCancelSplit,
            onDismissReport = vm::dismissSplitReport,
        )
    }

    if (state.splitNotice != null) {
        Gate8AlertDialog(
            title = "Pagamento dividido",
            reason = "A venda não foi concluída.",
            detail = state.splitNotice,
            accent = Gate8Colors.AccentBlue,
            onDismiss = { vm.dismissSplitNotice() },
        )
    }

    Gate8ScreenBackground {
    Gate8CartScreenRoot(
        showCart = state.showCart,
        modifier = Modifier.fillMaxSize(),
        background = {
        Column(Modifier.fillMaxSize()) {
            Gate8ScreenTopBar(
                onMenu = {
                    if (selectedEvent != null) vm.clearSelectedEvent() else onBack()
                },
                onAction = { vm.refreshCatalog() },
            )

            Column(Modifier.padding(horizontal = 16.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (selectedEvent != null) selectedEvent.name else "Ingressos",
                            color = Gate8Colors.TextPrimary,
                            fontSize = if (selectedEvent != null) 22.sp else 28.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            if (selectedEvent != null) {
                                "Escolha os lotes e quantidades"
                            } else {
                                "Eventos publicados"
                            },
                            color = Gate8Colors.TextSecondary,
                            fontSize = 14.sp,
                        )
                    }
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Gate8Colors.CardSurface)
                            .clickable { vm.refreshCatalog() }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.Tune,
                                contentDescription = "Atualizar",
                                tint = Gate8Colors.TextPrimary,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.size(6.dp))
                            Text("Atualizar", color = Gate8Colors.TextPrimary, fontSize = 13.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            state.error?.let {
                Text(
                    it,
                    color = Gate8Colors.Error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    fontSize = 13.sp,
                )
            }
            state.message?.let {
                Text(
                    it,
                    color = Gate8Colors.Success,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    fontSize = 13.sp,
                )
            }

            if (state.loading && state.catalog == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Gate8Colors.AccentBlue)
                }
            } else if (selectedEvent == null) {
                key(state.catalogVersion) {
                    PdvEventPicker(
                        modifier = Modifier.weight(1f),
                        events = events,
                        onSelect = { vm.selectEvent(it) },
                    )
                }
            } else {
                PdvTicketList(
                    modifier = Modifier.weight(1f),
                    event = selectedEvent,
                    cart = state.cart,
                    onIncrement = { batch -> vm.addTicket(batch, selectedEvent.name) },
                    onDecrement = { vm.removeTicket(it.id) },
                )
            }
        }

        if (cartItemCount > 0) {
            FloatingActionButton(
                onClick = { vm.openCart() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
                    .fillMaxWidth(0.9f)
                    .height(56.dp),
                containerColor = Gate8Colors.AccentBlue,
                contentColor = Color.White,
                shape = RoundedCornerShape(28.dp),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            Icons.Filled.ShoppingCart,
                            contentDescription = "Carrinho",
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                "Ver carrinho",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                            )
                            Text(
                                "R$ ${"%.2f".format(cartTotal)}",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.85f),
                            )
                        }
                    }
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            cartItemCount.toString(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
        }
        },
        sheet = {
            val batches = selectedEvent?.ticketBatches.orEmpty()
            val cartLines = state.cart.mapNotNull { line ->
                val batchId = line.batchId ?: return@mapNotNull null
                val batch = batches.firstOrNull { it.id == batchId }
                Gate8CartLineUi(
                    id = batchId,
                    description = line.description,
                    quantity = line.quantity,
                    unitPrice = line.unitPrice,
                    lineTotal = line.lineTotal,
                    canIncrement = batch?.canAdd(line.quantity) ?: false,
                    imageUrl = selectedEvent?.bannerUrl,
                )
            }
            Gate8CartSheet(
                itemCount = cartItemCount,
                total = cartTotal,
                lines = cartLines,
                loading = state.loading,
                loadingMessage = paymentLoadingMessage(state.payingMethod),
                onBack = { vm.closeCart() },
                onIncrement = { batchId ->
                    selectedEvent?.ticketBatches
                        ?.firstOrNull { it.id == batchId }
                        ?.let { vm.addTicket(it, selectedEvent.name) }
                },
                onDecrement = { vm.removeTicket(it) },
                onPayDebit = { vm.checkout(PaymentMethodApi.DEBIT) },
                onPayCredit = { vm.checkout(PaymentMethodApi.CREDIT) },
                onPayPix = { vm.checkout(PaymentMethodApi.PIX) },
                onPayCash = { vm.checkout(PaymentMethodApi.CASH) },
                onClear = { vm.clearCart() },
                cashEnabled = state.cashierOpen,
                onSplitPay = { vm.openSplitPayment() },
            )
        },
    )
    }
}

@Composable
private fun PdvEventPicker(
    modifier: Modifier = Modifier,
    events: List<EventCatalogDto>,
    onSelect: (String) -> Unit,
) {
    if (events.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Nenhum evento publicado no catálogo.",
                color = Gate8Colors.TextSecondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(16.dp),
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 100.dp),
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
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.5.dp, Gate8Colors.AccentBlue, shape)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.32f))
            .clickable(onClick = onClick),
    ) {
        if (!event.bannerUrl.isNullOrBlank()) {
            val context = LocalContext.current
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(event.bannerUrl)
                    .size(800, 450)
                    .crossfade(true)
                    .build(),
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
                    .aspectRatio(16f / 9f)
                    .background(Gate8Colors.AccentBlue.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    event.name.take(1).uppercase(),
                    color = Gate8Colors.AccentBlue,
                    fontWeight = FontWeight.Bold,
                    fontSize = 36.sp,
                )
            }
        }
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                event.name,
                color = Gate8Colors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            event.eventDate?.let {
                EventMetaRow(
                    icon = Icons.Outlined.CalendarMonth,
                    text = formatEventDate(it),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            event.location?.takeIf { it.isNotBlank() }?.let {
                EventMetaRow(
                    icon = Icons.Outlined.LocationOn,
                    text = it,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(top = 12.dp, bottom = 10.dp),
                color = Gate8Colors.TextSecondary.copy(alpha = 0.18f),
            )
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${event.ticketBatches.size} opção(ões) de ingresso",
                    color = Gate8Colors.AccentBlue,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Gate8Colors.TextSecondary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun EventMetaRow(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Gate8Colors.TextSecondary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text,
            color = Gate8Colors.TextSecondary,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PdvTicketList(
    modifier: Modifier = Modifier,
    event: EventCatalogDto,
    cart: List<CartLine>,
    onIncrement: (TicketBatchDto) -> Unit,
    onDecrement: (TicketBatchDto) -> Unit,
) {
    if (event.ticketBatches.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Nenhum lote disponível para este evento.",
                color = Gate8Colors.TextSecondary,
                fontSize = 14.sp,
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(event.ticketBatches, key = { it.id }) { batch ->
            val inCart = cart.firstOrNull { it.batchId == batch.id }?.quantity ?: 0
            TicketBatchCard(
                batch = batch,
                quantity = inCart,
                onIncrement = { onIncrement(batch) },
                onDecrement = { onDecrement(batch) },
            )
        }
    }
}

@Composable
private fun TicketBatchCard(
    batch: TicketBatchDto,
    quantity: Int,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
) {
    val soldOut = batch.isSoldOut
    val canIncrement = batch.canAdd(quantity)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Gate8Colors.CardSurface.copy(alpha = if (soldOut) 0.55f else 1f))
            .border(1.dp, Gate8Colors.AccentBlue, RoundedCornerShape(12.dp))
            .clickable(enabled = canIncrement, onClick = onIncrement)
            .padding(12.dp),
    ) {
        Text(
            batch.name,
            color = Gate8Colors.TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        batch.sector?.let {
            Text(
                it,
                color = Gate8Colors.TextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    "R$ ${"%.2f".format(batch.price)}",
                    color = Gate8Colors.AccentBlue,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (soldOut) {
                    Text(
                        "Esgotado",
                        color = Gate8Colors.Error,
                        fontSize = 11.sp,
                    )
                }
            }
            if (soldOut) {
                Text(
                    "Indisponível",
                    color = Gate8Colors.Error,
                    fontSize = 12.sp,
                )
            } else {
                Gate8QuantitySelector(
                    quantity = quantity,
                    canIncrement = canIncrement,
                    compact = false,
                    onIncrement = onIncrement,
                    onDecrement = onDecrement,
                )
            }
        }
    }
}

/** Converte ISO da API (UTC com Z) para horário de Brasília — não usar o HH:mm cru do UTC. */
private fun formatEventDate(iso: String): String {
    return runCatching {
        OffsetDateTime.parse(iso).atZoneSameInstant(eventZone).format(eventDateFmt)
    }.recoverCatching {
        LocalDateTime.parse(iso.substringBefore('.').removeSuffix("Z")).format(eventDateFmt)
    }.recoverCatching {
        LocalDate.parse(iso.substringBefore('T')).format(eventDateOnlyFmt)
    }.getOrDefault(iso)
}

private val brLocale = Locale("pt", "BR")
private val eventZone: ZoneId by lazy {
    runCatching { ZoneId.of("America/Sao_Paulo") }
        .getOrElse { ZoneOffset.of("-03:00") }
}
private val eventDateFmt by lazy {
    DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm", brLocale)
}
private val eventDateOnlyFmt by lazy {
    DateTimeFormatter.ofPattern("dd/MM/yyyy", brLocale)
}
