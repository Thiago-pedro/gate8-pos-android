package br.com.gate8.pos.ui.login

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import br.com.gate8.pos.R
import br.com.gate8.pos.ui.common.Gate8HeaderLogo
import br.com.gate8.pos.ui.common.Gate8MenuButton
import br.com.gate8.pos.ui.common.Gate8ScreenBackground
import br.com.gate8.pos.ui.theme.Gate8Colors
import org.koin.androidx.compose.koinViewModel

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
    val artHeight = remember {
        val metrics = context.resources.displayMetrics
        (metrics.heightPixels / metrics.density).dp
    }

    DisposableEffect(context) {
        val window = context.findActivity()?.window
        val previousMode = window?.attributes?.softInputMode
        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
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
                LoginNavigation.Home -> onHome()
                LoginNavigation.Pending -> onPending()
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.login_token_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .requiredHeight(artHeight),
        )
        Column(
            Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(40.dp))
            Gate8HeaderLogo(height = 52.dp)
            Spacer(Modifier.height(63.dp))

            val cardShape = RoundedCornerShape(16.dp)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(cardShape)
                    .background(Color.White.copy(alpha = 0.42f))
                    .border(1.dp, Gate8Colors.AccentBlue.copy(alpha = 0.32f), cardShape)
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Token",
                    color = Gate8Colors.TextOnLight,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(14.dp))
                ProducerTokenBoxes(
                    value = state.producerToken,
                    onValueChange = vm::onProducerTokenChange,
                )

                state.error?.let {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        it,
                        color = Gate8Colors.Error,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(Modifier.height(20.dp))
                if (state.loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        color = Gate8Colors.AccentBlue,
                    )
                } else {
                    Gate8MenuButton(
                        title = "Entrar",
                        subtitle = "Vincular esta maquininha ao produtor",
                        onClick = vm::login,
                        enabled = state.producerToken.length == 6,
                        centerText = true,
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ProducerTokenBoxes(
    value: String,
    onValueChange: (String) -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val shape = RoundedCornerShape(10.dp)

    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val gap = 6.dp
        val boxSize = ((maxWidth - gap * 5) / 6).coerceAtMost(48.dp)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .focusRequester(focusRequester)
                .fillMaxWidth(),
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
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            focusRequester.requestFocus()
                            keyboard?.show()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(1.dp).alpha(0f)) { innerTextField() }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        repeat(6) { index ->
                            Box(
                                modifier = Modifier
                                    .size(boxSize)
                                    .clip(shape)
                                    .background(Color.White)
                                    .border(2.dp, Gate8Colors.AccentBlue, shape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = value.getOrNull(index)?.toString().orEmpty(),
                                    color = Gate8Colors.TextOnLight,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            },
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
