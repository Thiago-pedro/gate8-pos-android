package br.com.gate8.pos.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

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
                .graphicsLayer { alpha = if (showCart) 0f else 1f },
        ) {
            background()
        }
        if (showCart) {
            sheet()
        }
    }
}
