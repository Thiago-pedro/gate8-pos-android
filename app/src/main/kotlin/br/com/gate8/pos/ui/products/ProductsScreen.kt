package br.com.gate8.pos.ui.products

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.gate8.pos.data.remote.dto.ProductDto
import br.com.gate8.pos.domain.model.PaymentMethodApi
import br.com.gate8.pos.domain.model.canAddMore
import br.com.gate8.pos.domain.model.isOutOfStock
import br.com.gate8.pos.domain.model.tracksStock
import br.com.gate8.pos.ui.common.Gate8CartLineUi
import br.com.gate8.pos.ui.common.Gate8CartScreenRoot
import br.com.gate8.pos.ui.common.Gate8ScreenBackground
import br.com.gate8.pos.ui.common.Gate8CartSheet
import br.com.gate8.pos.ui.common.Gate8QuantitySelector
import br.com.gate8.pos.ui.common.Gate8ScreenTopBar
import br.com.gate8.pos.ui.theme.Gate8Colors
import coil.compose.AsyncImage
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
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
    val products = state.catalog?.products.orEmpty()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val cartItemCount = state.cart.sumOf { it.quantity }
    val cartTotal = state.cart.sumOf { it.lineTotal }

    Gate8ScreenBackground {
    Gate8CartScreenRoot(
        showCart = state.showCart,
        modifier = Modifier.fillMaxSize(),
        background = {
        Column(Modifier.fillMaxSize()) {
            Gate8ScreenTopBar(onMenu = onBack, onAction = { vm.refreshCatalog() })

            Column(Modifier.padding(horizontal = 16.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Produtos",
                            color = Gate8Colors.TextPrimary,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            "Itens cadastrados no Gate8",
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

            if (state.loading && products.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Gate8Colors.AccentBlue)
                }
            } else if (products.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Nenhum produto no catálogo.\nCadastre itens no painel Gate8.",
                        color = Gate8Colors.TextSecondary,
                        fontSize = 14.sp,
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
                val product = products.firstOrNull { it.id == productId }
                Gate8CartLineUi(
                    id = productId,
                    description = line.description,
                    quantity = line.quantity,
                    unitPrice = line.unitPrice,
                    lineTotal = line.lineTotal,
                    canIncrement = product?.let { !it.isOutOfStock && it.canAddMore(line.quantity) } ?: false,
                )
            }
            ModalBottomSheet(
                onDismissRequest = { vm.closeCart() },
                sheetState = sheetState,
                containerColor = Color.Transparent,
            ) {
                Gate8CartSheet(
                    itemCount = cartItemCount,
                    total = cartTotal,
                    lines = cartLines,
                    loading = state.loading,
                    onIncrement = { productId ->
                        products.firstOrNull { it.id == productId }?.let { vm.addProduct(it) }
                    },
                    onDecrement = { vm.removeProduct(it) },
                    onPayDebit = { vm.checkout(PaymentMethodApi.DEBIT) },
                    onPayCredit = { vm.checkout(PaymentMethodApi.CREDIT) },
                    onPayPix = { vm.checkout(PaymentMethodApi.PIX) },
                    onPayCash = { vm.checkout(PaymentMethodApi.CASH) },
                    onClear = { vm.clearCart() },
                    cashEnabled = state.cashierOpen,
                )
            }
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
                .height(104.dp),
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
