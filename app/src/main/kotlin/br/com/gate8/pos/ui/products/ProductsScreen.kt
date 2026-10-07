package br.com.gate8.pos.ui.products

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import br.com.gate8.pos.BuildConfig
import br.com.gate8.pos.data.remote.dto.ProductDto
import br.com.gate8.pos.domain.model.PaymentMethodApi
import br.com.gate8.pos.domain.model.canAddMore
import br.com.gate8.pos.domain.model.isOutOfStock
import br.com.gate8.pos.domain.model.tracksStock
import br.com.gate8.pos.ui.common.Gate8CartLineUi
import br.com.gate8.pos.ui.common.Gate8CartScreenRoot
import br.com.gate8.pos.ui.common.Gate8ScreenBackground
import br.com.gate8.pos.ui.common.Gate8AlertDialog
import br.com.gate8.pos.ui.common.PaymentFailedAlert
import br.com.gate8.pos.ui.common.Gate8CartSheet
import br.com.gate8.pos.ui.common.SplitPaymentDialog
import br.com.gate8.pos.ui.common.Gate8ConfirmModal
import br.com.gate8.pos.ui.common.Gate8SuccessDialog
import br.com.gate8.pos.ui.common.PaymentWaitingOverlay
import br.com.gate8.pos.ui.common.paymentLoadingMessage
import br.com.gate8.pos.ui.common.Gate8OutlinedTextField
import br.com.gate8.pos.ui.common.Gate8QuantitySelector
import br.com.gate8.pos.ui.common.Gate8ScreenTopBar
import br.com.gate8.pos.ui.theme.Gate8Colors
import coil.compose.AsyncImage
import org.koin.androidx.compose.koinViewModel

@Composable
fun ProductsScreen(
    onBack: () -> Unit,
    vm: ProductsViewModel = koinViewModel(),
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
    val allProducts = state.catalog?.products.orEmpty()
    val categories = remember(allProducts) {
        allProducts.mapNotNull { it.category?.trim()?.takeIf { name -> name.isNotEmpty() } }
            .distinctBy { it.lowercase() }
            .sortedBy { it.lowercase() }
    }
    val products = remember(allProducts, state.searchQuery, state.selectedCategory) {
        val q = state.searchQuery.trim()
        val category = state.selectedCategory
        allProducts.filter { product ->
            val matchesCategory = category == null ||
                product.category?.trim().equals(category, ignoreCase = true)
            val matchesQuery = q.isEmpty() ||
                product.name.contains(q, ignoreCase = true) ||
                product.sku?.contains(q, ignoreCase = true) == true ||
                product.category?.contains(q, ignoreCase = true) == true ||
                product.description?.contains(q, ignoreCase = true) == true
            matchesCategory && matchesQuery
        }
    }
    LaunchedEffect(categories, state.selectedCategory) {
        val selected = state.selectedCategory ?: return@LaunchedEffect
        if (categories.none { it.equals(selected, ignoreCase = true) }) {
            vm.selectCategory(null)
        }
    }
    val cartItemCount = state.cart.sumOf { it.quantity }
    val cartTotal = state.cart.sumOf { it.lineTotal }

    PaymentWaitingOverlay(
        visible = state.loading,
        method = state.payingMethod,
        amount = cartTotal,
        onCancel = { vm.cancelPayment() },
    )

    state.saleSuccess?.let { success ->
        Gate8SuccessDialog(
            title = success.title,
            detail = success.detail,
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

    if (state.kitchenNotePrompt != null) {
        KitchenNoteDialog(
            value = state.kitchenNoteDraft,
            onValueChange = vm::updateKitchenNoteDraft,
            onConfirm = vm::confirmKitchenNote,
            onDismiss = vm::dismissKitchenNote,
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

    if (state.pendingClientCopy != null) {
        Gate8ConfirmModal(
            title = "Imprimir via do cliente?",
            message = "A via do lojista já saiu.\n\nImprimir também a via do cliente? " +
                "Em seguida saem o comprovante Gate8 e as fichas.",
            confirmLabel = "Sim, imprimir",
            dismissLabel = "Não",
            onConfirm = { vm.answerClientCopy(true) },
            onDismiss = { vm.answerClientCopy(false) },
        )
    }

    Gate8ScreenBackground {
    Gate8CartScreenRoot(
        showCart = state.showCart,
        modifier = Modifier.fillMaxSize(),
        background = {
        Column(Modifier.fillMaxSize()) {
            Gate8ScreenTopBar(
                onMenu = onBack,
                showAction = false,
            )

            Column(Modifier.padding(horizontal = 16.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Produtos",
                        color = Gate8Colors.TextPrimary,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
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

                Spacer(Modifier.height(12.dp))
                Gate8OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = vm::onSearchQueryChange,
                    label = "Buscar produto",
                    placeholder = "Buscar produto...",
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                    ),
                )
                if (categories.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CategoryChip(
                            label = "Todos",
                            selected = state.selectedCategory == null,
                            onClick = { vm.selectCategory(null) },
                        )
                        categories.forEach { category ->
                            CategoryChip(
                                label = category,
                                selected = category.equals(state.selectedCategory, ignoreCase = true),
                                onClick = { vm.selectCategory(category) },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

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

            if (state.loading && allProducts.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Gate8Colors.AccentBlue)
                }
            } else if (allProducts.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Nenhum produto no catálogo.\nCadastre itens no painel Gate8.",
                        color = Gate8Colors.TextSecondary,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            } else if (products.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (state.searchQuery.isNotBlank()) {
                            "Nenhum produto encontrado para “${state.searchQuery.trim()}”."
                        } else {
                            "Nenhum produto em ${state.selectedCategory}."
                        },
                        color = Gate8Colors.TextSecondary,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            } else {
                Box(Modifier.weight(1f)) {
                    key(state.catalogVersion) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            contentPadding = PaddingValues(
                                start = 12.dp,
                                end = 12.dp,
                                bottom = if (cartItemCount > 0) 108.dp else 16.dp,
                            ),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            items(
                                products,
                                key = { "${it.id}-${it.tracksStock}-${it.stockQuantity}-${it.price}" },
                            ) { product ->
                                val inCart = state.cart.firstOrNull { it.productId == product.id }?.quantity ?: 0
                                ProductGridCard(
                                    product = product,
                                    quantity = inCart,
                                    onIncrement = { vm.addProduct(product) },
                                    onDecrement = { vm.removeProduct(product.id) },
                                )
                            }
                        }
                    }
                    if (state.loading) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = Gate8Colors.AccentBlue)
                        }
                    }
                }
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
            val cartLines = state.cart.mapNotNull { line ->
                val productId = line.productId ?: return@mapNotNull null
                val product = allProducts.firstOrNull { it.id == productId }
                Gate8CartLineUi(
                    id = productId,
                    description = line.description,
                    quantity = line.quantity,
                    unitPrice = line.unitPrice,
                    lineTotal = line.lineTotal,
                    canIncrement = product?.let { !it.isOutOfStock && it.canAddMore(line.quantity) } ?: false,
                    imageUrl = product?.imageUrl,
                )
            }
            Gate8CartSheet(
                itemCount = cartItemCount,
                total = cartTotal,
                lines = cartLines,
                loading = state.loading,
                loadingMessage = paymentLoadingMessage(state.payingMethod),
                onBack = { vm.closeCart() },
                onIncrement = { productId ->
                    allProducts.firstOrNull { it.id == productId }?.let { vm.addProduct(it) }
                },
                onDecrement = { vm.removeProduct(it) },
                onPayDebit = { vm.checkout(PaymentMethodApi.DEBIT) },
                onPayCredit = { vm.checkout(PaymentMethodApi.CREDIT) },
                onPayPix = { vm.checkout(PaymentMethodApi.PIX) },
                onPayCash = { vm.checkout(PaymentMethodApi.CASH) },
                onPayCashless = { vm.checkout(PaymentMethodApi.CASHLESS) },
                onClear = { vm.clearCart() },
                cashEnabled = state.cashierOpen,
                showCashless = BuildConfig.FLAVOR.equals("cielo", ignoreCase = true),
                onSplitPay = { vm.openSplitPayment() },
            )
        },
    )
    }
}

@Composable
private fun ProductGridCard(
    product: ProductDto,
    quantity: Int,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
) {
    val outOfStock = product.isOutOfStock
    val canIncrement = !outOfStock && product.canAddMore(quantity)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, Gate8Colors.AccentBlue, RoundedCornerShape(12.dp))
            .background(Gate8Colors.CardSurface.copy(alpha = if (outOfStock) 0.5f else 1f))
            .clickable(enabled = canIncrement, onClick = onIncrement),
    ) {
        Box {
            if (!product.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = product.imageUrl,
                    contentDescription = product.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .background(Gate8Colors.CardSurfaceElevated),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        product.name.take(2).uppercase(),
                        color = Gate8Colors.AccentBlue,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                    )
                }
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp)
                .height(122.dp),
        ) {
            Text(
                product.name,
                color = Gate8Colors.TextPrimary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                product.category ?: "Geral",
                color = Gate8Colors.TextSecondary,
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "R$ ${"%.2f".format(product.price)}",
                color = Gate8Colors.AccentBlue,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 2.dp),
            )
            if (product.tracksStock) {
                Text(
                    if (outOfStock) "Esgotado" else "est: ${product.stockQuantity ?: 0}",
                    color = if (outOfStock) Gate8Colors.Error else Gate8Colors.TextSecondary,
                    fontSize = 8.sp,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.weight(1f))
            if (outOfStock) {
                Text(
                    "Indisponível",
                    color = Gate8Colors.Error,
                    fontSize = 9.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            } else {
                Gate8QuantitySelector(
                    quantity = quantity,
                    canIncrement = canIncrement,
                    compact = true,
                    onIncrement = onIncrement,
                    onDecrement = onDecrement,
                )
            }
        }
    }
}

@Composable
private fun CategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) Gate8Colors.AccentBlue else Gate8Colors.CardSurface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            color = if (selected) Color.White else Gate8Colors.TextPrimary,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

@Composable
private fun KitchenNoteDialog(
    value: String,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White)
                    .padding(horizontal = 22.dp, vertical = 24.dp),
            ) {
                Text(
                    "Pedido da cozinha",
                    color = Gate8Colors.TextPrimary,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Mesa ou observação. Sai na ficha da cozinha, junto com o número do pedido.",
                    color = Gate8Colors.TextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Gate8OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    label = "Mesa ou observação",
                    placeholder = "Ex.: Mesa 12, sem cebola",
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(18.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Gate8Colors.AccentBlue)
                        .clickable(onClick = onConfirm)
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Continuar",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Voltar",
                        color = Gate8Colors.TextSecondary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                    )
                }
            }
        }
    }
}
