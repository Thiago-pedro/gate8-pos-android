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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.gate8.pos.data.remote.dto.ProductDto
import br.com.gate8.pos.domain.model.CartLine
import br.com.gate8.pos.domain.model.PaymentMethodApi
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Box(
        Modifier
            .fillMaxSize()
            .background(Gate8Colors.ScreenGradient),
    ) {
        Column(Modifier.fillMaxSize()) {
            ProductsTopBar(onMenu = onBack, onRefresh = { vm.refreshCatalog() })

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
                            .background(Color.White.copy(alpha = 0.08f))
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

            if (state.loading && vm.products().isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Gate8Colors.AccentBlue)
                }
            } else if (vm.products().isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Nenhum produto no catálogo.\nCadastre itens no painel Gate8.",
                        color = Gate8Colors.TextSecondary,
                        fontSize = 14.sp,
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 100.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    items(vm.products(), key = { it.id }) { product ->
                        ProductGridCard(
                            product = product,
                            inCart = vm.quantityInCart(product.id),
                            onAdd = { vm.addProduct(product) },
                        )
                    }
                }
            }
        }

        if (vm.cartItemCount > 0) {
            FloatingActionButton(
                onClick = { vm.openCheckout() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
                    .fillMaxWidth(0.88f)
                    .height(52.dp),
                containerColor = Gate8Colors.AccentBlue,
                contentColor = Color.White,
                shape = RoundedCornerShape(26.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.ShoppingCart, contentDescription = null, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.size(10.dp))
                        Text("Ir p/ pagamentos", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }
                    Box(
                        Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            vm.cartItemCount.toString(),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
        }

        if (state.showCheckout) {
            ModalBottomSheet(
                onDismissRequest = { vm.closeCheckout() },
                sheetState = sheetState,
                containerColor = Gate8Colors.CardSurface,
            ) {
                CheckoutSheetContent(
                    itemCount = vm.cartItemCount,
                    total = vm.cartTotal,
                    cart = state.cart,
                    loading = state.loading,
                    onRemove = { vm.removeProduct(it) },
                    onPayCredit = { vm.checkout(PaymentMethodApi.CREDIT) },
                    onPayPix = { vm.checkout(PaymentMethodApi.PIX) },
                    onPayCash = { vm.checkout(PaymentMethodApi.CASH) },
                    onClear = { vm.clearCart() },
                )
            }
        }
    }
}

@Composable
private fun ProductsTopBar(onMenu: () -> Unit, onRefresh: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onMenu) {
            Icon(Icons.Filled.Menu, contentDescription = "Voltar", tint = Gate8Colors.TextPrimary)
        }
        Text(
            "gate8 tickets",
            color = Gate8Colors.TextPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        IconButton(onClick = onRefresh) {
            Icon(Icons.Filled.Search, contentDescription = "Buscar", tint = Gate8Colors.TextPrimary)
        }
    }
}

@Composable
private fun ProductGridCard(
    product: ProductDto,
    inCart: Int,
    onAdd: () -> Unit,
) {
    val outOfStock = product.stockQuantity <= 0
    Column(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Gate8Colors.CardSurface.copy(alpha = if (outOfStock) 0.5f else 1f))
            .then(
                if (outOfStock) Modifier else Modifier.clickable(onClick = onAdd),
            ),
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

            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Gate8Colors.BadgeBlue)
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            ) {
                Text(
                    product.category?.take(8)?.uppercase() ?: "ITEM",
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }

            Icon(
                Icons.Outlined.FavoriteBorder,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(16.dp),
            )
        }

        Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
            Text(
                product.name,
                color = Gate8Colors.TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 14.sp,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.LocationOn,
                    contentDescription = null,
                    tint = Gate8Colors.TextSecondary,
                    modifier = Modifier.size(11.dp),
                )
                Spacer(Modifier.size(2.dp))
                Text(
                    product.category ?: "Geral",
                    color = Gate8Colors.TextSecondary,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "R$ ${"%.2f".format(product.price)}",
                color = Gate8Colors.AccentBlue,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                if (outOfStock) "Esgotado" else "est: ${product.stockQuantity}",
                color = if (outOfStock) Gate8Colors.Error else Gate8Colors.TextSecondary,
                fontSize = 9.sp,
            )
            if (inCart > 0) {
                Text(
                    "No carrinho: $inCart",
                    color = Gate8Colors.Success,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun CheckoutSheetContent(
    itemCount: Int,
    total: Double,
    cart: List<CartLine>,
    loading: Boolean,
    onRemove: (String) -> Unit,
    onPayCredit: () -> Unit,
    onPayPix: () -> Unit,
    onPayCash: () -> Unit,
    onClear: () -> Unit,
) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp).padding(bottom = 32.dp)) {
        Text(
            "Pagamento",
            color = Gate8Colors.TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "$itemCount item(ns) · Total R$ ${"%.2f".format(total)}",
            color = Gate8Colors.TextSecondary,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        cart.forEach { line ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        line.description,
                        color = Gate8Colors.TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        "${line.quantity}x R$ ${"%.2f".format(line.unitPrice)} = R$ ${"%.2f".format(line.lineTotal)}",
                        color = Gate8Colors.TextSecondary,
                        fontSize = 12.sp,
                    )
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Gate8Colors.CardSurfaceElevated)
                        .clickable { line.productId?.let(onRemove) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text("−", color = Gate8Colors.TextPrimary, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        if (loading) {
            CircularProgressIndicator(color = Gate8Colors.AccentBlue)
        } else {
            PaymentButton("Crédito", onPayCredit)
            Spacer(Modifier.height(8.dp))
            PaymentButton("Pix", onPayPix)
            Spacer(Modifier.height(8.dp))
            PaymentButton("Dinheiro", onPayCash)
            Spacer(Modifier.height(12.dp))
            Text(
                "Limpar carrinho",
                color = Gate8Colors.TextSecondary,
                modifier = Modifier
                    .clickable(onClick = onClear)
                    .padding(8.dp),
            )
        }
    }
}

@Composable
private fun PaymentButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Gate8Colors.AccentBlue)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}
