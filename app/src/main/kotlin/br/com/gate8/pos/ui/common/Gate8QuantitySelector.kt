package br.com.gate8.pos.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.gate8.pos.ui.theme.Gate8Colors

@Composable
fun Gate8QuantitySelector(
    quantity: Int,
    canIncrement: Boolean,
    compact: Boolean,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    quantityColor: Color = Gate8Colors.TextPrimary,
    enabledButtonColor: Color = Gate8Colors.AccentBlue,
    disabledButtonColor: Color = Gate8Colors.CardSurfaceElevated,
) {
    val buttonSize = if (compact) 26.dp else 32.dp
    val iconSize = if (compact) 14.dp else 18.dp
    val qtyFontSize = if (compact) 12.sp else 15.sp

    val rowModifier = if (compact) {
        Modifier.fillMaxWidth()
    } else {
        Modifier
    }

    Row(
        rowModifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Gate8QuantityButton(
            enabled = quantity > 0,
            size = buttonSize,
            enabledColor = enabledButtonColor,
            disabledColor = disabledButtonColor,
            onClick = onDecrement,
        ) {
            Icon(
                Icons.Filled.Remove,
                contentDescription = "Remover",
                modifier = Modifier.size(iconSize),
                tint = if (quantity > 0) Color.White else Gate8Colors.TextSecondary,
            )
        }

        Text(
            quantity.toString(),
            color = quantityColor,
            fontSize = qtyFontSize,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = if (compact) 8.dp else 12.dp),
            textAlign = TextAlign.Center,
        )

        Gate8QuantityButton(
            enabled = canIncrement,
            size = buttonSize,
            enabledColor = enabledButtonColor,
            disabledColor = disabledButtonColor,
            onClick = onIncrement,
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = "Adicionar",
                modifier = Modifier.size(iconSize),
                tint = if (canIncrement) Color.White else Gate8Colors.TextSecondary,
            )
        }
    }
}

@Composable
private fun Gate8QuantityButton(
    enabled: Boolean,
    size: Dp,
    enabledColor: Color,
    disabledColor: Color,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (enabled) enabledColor else disabledColor)
            .then(
                if (enabled) Modifier.clickable(onClick = onClick) else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
