package br.com.gate8.pos.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.com.gate8.pos.ui.theme.Gate8Colors

@Composable
fun Gate8ScreenTopBar(
    onMenu: () -> Unit,
    onAction: () -> Unit,
    actionContentDescription: String = "Atualizar",
) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 12.dp),
    ) {
        IconButton(
            onClick = onMenu,
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            Icon(Icons.Filled.Home, contentDescription = "Início", tint = Gate8Colors.TextPrimary)
        }

        Gate8HeaderLogo(
            modifier = Modifier.align(Alignment.Center),
            height = 42.dp,
            horizontalPadding = 52.dp,
        )

        IconButton(
            onClick = onAction,
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = actionContentDescription,
                tint = Gate8Colors.TextPrimary,
            )
        }
    }
}
