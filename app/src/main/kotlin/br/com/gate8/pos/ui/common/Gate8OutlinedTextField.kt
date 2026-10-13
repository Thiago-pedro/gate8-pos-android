package br.com.gate8.pos.ui.common

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import br.com.gate8.pos.ui.theme.Gate8Colors

@Composable
fun Gate8OutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    prefix: String? = null,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    onDark: Boolean = false,
) {
    val textColor = if (onDark) androidx.compose.ui.graphics.Color.White else Gate8Colors.TextOnLight
    val labelColor = if (onDark) androidx.compose.ui.graphics.Color(0xFF9AD4FF) else Gate8Colors.TextOnLight
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        prefix = prefix?.let { { Text(it) } },
        singleLine = singleLine,
        modifier = modifier,
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = textColor,
            unfocusedTextColor = textColor,
            focusedBorderColor = if (onDark) androidx.compose.ui.graphics.Color(0xFF0000FE) else Gate8Colors.AccentBlue,
            unfocusedBorderColor = if (onDark) androidx.compose.ui.graphics.Color(0xFF0000FE).copy(alpha = 0.7f) else Gate8Colors.TextOnLight.copy(alpha = 0.5f),
            focusedLabelColor = labelColor,
            unfocusedLabelColor = labelColor,
            cursorColor = if (onDark) androidx.compose.ui.graphics.Color.White else Gate8Colors.AccentBlue,
            focusedPlaceholderColor = textColor.copy(alpha = 0.45f),
            unfocusedPlaceholderColor = textColor.copy(alpha = 0.45f),
            focusedPrefixColor = textColor,
            unfocusedPrefixColor = textColor,
        ),
        shape = RoundedCornerShape(12.dp),
    )
}
