package br.com.gate8.pos.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Contactless
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import br.com.gate8.pos.BuildConfig
import br.com.gate8.pos.R
import br.com.gate8.pos.domain.model.PaymentMethodApi
import java.util.Locale

/**
 * Card de espera enquanto a maquininha aguarda o cartão (ou gera o Pix).
 * Não aparece para dinheiro. No flavor **cielo** fica desligado, exceto cashless
 * (leitura no próprio Gate8).
 */
@Composable
fun PaymentWaitingOverlay(
    visible: Boolean,
    method: PaymentMethodApi?,
    amount: Double,
    onCancel: (() -> Unit)? = null,
    titleOverride: String? = null,
    messageOverride: String? = null,
    amountCaption: String = "Valor a pagar",
    showAmount: Boolean = true,
) {
    if (!visible) return
    if (BuildConfig.FLAVOR.equals("cielo", ignoreCase = true) &&
        method != PaymentMethodApi.CASHLESS
    ) {
        return
    }
    val message = messageOverride ?: paymentLoadingMessage(method) ?: return
    val cashless = method == PaymentMethodApi.CASHLESS

    val title: String
    val icon: ImageVector
    when (method) {
        PaymentMethodApi.DEBIT -> {
            title = titleOverride ?: "Pagamento no débito"
            icon = Icons.Filled.Contactless
        }
        PaymentMethodApi.CREDIT -> {
            title = titleOverride ?: "Pagamento no crédito"
            icon = Icons.Filled.Contactless
        }
        PaymentMethodApi.PIX -> {
            title = titleOverride ?: "Pagamento via Pix"
            icon = Icons.Filled.QrCode2
        }
        PaymentMethodApi.CASHLESS -> {
            title = titleOverride ?: "Pagamento cashless"
            icon = Icons.Filled.Contactless
        }
        else -> {
            title = titleOverride ?: "Processando pagamento"
            icon = Icons.Filled.CreditCard
        }
    }

    val shape = RoundedCornerShape(22.dp)
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .clip(shape)
                .background(Color(0xFF0C1E4A))
                .border(1.5.dp, Color(0xFF0000FE).copy(alpha = 0.9f), shape)
                .padding(horizontal = 22.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (showAmount) {
                Text(
                    amountCaption,
                    color = Color(0xFFD6E4FF),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    String.format(Locale("pt", "BR"), "R$ %,.2f", amount),
                    color = Color.White,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
            }

            if (cashless) {
                Image(
                    painter = painterResource(R.drawable.ic_cashless_nfc),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(108.dp),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Box(
                    Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0000FE).copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = Color(0xFF7EE7FF),
                        modifier = Modifier.size(52.dp),
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                title,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (cashless && !message.endsWith("...")) "$message..." else message,
                color = Color(0xFFD6E4FF),
                fontSize = 15.sp,
                lineHeight = 21.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(18.dp))
            CircularProgressIndicator(
                color = Color(0xFF3D7CFF),
                strokeWidth = 3.dp,
                modifier = Modifier.size(36.dp),
            )
            if (cashless) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Aguardando cartão...",
                    color = Color(0xFF9AD4FF),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
            }

            if (onCancel != null) {
                Spacer(Modifier.height(16.dp))
                Text(
                    "Cancelar",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onCancel)
                        .padding(vertical = 10.dp),
                )
            }
        }
    }
}
