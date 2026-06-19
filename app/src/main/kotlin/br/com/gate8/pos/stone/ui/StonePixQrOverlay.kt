package br.com.gate8.pos.stone.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import br.com.gate8.pos.stone.StoneActivityHolder
import br.com.gate8.pos.ui.theme.Gate8Colors

/**
 * Overlay do QR Code PIX. É renderizado como [Dialog] (janela própria) para ficar SEMPRE
 * à frente do [br.com.gate8.pos.ui.common.PaymentWaitingOverlay] (que também é um Dialog).
 * O fundo é sólido para cobrir o spinner que continua rodando por baixo.
 */
@Composable
fun StonePixQrOverlay(holder: StoneActivityHolder) {
    val qr by holder.pixQrCode.collectAsState()
    val bitmap = qr ?: return

    // Barra de tempo: o QR Pix da Stone vale ~90 segundos.
    val progress = remember(bitmap) { Animatable(1f) }
    LaunchedEffect(bitmap) {
        progress.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 90_000, easing = LinearEasing),
        )
    }
    // Vira vermelho nos últimos ~10 segundos.
    val ending by remember { derivedStateOf { progress.value <= 10f / 90f } }
    val barColor = if (ending) Gate8Colors.Error else Gate8Colors.AccentBlue

    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Gate8Colors.Background),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp),
            ) {
                Text(
                    "PIX",
                    color = Gate8Colors.AccentBlue,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Escaneie o QR Code para pagar",
                    color = Gate8Colors.TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(28.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White)
                        .padding(16.dp),
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "QR Code PIX",
                        modifier = Modifier.size(280.dp),
                    )
                }
                Spacer(Modifier.height(24.dp))
                Text(
                    "Aguardando confirmação do pagamento…",
                    color = Gate8Colors.TextSecondary,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(18.dp))
                LinearProgressIndicator(
                    progress = { progress.value },
                    modifier = Modifier
                        .width(280.dp)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = barColor,
                    trackColor = barColor.copy(alpha = 0.15f),
                )
            }

            TextButton(
                onClick = { holder.requestCancel() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp),
            ) {
                Text(
                    "Cancelar",
                    color = Gate8Colors.TextSecondary,
                    fontSize = 14.sp,
                )
            }
        }
    }
}
