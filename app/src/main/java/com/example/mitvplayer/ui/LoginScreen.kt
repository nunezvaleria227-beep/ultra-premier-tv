package com.example.mitvplayer.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mitvplayer.Config

/**
 * Entrar a Ultra Premier TV (estilo deportivo): el logo grande en un panel azul en diagonal
 * y, a la derecha, usuario, contraseña y el botón blanco para entrar.
 */
@Composable
fun LoginScreen(vm: AppViewModel) {
    val focusManager = LocalFocusManager.current
    val needsServer = Config.PORTAL_URL.isBlank()
    val state = vm.loginState

    AppBackground(dim = 0.45f) {
        // Panel azul en diagonal con una franja blanca.
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val top = w * 0.585f
            val bottom = w * 0.46f
            val panel = Path().apply {
                moveTo(0f, 0f)
                lineTo(top, 0f)
                lineTo(bottom, h)
                lineTo(0f, h)
                close()
            }
            drawPath(panel, Brush.linearGradient(listOf(AccentColor, AccentDeep), start = Offset.Zero, end = Offset(top, h)))
            val stripe = Path().apply {
                val gap = w * 0.006f
                val sw = w * 0.012f
                moveTo(top + gap, 0f)
                lineTo(top + gap + sw, 0f)
                lineTo(bottom + gap + sw, h)
                lineTo(bottom + gap, h)
                close()
            }
            drawPath(stripe, Color.White.copy(alpha = 0.9f))
        }

        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 55.dp)
                .width(360.dp)
        ) {
            AppLogo(Modifier.width(290.dp))
            Spacer(Modifier.height(20.dp))
            Text("TV EN VIVO, PELÍCULAS\nY SERIES", style = sporty(20f), color = Color.White, lineHeight = 22.sp)
            Spacer(Modifier.height(6.dp))
            Text("Todo en un solo lugar", color = Color(0xFFDCE6FF), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }

        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 560.dp)
                .width(320.dp)
        ) {
            Text("ENTRAR", style = sporty(27f), color = TextPrimary)
            Text(
                "Escribe el usuario y la contraseña de tu cuenta.",
                color = TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 4.dp)
            )
            if (needsServer) {
                SlantField(
                    label = "SERVIDOR",
                    value = vm.server,
                    onValueChange = { vm.server = it },
                    placeholder = "http://servidor.com:8080",
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next,
                    onAction = { focusManager.moveFocus(FocusDirection.Down) }
                )
            }
            SlantField(
                label = "USUARIO",
                value = vm.username,
                onValueChange = { vm.username = it },
                placeholder = "Escribe tu usuario",
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next,
                onAction = { focusManager.moveFocus(FocusDirection.Down) }
            )
            SlantField(
                label = "CONTRASEÑA",
                value = vm.password,
                onValueChange = { vm.password = it },
                placeholder = "Escribe tu contraseña",
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
                onAction = {
                    focusManager.clearFocus()
                    vm.submitLogin()
                }
            )
            val error = state.error
            if (error != null) {
                Text(
                    text = error,
                    color = ErrorColor,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 10.dp)
                )
            }
            Spacer(Modifier.height(if (error != null) 12.dp else 20.dp))
            EnterButton(
                text = if (state.loading) "ENTRANDO… UN MOMENTO" else "ENTRAR  ›",
                enabled = !state.loading,
                onClick = { vm.submitLogin() }
            )
            Text(
                "Si tienes problemas para entrar, contacta a tu proveedor.",
                color = TextTertiary,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 13.dp)
            )
        }
    }
}

@Composable
private fun SlantField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    imeAction: ImeAction,
    onAction: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = SlantShape(8.dp)
    Text(
        text = label,
        color = TextTertiary,
        fontSize = 7.5.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 1.5.sp,
        modifier = Modifier.padding(top = 16.dp, bottom = 5.dp)
    )
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = UltraFont),
        cursorBrush = SolidColor(AccentLight),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(onNext = { onAction() }, onDone = { onAction() }),
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused },
        decorationBox = { inner ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .shadow(if (focused) 14.dp else 0.dp, shape, ambientColor = AccentColor, spotColor = AccentColor)
                    .clip(shape)
                    .background(if (focused) AccentColor.copy(alpha = 0.18f) else Color(0x10FFFFFF))
                    .border(if (focused) 1.5.dp else 1.dp, if (focused) Color.White else GlassBorder, shape)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f)) {
                    if (value.isEmpty()) {
                        Text(placeholder, color = TextTertiary, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                    }
                    inner()
                }
            }
        }
    )
}

@Composable
private fun EnterButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.04f else 1f, label = "enter")
    val shape = SlantShape(8.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .scale(scale)
            .shadow(if (focused) 20.dp else 10.dp, shape, ambientColor = AccentColor, spotColor = if (focused) AccentLight else Color.White)
            .clip(shape)
            .background(if (focused) Color.White else Color(0xFFE9F0FF))
            .border(if (focused) 2.dp else 0.dp, if (focused) AccentLight else Color.Transparent, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .height(40.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = sporty(14f), color = AccentDeep)
    }
}
