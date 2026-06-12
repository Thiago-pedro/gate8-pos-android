package br.com.gate8.pos.ui.common

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.com.gate8.pos.R

@Composable
fun Gate8HeaderLogo(
    modifier: Modifier = Modifier,
    height: Dp = 46.dp,
    horizontalPadding: Dp = 44.dp,
) {
    Image(
        painter = painterResource(R.drawable.logo_gate8_header),
        contentDescription = "Gate8 tickets",
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding)
            .height(height),
        contentScale = ContentScale.Fit,
    )
}
