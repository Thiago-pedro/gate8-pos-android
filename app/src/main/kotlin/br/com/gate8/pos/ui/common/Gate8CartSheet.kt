package br.com.gate8.pos.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.gate8.pos.BuildConfig
import br.com.gate8.pos.R
import br.com.gate8.pos.domain.model.PaymentMethodApi
import br.com.gate8.pos.ui.theme.Gate8Colors
import coil.compose.AsyncImage
import java.util.Locale

/**
 * Mensagem exibida sob o spinner enquanto o pagamento processa, conforme a forma escolhida.
 * Retorna null quando não há mensagem específica (ex.: dinheiro, flavor Cielo — UI nativa).
 */
fun paymentLoadingMessage(method: PaymentMethodApi?): String? {
    if (method == PaymentMethodApi.CASHLESS) {
        return "Aproxime o cartão cashless na maquininha"
    }
    if (BuildConfig.FLAVOR.equals("cielo", ignoreCase = true)) return null
    return when (method) {
        PaymentMethodApi.DEBIT, PaymentMethodApi.CREDIT ->
            "Aproxime, insira ou passe o cartão na parte superior da maquininha"
        PaymentMethodApi.PIX -> "Gerando o QR Code Pix..."
        else -> null
    }
}

data class Gate8CartLineUi(
    val id: String,
    val description: String,
    val quantity: Int,
    val unitPrice: Double,
    val lineTotal: Double,
    val canIncrement: Boolean,
    val imageUrl: String? = null,
)

private enum class PayTileKind {
    Debit,
    Credit,
    Pix,
    Cash,
    Cashless,
    Split,
}

@Composable
fun Gate8CartSheet(
    itemCount: Int,
    total: Double,
    lines: List<Gate8CartLineUi>,
    loading: Boolean,
    loadingMessage: String? = null,
    onBack: () -> Unit,
    onIncrement: (String) -> Unit,
    onDecrement: (String) -> Unit,
    onPayDebit: () -> Unit,
    onPayCredit: () -> Unit,
    onPayPix: () -> Unit,
    onPayCash: () -> Unit,
    onPayCashless: (() -> Unit)? = null,
    onSplitPay: (() -> Unit)? = null,
    onClear: () -> Unit,
    cashEnabled: Boolean = true,
    showCashless: Boolean = false,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 12.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Voltar",
                    tint = Gate8Colors.TextPrimary,
                )
            }
            Text(
                "Carrinho",
                color = Gate8Colors.TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Row(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = !loading, onClick = onClear)
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Limpar",
                    tint = Gate8Colors.TextPrimary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "Limpar",
                    color = Gate8Colors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        Row(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "$itemCount item(ns) · Total ",
                color = Gate8Colors.TextPrimary,
                fontSize = 16.sp,
            )
            Text(
                brMoney(total),
                color = Gate8Colors.AccentBlue,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        if (lines.isEmpty()) {
            Text(
                "Nenhum item no carrinho.",
                color = Gate8Colors.TextSecondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
            )
        } else {
            Column(
                Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                lines.forEach { line ->
                    CartLineCard(
                        line = line,
                        enabled = !loading,
                        onIncrement = { onIncrement(line.id) },
                        onDecrement = { onDecrement(line.id) },
                    )
                }
            }
        }

        HorizontalDivider(
            Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
            color = Gate8Colors.CardSurfaceElevated,
        )

        Text(
            "Como o cliente vai pagar?",
            color = Gate8Colors.TextPrimary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(14.dp))

        if (loading) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(color = Gate8Colors.AccentBlue)
                if (!loadingMessage.isNullOrBlank()) {
                    Spacer(Modifier.height(18.dp))
                    Text(
                        loadingMessage,
                        color = Gate8Colors.TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                    )
                }
            }
        } else {
            PaymentGrid(
                cashEnabled = cashEnabled,
                showCashless = showCashless && onPayCashless != null,
                showSplit = onSplitPay != null,
                onPayDebit = onPayDebit,
                onPayCredit = onPayCredit,
                onPayPix = onPayPix,
                onPayCash = onPayCash,
                onPayCashless = { onPayCashless?.invoke() },
                onSplitPay = { onSplitPay?.invoke() },
            )
        }
    }
}

@Composable
private fun CartLineCard(
    line: Gate8CartLineUi,
    enabled: Boolean,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, Gate8Colors.CardSurfaceElevated, RoundedCornerShape(16.dp))
            .background(Color.White)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!line.imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = line.imageUrl,
                contentDescription = line.description,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(12.dp)),
            )
        } else {
            Box(
                Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Gate8Colors.CardSurface),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                line.description,
                color = Gate8Colors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${line.quantity}x ${brMoney(line.unitPrice)}",
                color = Gate8Colors.TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
            Text(
                "Subtotal ${brMoney(line.lineTotal)}",
                color = Gate8Colors.AccentBlue,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        CartQuantity(
            quantity = line.quantity,
            canIncrement = enabled && line.canIncrement,
            canDecrement = enabled && line.quantity > 0,
            onIncrement = onIncrement,
            onDecrement = onDecrement,
        )
    }
}

@Composable
private fun CartQuantity(
    quantity: Int,
    canIncrement: Boolean,
    canDecrement: Boolean,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Gate8Colors.CardSurfaceElevated)
                .clickable(enabled = canDecrement, onClick = onDecrement),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Remove,
                contentDescription = "Diminuir",
                tint = Gate8Colors.TextSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            quantity.toString(),
            color = Gate8Colors.TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 10.dp),
        )
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(if (canIncrement) Gate8Colors.AccentBlue else Gate8Colors.CardSurfaceElevated)
                .clickable(enabled = canIncrement, onClick = onIncrement),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = "Aumentar",
                tint = if (canIncrement) Color.White else Gate8Colors.TextSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun PaymentGrid(
    cashEnabled: Boolean,
    showCashless: Boolean,
    showSplit: Boolean,
    onPayDebit: () -> Unit,
    onPayCredit: () -> Unit,
    onPayPix: () -> Unit,
    onPayCash: () -> Unit,
    onPayCashless: () -> Unit,
    onSplitPay: () -> Unit,
) {
    val tiles = buildList {
        add(PayTile("Débito", PayTileKind.Debit, true, onPayDebit))
        add(PayTile("Crédito", PayTileKind.Credit, true, onPayCredit))
        add(PayTile("Pix", PayTileKind.Pix, true, onPayPix))
        add(PayTile("Dinheiro", PayTileKind.Cash, cashEnabled, onPayCash))
        if (showCashless) add(PayTile("Cashless", PayTileKind.Cashless, true, onPayCashless))
        if (showSplit) add(PayTile("Dividir\npagamento", PayTileKind.Split, true, onSplitPay))
    }
    Column(
        Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        tiles.chunked(2).forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.forEach { tile ->
                    PayTileButton(tile, Modifier.weight(1f))
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

private data class PayTile(
    val label: String,
    val kind: PayTileKind,
    val enabled: Boolean,
    val onClick: () -> Unit,
)

@Composable
private fun PayTileButton(tile: PayTile, modifier: Modifier) {
    val outlined = tile.kind == PayTileKind.Split
    val background = when {
        !tile.enabled -> Gate8Colors.CardSurfaceElevated
        outlined -> Color.White
        else -> Gate8Colors.AccentBlue
    }
    val content = when {
        !tile.enabled -> Gate8Colors.TextSecondary
        outlined -> Gate8Colors.AccentBlue
        else -> Color.White
    }
    Column(
        modifier
            .height(112.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .then(
                if (outlined && tile.enabled) {
                    Modifier.border(1.5.dp, Gate8Colors.AccentBlue, RoundedCornerShape(16.dp))
                } else {
                    Modifier
                },
            )
            .clickable(enabled = tile.enabled, onClick = tile.onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        PayGlyph(tile.kind, content, enabled = tile.enabled)
        Spacer(Modifier.height(8.dp))
        Text(
            tile.label,
            color = content,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp,
        )
    }
}

@Composable
private fun PayGlyph(kind: PayTileKind, tint: Color, enabled: Boolean) {
    if (kind == PayTileKind.Pix || kind == PayTileKind.Cash) {
        Image(
            painter = painterResource(
                if (kind == PayTileKind.Pix) R.drawable.ic_pay_pix else R.drawable.ic_pay_cash,
            ),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .height(40.dp)
                .width(if (kind == PayTileKind.Cash) 56.dp else 40.dp)
                .alpha(if (enabled) 1f else 0.4f),
        )
        return
    }
    Canvas(Modifier.size(36.dp)) {
        val w = size.width
        val h = size.height
        when (kind) {
            PayTileKind.Debit, PayTileKind.Cashless -> {
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.06f, h * 0.22f),
                    size = Size(w * 0.88f, h * 0.56f),
                    cornerRadius = CornerRadius(8f, 8f),
                    style = Stroke(width = 2.4f),
                )
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(w * 0.16f, h * 0.38f),
                    size = Size(w * 0.16f, h * 0.16f),
                    cornerRadius = CornerRadius(2f, 2f),
                )
            }
            PayTileKind.Credit -> {
                drawRoundRect(
                    color = Color.White,
                    topLeft = Offset(w * 0.04f, h * 0.2f),
                    size = Size(w * 0.92f, h * 0.6f),
                    cornerRadius = CornerRadius(8f, 8f),
                )
                drawCircle(Color(0xFFEB001B), radius = w * 0.13f, center = Offset(w * 0.42f, h * 0.5f))
                drawCircle(Color(0xFFF79E1B), radius = w * 0.13f, center = Offset(w * 0.58f, h * 0.5f))
            }
            PayTileKind.Pix, PayTileKind.Cash -> Unit
            PayTileKind.Split -> {
                drawCircle(tint, radius = w * 0.07f, center = Offset(w * 0.28f, h * 0.28f))
                drawCircle(tint, radius = w * 0.07f, center = Offset(w * 0.72f, h * 0.72f))
                drawLine(
                    color = tint,
                    start = Offset(w * 0.72f, h * 0.22f),
                    end = Offset(w * 0.28f, h * 0.78f),
                    strokeWidth = 3.2f,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

private fun brMoney(value: Double): String =
    "R$ " + String.format(Locale("pt", "BR"), "%.2f", value)
