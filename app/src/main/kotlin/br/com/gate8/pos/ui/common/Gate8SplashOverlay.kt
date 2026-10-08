package br.com.gate8.pos.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay

private const val SPLASH_MIN_MS = 2_000L

@Composable
fun Gate8SplashHost(
    onSplashVisible: (Boolean) -> Unit,
    onSplashFinished: () -> Unit,
    content: @Composable () -> Unit,
) {
    var showSplash by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        // Mantém só a arte da janela. Uma segunda imagem por cima mudava o enquadramento.
        delay(SPLASH_MIN_MS)
        showSplash = false
        onSplashVisible(false)
        onSplashFinished()
    }

    if (showSplash) {
        Box(Modifier.fillMaxSize())
    } else {
        content()
    }
}
