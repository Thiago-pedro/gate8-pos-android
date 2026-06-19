package br.com.gate8.pos.ui.common

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import br.com.gate8.pos.ui.theme.Gate8Colors

/**
 * Modal de confirmação (Sim/Não) baseado em [Dialog], portanto sempre flutua
 * acima do conteúdo da tela — diferente do `Gate8ConfirmDialog`, que é um Box
 * inline e depende da ordem de desenho.
 */
@Composable
fun Gate8ConfirmModal(
    title: String,
    message: String,
    confirmLabel: String,
    dismissLabel: String = "Cancelar",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
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
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.White)
                    .padding(horizontal = 24.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    title,
                    color = Gate8Colors.TextPrimary,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    message,
                    color = Gate8Colors.TextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(24.dp))

                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Gate8Colors.AccentBlue)
                        .clickable(onClick = onConfirm)
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        confirmLabel,
                        color = Gate8Colors.TextOnButton,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Spacer(Modifier.height(10.dp))

                Text(
                    dismissLabel,
                    color = Gate8Colors.AccentBlue,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 14.dp),
                )
            }
        }
    }
}
