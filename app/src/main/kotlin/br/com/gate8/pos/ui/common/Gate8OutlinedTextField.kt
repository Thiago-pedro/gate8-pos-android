package br.com.gate8.pos.ui.common

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.gate8.pos.ui.theme.Gate8Colors

@Composable
fun Gate8OutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = singleLine,
        modifier = modifier,
        keyboardOptions = keyboardOptions,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Gate8Colors.TextOnLight,
            unfocusedTextColor = Gate8Colors.TextOnLight,
            focusedBorderColor = Gate8Colors.AccentBlue,
            unfocusedBorderColor = Gate8Colors.TextOnLight.copy(alpha = 0.5f),
            focusedLabelColor = Gate8Colors.AccentBlue,
            unfocusedLabelColor = Gate8Colors.TextOnLight,
            cursorColor = Gate8Colors.AccentBlue,
            focusedPlaceholderColor = Gate8Colors.TextOnLight.copy(alpha = 0.45f),
            unfocusedPlaceholderColor = Gate8Colors.TextOnLight.copy(alpha = 0.45f),
        ),
        shape = RoundedCornerShape(12.dp),
    )
}
