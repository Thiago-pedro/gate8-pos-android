package br.com.gate8.pos.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

private val CartBarFill = Color(0x80304870)
private val CartBarSolid = Color(0xFF0C1E4A)
private val CartNeon = Color(0xFF0000FE)
private val CartBarButton = Color(0xFF1236FF)

@Composable
fun Gate8CartBar(
    itemCount: Int,
    total: Double,
    itemSingular: String,
    onOpenCart: () -> Unit,
    modifier: Modifier = Modifier,
    solid: Boolean = false,
) {
    val shape = RoundedCornerShape(20.dp)
    val buttonShape = RoundedCornerShape(14.dp)
    val itemWord = if (itemCount == 1) itemSingular else "${itemSingular}s"
    Row(
        modifier
            .fillMaxWidth()
            .neonBorder(CartNeon, 20.dp)
            .clip(shape)
            .background(if (solid) CartBarSolid else CartBarFill)
            .border(1.5.dp, CartNeon, shape)
            .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.ShoppingCart,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(26.dp),
        )
        Column(
            Modifier
                .weight(1f)
                .padding(start = 10.dp, end = 8.dp),
        ) {
            Text(
                "$itemCount $itemWord",
                color = Color.White,
                fontSize = 13.sp,
                lineHeight = 16.sp,
            )
            Text(
                brCartMoney(total),
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 28.sp,
            )
        }
        Row(
            Modifier
                .clip(buttonShape)
                .background(CartBarButton)
                .clickable(onClick = onOpenCart)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.ShoppingCart,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "IR PARA CARRINHO",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.3.sp,
            )
        }
    }
}

private fun brCartMoney(value: Double): String =
    String.format(Locale("pt", "BR"), "R$ %,.2f", value)

/** Halo só na borda. O sombreado antigo vazava pelo fundo translúcido e manchava o valor. */
private fun Modifier.neonBorder(color: Color, corner: Dp): Modifier = drawBehind {
    val radius = corner.toPx()
    listOf(
        6.dp.toPx() to 0.08f,
        3.dp.toPx() to 0.16f,
    ).forEach { (width, alpha) ->
        drawRoundRect(
            color = color.copy(alpha = alpha),
            cornerRadius = CornerRadius(radius, radius),
            style = Stroke(width = width),
        )
    }
}
