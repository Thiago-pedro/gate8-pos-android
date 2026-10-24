package br.com.gate8.pos.ui.products

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
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
import br.com.gate8.pos.ui.common.Gate8CartBar
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
import br.com.gate8.pos.ui.common.Gate8ScreenTopBar
import br.com.gate8.pos.ui.theme.Gate8Colors
import coil.compose.AsyncImage
import java.util.Locale
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
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF3D7CFF))
                            .clickable { vm.refreshCatalog() }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Outlined.Refresh,
                                contentDescription = "Atualizar",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.size(6.dp))
                            Text("Atualizar", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                ProductSearchField(
                    value = state.searchQuery,
                    onValueChange = vm::onSearchQueryChange,
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
                        color = Color(0xFFD6E4FF),
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
                        color = Color(0xFFD6E4FF),
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
            } else {
                Box(Modifier.weight(1f)) {
                    key(state.catalogVersion) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                bottom = if (cartItemCount > 0) 124.dp else 16.dp,
                            ),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
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
                                    modifier = Modifier.fillMaxHeight(),
                                )
                            }
                        }
                    }
                    if (state.loading && (state.payingMethod != null || state.split != null)) {
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
            Gate8CartBar(
                itemCount = cartItemCount,
                total = cartTotal,
                itemSingular = "Produto",
                onOpenCart = { vm.openCart() },
                solid = true,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(start = 12.dp, end = 12.dp, bottom = 16.dp),
            )
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

private val ProductCardShape = RoundedCornerShape(18.dp)
private val ProductImageShape = RoundedCornerShape(14.dp)
private val brLocale = Locale("pt", "BR")

@Composable
private fun ProductGridCard(
    product: ProductDto,
    quantity: Int,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val outOfStock = product.isOutOfStock
    val canIncrement = !outOfStock && product.canAddMore(quantity)

    Column(
        modifier
            .fillMaxWidth()
            .clip(ProductCardShape)
            .background(Color(0xFF0D2046).copy(alpha = if (outOfStock) 0.62f else 1f))
            .border(1.dp, Color(0xFF0000FE).copy(alpha = 0.55f), ProductCardShape)
            .clickable(enabled = canIncrement, onClick = onIncrement)
            .padding(8.dp),
    ) {
        if (!product.imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = product.imageUrl,
                contentDescription = product.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.35f)
                    .clip(ProductImageShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.35f)
                    .clip(ProductImageShape)
                    .background(Color(0xFF132A58)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Image,
                    contentDescription = null,
                    tint = Color(0xFF7EE7FF),
                    modifier = Modifier.size(36.dp),
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            product.name,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 18.sp,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            product.category?.takeIf { it.isNotBlank() } ?: "Geral",
            color = Color(0xFF9AD4FF),
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
        Text(
            String.format(brLocale, "R$ %,.2f", product.price),
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            modifier = Modifier.padding(top = 4.dp),
        )
        Spacer(Modifier.height(10.dp))
        if (outOfStock) {
            Text(
                "Esgotado",
                color = Gate8Colors.Error,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                textAlign = TextAlign.Center,
            )
        } else {
            ProductQuantityRow(
                quantity = quantity,
                canIncrement = canIncrement,
                onIncrement = onIncrement,
                onDecrement = onDecrement,
            )
        }
    }
}

@Composable
private fun ProductQuantityRow(
    quantity: Int,
    canIncrement: Boolean,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProductQtyButton(
            enabled = quantity > 0,
            filled = false,
            onClick = onDecrement,
        ) {
            Icon(
                Icons.Filled.Remove,
                contentDescription = "Remover",
                modifier = Modifier.size(16.dp),
                tint = if (quantity > 0) Color.White else Color.White.copy(alpha = 0.35f),
            )
        }
        Text(
            quantity.toString(),
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(36.dp),
            textAlign = TextAlign.Center,
        )
        ProductQtyButton(
            enabled = canIncrement,
            filled = true,
            onClick = onIncrement,
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = "Adicionar",
                modifier = Modifier.size(16.dp),
                tint = if (canIncrement) Color.White else Color.White.copy(alpha = 0.35f),
            )
        }
    }
}

@Composable
private fun ProductQtyButton(
    enabled: Boolean,
    filled: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val glow = filled && enabled
    Box(
        Modifier
            .size(36.dp)
            .drawBehind {
                if (!glow) return@drawBehind
                drawCircle(
                    color = Color(0xFF0000FE).copy(alpha = 0.45f),
                    radius = size.minDimension / 2f + 3.dp.toPx(),
                )
            }
            .clip(CircleShape)
            .background(
                when {
                    !enabled -> Color.White.copy(alpha = 0.12f)
                    filled -> Color(0xFF0000FE)
                    else -> Color(0xFF1A3A78)
                },
            )
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun ProductSearchField(
    value: String,
    onValueChange: (String) -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(shape)
            .background(Color(0xCC0B1E42))
            .border(1.dp, Color(0xFF0000FE).copy(alpha = 0.75f), shape)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Search,
            contentDescription = null,
            tint = Color(0xFF9AD4FF),
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            cursorBrush = SolidColor(Color.White),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            "Buscar produto",
                            color = Color(0xFF9AD4FF).copy(alpha = 0.85f),
                            fontSize = 15.sp,
                        )
                    }
                    inner()
                }
            },
        )
    }
}

@Composable
private fun CategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        Modifier
            .clip(shape)
            .background(if (selected) Color(0xFF0000FE) else Color(0x660B1E42))
            .border(1.dp, Color(0xFF0000FE).copy(alpha = if (selected) 1f else 0.75f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        Text(
            label,
            color = Color.White,
            fontSize = 12.sp,
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
            val shape = RoundedCornerShape(22.dp)
            val buttonShape = RoundedCornerShape(16.dp)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(Color(0xFF0C1E4A))
                    .border(1.5.dp, Color(0xFF0000FE).copy(alpha = 0.9f), shape)
                    .padding(horizontal = 22.dp, vertical = 24.dp),
            ) {
                Text(
                    "Pedido da cozinha",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Mesa ou observação. Sai na ficha da cozinha, junto com o número do pedido.",
                    color = Color.White,
                    fontSize = 15.sp,
                    lineHeight = 21.sp,
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
                    onDark = true,
                )
                Spacer(Modifier.height(18.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .border(1.5.dp, Color(0xFF6EA8FF), buttonShape)
                        .clip(buttonShape)
                        .background(Color(0xFF1A46FF))
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
                        .clip(buttonShape)
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Voltar",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                    )
                }
            }
        }
    }
}
