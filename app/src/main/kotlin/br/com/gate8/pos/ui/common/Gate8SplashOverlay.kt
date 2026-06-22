package br.com.gate8.pos.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.gate8.pos.R
import br.com.gate8.pos.ui.theme.Gate8Colors
import kotlinx.coroutines.delay

private const val SPLASH_MIN_MS = 4000L

@Composable
fun Gate8SplashHost(
    onSplashVisible: (Boolean) -> Unit,
    onSplashFinished: () -> Unit,
    content: @Composable () -> Unit,
) {
    var showSplash by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        // Solta o splash do sistema (branco) assim que o primeiro frame do Compose é desenhado,
        // para que o splash do app (imagem + barra) fique visível por cima.
        onSplashVisible(false)
        delay(SPLASH_MIN_MS)
        showSplash = false
        onSplashFinished()
    }

    if (showSplash) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Gate8Colors.Background),
        ) {
            Image(
                painter = painterResource(R.drawable.splash_inicio),
                contentDescription = "Gate8",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                LinearProgressIndicator(
                    color = Gate8Colors.AccentBlue,
                    trackColor = Gate8Colors.AccentBlue.copy(alpha = 0.2f),
                    modifier = Modifier.width(200.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Inicializando",
                    color = Gate8Colors.AccentBlue,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    } else {
        content()
    }
}
