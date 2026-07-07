package br.com.gate8.pos.ui.common

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import br.com.gate8.pos.ui.theme.Gate8Colors

/**
 * Modal de alerta/aviso reutilizável (ex.: QR Code Pix expirado).
 * Ícone colorido configurável, título, motivo em destaque, detalhe opcional e botão OK.
 */
@Composable
fun Gate8AlertDialog(
    title: String,
    /** Motivo principal — exibido em destaque (negrito, cor primária). */
    reason: String? = null,
    /** Texto complementar abaixo do motivo (cor secundária). */
    detail: String? = null,
    icon: ImageVector = Icons.Filled.Schedule,
    accent: Color = Gate8Colors.Error,
    buttonLabel: String = "OK",
    onDismiss: () -> Unit,
) {
    val prominentText = reason?.takeIf { it.isNotBlank() }
    val secondaryText = detail?.takeIf { it.isNotBlank() }
    val legacyDetailOnly = prominentText == null && secondaryText != null
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White)
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(92.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(58.dp),
                    )
                }

                Spacer(Modifier.height(20.dp))

                Text(
                    title,
                    color = Gate8Colors.TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )

                if (prominentText != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        prominentText,
                        color = Gate8Colors.TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 22.sp,
                        textAlign = TextAlign.Center,
                    )
                }

                if (secondaryText != null) {
                    Spacer(Modifier.height(if (prominentText != null) 8.dp else 10.dp))
                    Text(
                        secondaryText,
                        color = Gate8Colors.TextSecondary,
                        fontSize = if (legacyDetailOnly) 14.sp else 13.sp,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Center,
                    )
                }

                Spacer(Modifier.height(26.dp))

                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(accent)
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        buttonLabel,
                        color = Gate8Colors.TextOnButton,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
