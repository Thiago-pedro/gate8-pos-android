package br.com.gate8.pos.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import br.com.gate8.pos.R
import kotlinx.coroutines.delay

private const val SPLASH_MIN_MS = 1100L

@Composable
fun Gate8SplashHost(
    onSplashVisible: (Boolean) -> Unit,
    onSplashFinished: () -> Unit,
    content: @Composable () -> Unit,
) {
    var showSplash by remember { mutableStateOf(true) }

    LaunchedEffect(showSplash) {
        onSplashVisible(showSplash)
    }

    LaunchedEffect(Unit) {
        delay(SPLASH_MIN_MS)
        showSplash = false
        onSplashFinished()
    }

    if (showSplash) {
        Image(
            painter = painterResource(R.drawable.bg_splash_inicio),
            contentDescription = "Gate8",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    } else {
        content()
    }
}
