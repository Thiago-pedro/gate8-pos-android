package br.com.gate8.pos.ui.common



import androidx.compose.foundation.background

import androidx.compose.foundation.layout.Box

import androidx.compose.foundation.layout.BoxScope

import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.foundation.layout.fillMaxWidth

import androidx.compose.runtime.Composable

import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier

import br.com.gate8.pos.ui.theme.Gate8Colors



@Composable

fun Gate8ScreenBackground(

    modifier: Modifier = Modifier,

    contentAlignment: Alignment = Alignment.TopStart,

    content: @Composable BoxScope.() -> Unit,

) {

    Box(

        modifier = modifier

            .fillMaxSize()

            .background(Gate8Colors.Background),

        contentAlignment = contentAlignment,

        content = content,

    )

}



@Composable

fun Gate8ScreenBackgroundFillWidth(

    modifier: Modifier = Modifier,

    content: @Composable BoxScope.() -> Unit,

) {

    Box(

        modifier = modifier

            .fillMaxWidth()

            .background(Gate8Colors.Background),

        content = content,

    )

}


