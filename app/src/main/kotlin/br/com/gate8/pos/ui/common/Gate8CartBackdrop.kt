package br.com.gate8.pos.ui.common

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private const val CART_BLUR_RADIUS_DP = 18
private const val CART_SCRIM_ALPHA_API31 = 0.14f
private const val CART_SCRIM_ALPHA_LEGACY = 0.34f

fun Modifier.gate8BlurredWhenCartOpen(active: Boolean): Modifier {
    if (!active) return this
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        blur(CART_BLUR_RADIUS_DP.dp)
    } else {
        this
    }
}

@Composable
fun Gate8CartBackdropLayer(showCart: Boolean) {
    if (!showCart) return
    val scrimAlpha = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        CART_SCRIM_ALPHA_API31
    } else {
        CART_SCRIM_ALPHA_LEGACY
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = scrimAlpha)),
    )
}

@Composable
fun Gate8CartScreenRoot(
    showCart: Boolean,
    modifier: Modifier = Modifier,
    background: @Composable BoxScope.() -> Unit,
    sheet: @Composable () -> Unit,
) {
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .gate8BlurredWhenCartOpen(showCart),
        ) {
            background()
        }
        Gate8CartBackdropLayer(showCart)
        if (showCart) {
            sheet()
        }
    }
}
