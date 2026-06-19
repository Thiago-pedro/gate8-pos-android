package br.com.gate8.pos.ui.common

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contactless
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import br.com.gate8.pos.domain.model.PaymentMethodApi
import br.com.gate8.pos.ui.theme.Gate8Colors

/**
 * Overlay em tela cheia exibido enquanto a maquininha aguarda o cartão (ou gera o Pix).
 * Mostra ícone grande, valor, instrução e spinner. Não aparece para dinheiro.
 */
@Composable
fun PaymentWaitingOverlay(
    visible: Boolean,
    method: PaymentMethodApi?,
    amount: Double,
    onCancel: (() -> Unit)? = null,
) {
    if (!visible) return
    val message = paymentLoadingMessage(method) ?: return

    val title: String
    val icon: ImageVector
    when (method) {
        PaymentMethodApi.DEBIT -> {
            title = "Pagamento no débito"
            icon = Icons.Filled.Contactless
        }
        PaymentMethodApi.CREDIT -> {
            title = "Pagamento no crédito"
            icon = Icons.Filled.Contactless
        }
        PaymentMethodApi.PIX -> {
            title = "Pagamento via Pix"
            icon = Icons.Filled.QrCode2
        }
        else -> {
            title = "Processando pagamento"
            icon = Icons.Filled.CreditCard
        }
    }

    val pulse = rememberInfiniteTransition(label = "pulse")
    val scale by pulse.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scale",
    )

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
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "Valor a pagar",
                    color = Gate8Colors.TextSecondary,
                    fontSize = 16.sp,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "R$ ${"%.2f".format(amount)}",
                    color = Gate8Colors.TextPrimary,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(Modifier.height(48.dp))

                Box(
                    Modifier
                        .size(200.dp)
                        .scale(scale)
                        .clip(CircleShape)
                        .background(Gate8Colors.AccentBlue.copy(alpha = 0.10f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = Gate8Colors.AccentBlue,
                        modifier = Modifier.size(110.dp),
                    )
                }

                Spacer(Modifier.height(44.dp))

                Text(
                    title,
                    color = Gate8Colors.TextPrimary,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    message,
                    color = Gate8Colors.TextSecondary,
                    fontSize = 19.sp,
                    lineHeight = 27.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(48.dp))

                CircularProgressIndicator(
                    color = Gate8Colors.AccentBlue,
                    strokeWidth = 4.dp,
                    modifier = Modifier.size(52.dp),
                )
            }

            if (onCancel != null) {
                TextButton(
                    onClick = onCancel,
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
}
