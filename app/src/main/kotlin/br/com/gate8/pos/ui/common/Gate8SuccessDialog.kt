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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import br.com.gate8.pos.R
import br.com.gate8.pos.ui.theme.Gate8Colors

/**
 * Modal simples de confirmação de sucesso (ex.: venda concluída).
 * Mostra um ícone verde de check, título, detalhe opcional e um botão de OK.
 */
@Composable
@Suppress("UNUSED_PARAMETER")
fun Gate8SuccessDialog(
    title: String,
    detail: String? = null,
    buttonLabel: String = "OK",
    onDismiss: () -> Unit,
    onDark: Boolean = false,
) {
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
            val shape = RoundedCornerShape(22.dp)
            val buttonShape = RoundedCornerShape(16.dp)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(Color(0xFF0C1E4A))
                    .border(1.5.dp, Color(0xFF0000FE).copy(alpha = 0.9f), shape)
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_success_check),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(108.dp),
                    contentScale = ContentScale.Fit,
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    title,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )

                if (!detail.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        detail,
                        color = Color.White,
                        fontSize = 16.sp,
                        lineHeight = 22.sp,
                        textAlign = TextAlign.Center,
                    )
                }

                Spacer(Modifier.height(26.dp))

                Box(
                    Modifier
                        .fillMaxWidth()
                        .border(1.5.dp, Color(0xFF6EA8FF), buttonShape)
                        .clip(buttonShape)
                        .background(Color(0xFF1A46FF))
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
