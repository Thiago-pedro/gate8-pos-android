package br.com.gate8.pos.ui.login

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.gate8.pos.R
import br.com.gate8.pos.ui.common.Gate8HeaderLogo
import br.com.gate8.pos.ui.common.Gate8MenuButton
import br.com.gate8.pos.ui.common.Gate8ScreenBackground
import br.com.gate8.pos.ui.theme.Gate8Colors
import kotlin.math.abs
import kotlin.math.sin
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel

private enum class TokenPhase { Idle, Checking, Success, Error, Done }

private const val TOKEN_ACCENT = 0xFF0000FE

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

@Composable
fun LoginScreen(
    onHome: () -> Unit,
    onPending: () -> Unit,
    vm: LoginViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    var folded by remember { mutableStateOf(false) }
    val phase = when {
        folded -> TokenPhase.Done
        state.confirmed -> TokenPhase.Success
        state.loading -> TokenPhase.Checking
        state.error != null -> TokenPhase.Error
        else -> TokenPhase.Idle
    }

    DisposableEffect(context) {
        val window = context.findActivity()?.window
        val previousMode = window?.attributes?.softInputMode
        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        onDispose {
            if (window == null) return@onDispose
            val adjustMask = WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST
            val restore = if (previousMode != null && previousMode and adjustMask != 0) {
                previousMode
            } else {
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            }
            window.setSoftInputMode(restore)
        }
    }

    LaunchedEffect(Unit) {
        vm.navigation.collect { nav ->
            when (nav) {
                // A home espera a animação de sucesso. Pendente abre na hora.
                LoginNavigation.Home -> Unit
                LoginNavigation.Pending -> onPending()
            }
        }
    }

    LaunchedEffect(state.confirmed) {
        if (!state.confirmed) {
            folded = false
            return@LaunchedEffect
        }
        keyboard?.hide()
        delay(650)
        folded = true
        delay(780)
        onHome()
    }

    LaunchedEffect(state.loading) {
        if (state.loading) keyboard?.hide()
    }

    LaunchedEffect(state.errorTick) {
        if (state.errorTick == 0) return@LaunchedEffect
        delay(600)
        vm.dismissFailedToken()
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF020936)),
    ) {
        var baseHeight by remember { mutableStateOf(0.dp) }
        if (maxHeight > baseHeight) baseHeight = maxHeight
        val screenHeight = if (baseHeight > 0.dp) baseHeight else maxHeight
        val keyboardOpen = screenHeight - maxHeight > 64.dp

        TokenTechBackground(Modifier.matchParentSize())

        val cardWidth = maxWidth * 0.90f
        val cardInner = cardWidth * 0.90f
        val boxGap = (maxWidth.value * 0.030f).dp.coerceIn(10.dp, 12.dp)
        val targetRow = maxWidth * 0.80f
        val row = if (targetRow < cardInner) targetRow else cardInner
        val boxSize = (row - boxGap * 5) / 6
        val boxCorner = (boxSize.value * 0.42f).dp.coerceIn(17.dp, 20.dp)
        val cardRadius = (maxWidth.value * 36f / 360f).dp.coerceIn(34.dp, 38.dp)
        val cardHeight = screenHeight * 0.23f
        val logoPainter = painterResource(R.drawable.logo_gate8_header)
        val logoAspect = logoPainter.intrinsicSize.let { size ->
            if (size.width > 0f && size.height > 0f) size.height / size.width else 0.22f
        }
        val logoWidth = if (keyboardOpen && maxHeight < 230.dp) maxWidth * 0.48f else maxWidth * 0.61f
        val logoHeight = logoWidth * logoAspect
        val midGap = if (!keyboardOpen) {
            (screenHeight * 0.19f - logoHeight).coerceAtLeast(16.dp)
        } else if (maxHeight < 230.dp) {
            8.dp
        } else {
            18.dp
        }
        val cardBlock = if (keyboardOpen) boxSize + 96.dp else cardHeight
        val topGap = if (!keyboardOpen) {
            screenHeight * 0.15f
        } else {
            ((maxHeight - logoHeight - midGap - cardBlock) / 2).coerceAtLeast(4.dp)
        }

        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(topGap))
            LoginLogo(width = logoWidth)
            Spacer(Modifier.height(midGap))
            LoginTokenCard(
                cardWidth = cardWidth,
                cardHeight = if (keyboardOpen) null else cardHeight,
                cardRadius = cardRadius,
                boxSize = boxSize,
                boxGap = boxGap,
                boxCorner = boxCorner,
                phase = phase,
                token = state.producerToken,
                error = state.error,
                errorTick = state.errorTick,
                enabled = !state.loading && !state.confirmed,
                onTokenChange = vm::onProducerTokenChange,
            )
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun LoginLogo(
    width: Dp,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(R.drawable.logo_gate8_header),
        contentDescription = "Gate8",
        modifier = modifier
            .width(width)
            .wrapContentHeight(),
        contentScale = ContentScale.FillWidth,
    )
}

@Composable
private fun LoginTokenCard(
    cardWidth: Dp,
    cardHeight: Dp?,
    cardRadius: Dp,
    boxSize: Dp,
    boxGap: Dp,
    boxCorner: Dp,
    phase: TokenPhase,
    token: String,
    error: String?,
    errorTick: Int,
    enabled: Boolean,
    onTokenChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(cardRadius)
    val locked = cardHeight != null && phase != TokenPhase.Error && phase != TokenPhase.Checking
    Column(
        modifier
            .width(cardWidth)
            .then(
                if (locked) {
                    Modifier.height(cardHeight!!)
                } else {
                    Modifier.heightIn(min = cardHeight ?: 0.dp)
                },
            )
            .drawBehind {
                val radius = cardRadius.toPx()
                val spread = 7.dp.toPx()
                drawRoundRect(
                    color = Color(0xFF087BFF).copy(alpha = 0.28f),
                    topLeft = Offset(-spread, -spread),
                    size = Size(size.width + spread * 2, size.height + spread * 2),
                    cornerRadius = CornerRadius(radius + spread),
                    style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round),
                )
            }
            .background(Color(0xF2030D3D), shape)
            .border(2.dp, Color(0xFF005BFF), shape)
            .padding(horizontal = cardWidth * 0.05f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val titleGap = if (locked) {
            val height = cardHeight ?: 0.dp
            val room = height - boxSize - 56.dp
            (height * 0.20f).coerceAtMost(room).coerceAtLeast(12.dp)
        } else {
            18.dp
        }
        Spacer(Modifier.height(titleGap))
        Text(
            "Token",
            color = Color(0xFFF4F6FF),
            fontSize = 19.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(14.dp))
        ProducerTokenBoxes(
            value = token,
            onValueChange = onTokenChange,
            phase = phase,
            errorTick = errorTick,
            enabled = enabled,
            boxSize = boxSize,
            gap = boxGap,
            boxCorner = boxCorner,
        )
        if (phase == TokenPhase.Checking) {
            Spacer(Modifier.height(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = Color(TOKEN_ACCENT),
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Validando…",
                    color = Color(0xFF9AD4FF),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        error?.let {
            Spacer(Modifier.height(12.dp))
            Text(
                it,
                color = Gate8Colors.Error,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (locked) {
            Spacer(Modifier.weight(1f))
        } else {
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun TokenTechBackground(modifier: Modifier = Modifier) {
    Box(modifier) {
        Image(
            painter = painterResource(R.drawable.app_screen_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
            colorFilter = ColorFilter.colorMatrix(
                ColorMatrix(
                    floatArrayOf(
                        0.22f, 0f, 0f, 0f, 2f,
                        0f, 0.28f, 0f, 0f, 6f,
                        0f, 0f, 0.55f, 0f, 18f,
                        0f, 0f, 0f, 1f, 0f,
                    ),
                ),
            ),
        )
        Canvas(Modifier.matchParentSize()) {
            drawRect(Color(0xFF020936).copy(alpha = 0.55f))
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xE6020936),
                        Color(0x99020936),
                        Color(0x22020936),
                    ),
                    center = Offset(size.width * 0.5f, size.height * 0.42f),
                    radius = size.maxDimension * 0.72f,
                ),
            )
        }
    }
}

@Composable
private fun ProducerTokenBoxes(
    value: String,
    onValueChange: (String) -> Unit,
    phase: TokenPhase,
    errorTick: Int,
    enabled: Boolean,
    boxSize: Dp,
    gap: Dp,
    boxCorner: Dp,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val shake = remember { Animatable(0f) }
    var focused by remember { mutableStateOf(false) }
    val clock = rememberInfiniteTransition(label = "otpRipple")
    val wave by clock.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "otpWave",
    )
    val caret by clock.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "otpCaret",
    )

    LaunchedEffect(errorTick) {
        if (errorTick == 0) return@LaunchedEffect
        shake.snapTo(0f)
        listOf(-14f, 12f, -9f, 7f, -4f, 0f).forEach { target ->
            shake.animateTo(target, tween(80, easing = FastOutSlowInEasing))
        }
    }

    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            modifier = Modifier
                .focusRequester(focusRequester)
                .onFocusChanged { focused = it.isFocused }
                .fillMaxWidth()
                .graphicsLayer { translationX = shake.value },
            textStyle = TextStyle(color = Color.Transparent, fontSize = 1.sp),
            cursorBrush = SolidColor(Color.Transparent),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                autoCorrectEnabled = false,
            ),
            decorationBox = { innerTextField ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(boxSize)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            enabled = enabled,
                        ) {
                            focusRequester.requestFocus()
                            keyboard?.show()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(1.dp).alpha(0f)) { innerTextField() }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally),
                    ) {
                        repeat(6) { index ->
                            TokenCell(
                                index = index,
                                glyph = value.getOrNull(index),
                                phase = phase,
                                active = focused && enabled && phase == TokenPhase.Idle && index == value.length && value.length < 6,
                                boxSize = boxSize,
                                boxCorner = boxCorner,
                                wave = wave,
                                caretAlpha = caret,
                            )
                        }
                    }
                    AnimatedVisibility(
                        visible = phase == TokenPhase.Done,
                        enter = fadeIn(tween(180, delayMillis = 120)) + scaleIn(
                            initialScale = 0.3f,
                            animationSpec = spring(dampingRatio = 0.52f, stiffness = 380f),
                        ),
                    ) {
                        VerifiedPill()
                    }
                }
            },
        )
    }
}

@Composable
private fun TokenCell(
    index: Int,
    glyph: Char?,
    phase: TokenPhase,
    active: Boolean,
    boxSize: Dp,
    boxCorner: Dp,
    wave: Float,
    caretAlpha: Float,
) {
    val accent = Color(TOKEN_ACCENT)
    val shape = RoundedCornerShape(boxCorner)
    val density = LocalDensity.current
    val fold = remember { Animatable(0f) }
    val painted = phase == TokenPhase.Success || phase == TokenPhase.Done
    var accentOn by remember { mutableStateOf(false) }

    LaunchedEffect(painted) {
        if (painted) {
            delay(index * 50L)
            accentOn = true
        } else {
            accentOn = false
        }
    }
    LaunchedEffect(phase) {
        if (phase == TokenPhase.Done) {
            delay((abs(2.5f - index) * 30f).toLong())
            fold.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 260f))
        } else {
            fold.snapTo(0f)
        }
    }

    val shifted = ((wave - index * 0.0875f) % 1f + 1f) % 1f
    val ripple = if (phase == TokenPhase.Checking) -sin(shifted * Math.PI).toFloat() else 0f
    val fill = when {
        accentOn -> accent
        phase == TokenPhase.Error -> Color(0xFF3A1F1E)
        active -> Color(0xFF087BFF).copy(alpha = 0.18f)
        else -> Color(0xFF0A1B55).copy(alpha = 0.45f)
    }
    val border = when {
        phase == TokenPhase.Error -> Color(0xFFFF3B30)
        accentOn -> accent
        active -> Color(0xFF00A6FF)
        else -> Color(0xFF0070FF)
    }

    Box(
        modifier = Modifier
            .size(boxSize)
            .graphicsLayer {
                val towardCenter = (2.5f - index) * with(density) { 28.dp.toPx() }
                translationX = towardCenter * fold.value
                translationY = with(density) { 8.dp.toPx() } * ripple
                val scale = 1f - 0.7f * fold.value
                scaleX = scale
                scaleY = scale
                alpha = 1f - fold.value
            }
            .drawBehind {
                if (!active || accentOn || phase == TokenPhase.Error) return@drawBehind
                val radius = boxCorner.toPx()
                val spread = 3.dp.toPx()
                drawRoundRect(
                    color = Color(0xFF00A6FF).copy(alpha = 0.45f),
                    topLeft = Offset(-spread, -spread),
                    size = Size(size.width + spread * 2, size.height + spread * 2),
                    cornerRadius = CornerRadius(radius + spread),
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
                )
            }
            .clip(shape)
            .background(fill)
            .border(if (active) 2.5.dp else 2.dp, border, shape),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = glyph,
            transitionSpec = {
                (
                    fadeIn(spring(dampingRatio = 0.55f, stiffness = 520f)) +
                        scaleIn(
                            initialScale = 0.4f,
                            animationSpec = spring(dampingRatio = 0.5f, stiffness = 520f),
                        ) +
                        slideInVertically(
                            animationSpec = spring(dampingRatio = 0.55f, stiffness = 520f),
                        ) { 18 }
                    ).togetherWith(
                    fadeOut(tween(120)) +
                        scaleOut(targetScale = 0.6f, animationSpec = tween(120)) +
                        slideOutVertically(tween(120)) { -14 },
                )
            },
            label = "otpDigit$index",
        ) { char ->
            if (char != null) {
                Text(
                    text = char.toString(),
                    color = if (phase == TokenPhase.Error) Color(0xFFFF8A80) else Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
            } else if (active) {
                Box(
                    Modifier
                        .width(2.dp)
                        .height(22.dp)
                        .alpha(if (caretAlpha > 0.5f) 1f else 0.15f)
                        .background(accent, RoundedCornerShape(1.dp)),
                )
            }
        }
    }
}

@Composable
private fun VerifiedPill() {
    Row(
        modifier = Modifier
            .height(48.dp)
            .clip(CircleShape)
            .background(Color(0xFFF5F5F7))
            .padding(start = 8.dp, end = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Color(TOKEN_ACCENT)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            "Verificado",
            color = Color(0xFF1D1D1F),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun LoginPendingScreen(
    onBackToLogin: () -> Unit,
    onHome: () -> Unit,
    vm: LoginViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsState()

    LaunchedEffect(Unit) {
        vm.navigation.collect { nav ->
            when (nav) {
                LoginNavigation.Home -> onHome()
                LoginNavigation.Pending -> Unit
            }
        }
    }

    Gate8ScreenBackground {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))
            Gate8HeaderLogo(height = 52.dp)
            Spacer(Modifier.height(32.dp))

            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Gate8Colors.CardSurface)
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Aguardando liberação",
                    color = Gate8Colors.TextOnLight,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    state.pendingDeviceName?.let { "Nome: $it" }
                        ?: "O produtor precisa liberar esta maquininha no painel.",
                    color = Gate8Colors.TextOnLight.copy(alpha = 0.75f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                )
                state.error?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        it,
                        color = Gate8Colors.Error,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(20.dp))
                if (state.loading) {
                    CircularProgressIndicator(color = Gate8Colors.AccentBlue)
                } else {
                    Gate8MenuButton(
                        title = "Verificar",
                        subtitle = "Consultar se já foi liberada",
                        onClick = vm::retryPending,
                        centerText = true,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Voltar",
                        color = Gate8Colors.AccentBlue,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onBackToLogin)
                            .padding(vertical = 14.dp),
                    )
                }
            }
        }
    }
}
