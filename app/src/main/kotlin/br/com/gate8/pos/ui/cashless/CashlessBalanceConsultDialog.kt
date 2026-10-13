package br.com.gate8.pos.ui.cashless

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import br.com.gate8.pos.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.Locale

private val NavyTop = Color(0xFF12306A)
private val NavyMid = Color(0xFF0B1B42)
private val NavyBottom = Color(0xFF07122C)
private val Electric = Color(0xFF0000FE)
private val ElectricLift = Color(0xFF3D63FF)
private val ElectricText = Color(0xFF3A62FF)
private val CyanGlow = Color(0xFF7EB6FF)
private val LabelBlue = Color(0xFF9EC4FF)
private val CardFill = Color(0xFF0C2048)

@Composable
fun CashlessBalanceConsultDialog(
    title: String,
    balanceReais: Double,
    uidHex: String,
    status: String,
    name: String?,
    cpf: String?,
    phone: String?,
    note: String?,
    success: Boolean,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            val shape = RoundedCornerShape(28.dp)
            Column(
                Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 18.dp,
                        shape = shape,
                        ambientColor = Electric.copy(alpha = 0.45f),
                        spotColor = Electric.copy(alpha = 0.65f),
                    )
                    .clip(shape)
                    .background(
                        Brush.verticalGradient(listOf(NavyTop, NavyMid, NavyBottom)),
                    )
                    .border(1.dp, Electric.copy(alpha = 0.85f), shape)
                    .padding(horizontal = 22.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SuccessMark(success = success)
                Spacer(Modifier.height(16.dp))
                Text(
                    title,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    formatReais(balanceReais),
                    color = ElectricText,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(18.dp))
                DetailsCard(
                    uidHex = uidHex,
                    status = status,
                    name = name,
                    cpf = cpf,
                    phone = phone,
                )
                if (!note.isNullOrBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        note,
                        color = LabelBlue,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp,
                    )
                }
                Spacer(Modifier.height(20.dp))
                OkButton(onClick = onDismiss)
            }
        }
    }
}

@Composable
private fun SuccessMark(success: Boolean) {
    if (success) {
        Image(
            painter = painterResource(R.drawable.ic_cashless_success),
            contentDescription = null,
            modifier = Modifier.height(84.dp),
            contentScale = ContentScale.Fit,
        )
        return
    }
    Image(
        painter = painterResource(R.drawable.ic_cashless_info),
        contentDescription = null,
        modifier = Modifier.height(84.dp),
        contentScale = ContentScale.Fit,
    )
}

@Composable
private fun DetailsCard(
    uidHex: String,
    status: String,
    name: String?,
    cpf: String?,
    phone: String?,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CardFill.copy(alpha = 0.72f))
            .border(1.dp, Electric.copy(alpha = 0.35f), shape)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        DetailRow(Icons.Outlined.CreditCard, "UID", uidHex)
        DetailRow(Icons.Outlined.CheckCircle, "Status", status)
        name?.takeIf { it.isNotBlank() }?.let { DetailRow(Icons.Outlined.Person, "Nome", it) }
        cpf?.takeIf { it.isNotBlank() }?.let { DetailRow(Icons.Outlined.Badge, "CPF", formatCpf(it)) }
        phone?.takeIf { it.isNotBlank() }?.let { DetailRow(Icons.Outlined.Phone, "Telefone", formatPhone(it)) }
    }
}

@Composable
private fun DetailRow(icon: ImageVector, label: String, value: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = CyanGlow,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            color = LabelBlue,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            value,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun OkButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val shape = RoundedCornerShape(18.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                val scale = if (pressed) 0.97f else 1f
                scaleX = scale
                scaleY = scale
                alpha = if (pressed) 0.86f else 1f
            }
            .shadow(
                elevation = if (pressed) 4.dp else 10.dp,
                shape = shape,
                ambientColor = Electric.copy(alpha = 0.4f),
                spotColor = CyanGlow.copy(alpha = 0.45f),
            )
            .clip(shape)
            .background(Brush.verticalGradient(listOf(ElectricLift, Electric)))
            .border(1.dp, CyanGlow.copy(alpha = 0.85f), shape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "OK",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun formatReais(value: Double): String =
    "R$ ${"%.2f".format(Locale("pt", "BR"), value)}"

private fun formatCpf(cpf: String): String {
    val d = cpf.filter { it.isDigit() }
    if (d.length != 11) return cpf
    return "${d.substring(0, 3)}.${d.substring(3, 6)}.${d.substring(6, 9)}-${d.substring(9)}"
}

private fun formatPhone(phone: String): String {
    val d = phone.filter { it.isDigit() }
    if (d.length != 11) return phone
    return "(${d.take(2)}) ${d.substring(2, 7)}-${d.substring(7)}"
}
