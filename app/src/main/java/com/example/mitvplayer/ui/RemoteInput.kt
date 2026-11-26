package com.example.mitvplayer.ui

import android.content.res.Configuration
import android.os.SystemClock
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Campos de texto que se manejan con CUALQUIER control: el del Fire TV, el de la tele (HDMI-CEC), controles
 * universales, teclados o mandos.
 *
 * Por sí solo, Android solo saca el foco de un campo con las flechas cuando el control "es un control de Fire TV";
 * con otros controles el cursor se quedaba atrapado (no se podía bajar a "Entrar" ni moverse a otra parte).
 * Aquí: arriba/abajo/izquierda/derecha salen del campo y OK abre el teclado. Si el teclado del sistema no aparece
 * (pasa cuando el control se presenta como teclado), se abre el teclado propio de la app.
 */
fun Modifier.remoteTextInput(
    value: String,
    onValueChange: (String) -> Unit,
    title: String,
    secret: Boolean = false,
    /** El texto lo puso la app (no el cliente): al empezar a escribir se vacía primero, no se escribe encima. */
    clearFirst: Boolean = false,
    onClearFirst: () -> Unit = {}
): Modifier = composed {
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentValue by rememberUpdatedState(value)
    val currentOnChange by rememberUpdatedState(onValueChange)
    val currentClearFirst by rememberUpdatedState(clearFirst)
    val currentOnClearFirst by rememberUpdatedState(onClearFirst)
    var askedSystemKeyboardAt by remember { mutableStateOf(0L) }
    var askedWithValue by remember { mutableStateOf("") }

    fun openKeyboard() {
        val cleared = currentClearFirst
        if (cleared) currentOnClearFirst()
        val startValue = if (cleared) "" else currentValue
        val config = context.resources.configuration
        val hardKeyboard = config.keyboard == Configuration.KEYBOARD_QWERTY &&
            config.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO
        val now = SystemClock.uptimeMillis()
        // Si se pidió el teclado del sistema hace un momento, el OK volvió a llegar aquí y no se escribió nada,
        // es que ese teclado no apareció con este control: se abre el de la app.
        val systemFailed = askedSystemKeyboardAt != 0L && now - askedSystemKeyboardAt < 6_000 && currentValue == askedWithValue
        if (hardKeyboard || systemFailed) {
            askedSystemKeyboardAt = 0L
            TvKeyboard.open(title, startValue, secret) { currentOnChange(it) }
        } else {
            askedSystemKeyboardAt = now
            askedWithValue = startValue
            if (cleared) {
                // Primero se ve el campo vacío y luego sale el teclado (si no, el teclado agarra el texto viejo).
                scope.launch {
                    withFrameNanos { }
                    keyboard?.show()
                }
            } else {
                keyboard?.show()
            }
        }
    }

    onPreviewKeyEvent { event ->
        // Lo que manda el teclado en pantalla se deja tal cual.
        if (event.nativeKeyEvent.flags and KeyEvent.FLAG_SOFT_KEYBOARD != 0) return@onPreviewKeyEvent false
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        // Controles con letras (o teclados): escribir directo sobre un texto que puso la app lo reemplaza.
        val native = event.nativeKeyEvent
        if (currentClearFirst && native.isPrintingKey && native.unicodeChar >= 32) {
            currentOnClearFirst()
            currentOnChange(native.unicodeChar.toChar().toString())
            return@onPreviewKeyEvent true
        }
        when (event.key) {
            Key.DirectionUp -> focusManager.moveFocus(FocusDirection.Up)
            Key.DirectionDown -> focusManager.moveFocus(FocusDirection.Down)
            Key.DirectionLeft -> focusManager.moveFocus(FocusDirection.Left)
            Key.DirectionRight -> focusManager.moveFocus(FocusDirection.Right)
            Key.DirectionCenter -> {
                openKeyboard()
                true
            }
            // Controles cuyo OK es "Enter": en un campo vacío abre el teclado; con texto hace lo de siempre (siguiente/entrar).
            Key.Enter, Key.NumPadEnter -> if (currentValue.isEmpty()) {
                openKeyboard()
                true
            } else {
                false
            }
            else -> false
        }
    }
}

/** El teclado propio que se abre encima de todo (se dibuja una sola vez, en la raíz de la app). */
object TvKeyboard {
    class Request(
        val title: String,
        val initial: String,
        val secret: Boolean,
        val onChange: (String) -> Unit
    )

    var request by mutableStateOf<Request?>(null)
        private set

    fun open(title: String, initial: String, secret: Boolean, onChange: (String) -> Unit) {
        request = Request(title, initial, secret, onChange)
    }

    fun close() {
        request = null
    }
}

private const val LETTERS = "qwertyuiopasdfghjklñzxcvbnm"
private val DIGITS = "1234567890".toList()
private val SYMBOLS_1 = "@.-_!#$%&*".toList()
private val SYMBOLS_2 = "+/?:=~^(),".toList()

/** Va en la raíz de la app (y dentro de otras ventanas si hace falta): muestra el teclado cuando se pide. */
@Composable
fun TvKeyboardHost() {
    val request = TvKeyboard.request ?: return
    TvKeyboardDialog(request)
}

@Composable
private fun TvKeyboardDialog(request: TvKeyboard.Request) {
    var text by remember(request) { mutableStateOf(request.initial) }
    var upper by remember(request) { mutableStateOf(false) }
    var symbols by remember(request) { mutableStateOf(false) }
    val firstKey = remember { FocusRequester() }
    val accent = MaterialTheme.colorScheme.primary

    fun set(value: String) {
        text = value
        request.onChange(value)
    }

    LaunchedEffect(request) {
        withFrameNanos { }
        withFrameNanos { }
        try {
            firstKey.requestFocus()
        } catch (e: Exception) {
            // Sin foco inicial: el primer toque de flecha lo pone.
        }
    }

    Dialog(
        onDismissRequest = { TvKeyboard.close() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xF2121216))
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp))
                // Si el control también tiene letras (teclado), se puede escribir directo.
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val native = event.nativeKeyEvent
                    when {
                        native.keyCode == KeyEvent.KEYCODE_DEL -> {
                            if (text.isNotEmpty()) set(text.dropLast(1))
                            true
                        }
                        native.isPrintingKey && native.unicodeChar >= 32 -> {
                            set(text + native.unicodeChar.toChar())
                            true
                        }
                        else -> false
                    }
                }
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(request.title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x22FFFFFF))
                    .border(2.dp, accent, RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                // En la contraseña solo se ve la última letra que se escribió.
                val shown = if (request.secret && text.isNotEmpty()) "•".repeat(text.length - 1) + text.last() else text
                Text(
                    text = if (text.isEmpty()) "Escribe con las flechas y OK" else shown + "▏",
                    color = if (text.isEmpty()) Color(0x99FFFFFF) else Color.White,
                    fontSize = 18.sp,
                    maxLines = 1
                )
            }

            val rows: List<List<Char>> = if (symbols) {
                listOf(DIGITS, SYMBOLS_1, SYMBOLS_2)
            } else {
                val letters = if (upper) LETTERS.uppercase() else LETTERS
                listOf(DIGITS, letters.substring(0, 10).toList(), letters.substring(10, 20).toList(), letters.substring(20).toList() + listOf('.', '-', '_'))
            }
            rows.forEachIndexed { r, keys ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    keys.forEachIndexed { c, ch ->
                        KbKey(
                            label = ch.toString(),
                            accent = accent,
                            modifier = Modifier
                                .width(46.dp)
                                .then(if (r == 0 && c == 0) Modifier.focusRequester(firstKey) else Modifier)
                        ) { set(text + ch) }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                KbKey(if (upper) "abc" else "ABC", accent, Modifier.width(70.dp)) {
                    symbols = false
                    upper = !upper
                }
                KbKey(if (symbols) "Letras" else "#+=", accent, Modifier.width(70.dp)) { symbols = !symbols }
                KbKey("Espacio", accent, Modifier.width(120.dp)) { set("$text ") }
                KbKey("Borrar", accent, Modifier.width(90.dp)) { if (text.isNotEmpty()) set(text.dropLast(1)) }
                KbKey("Listo", accent, Modifier.width(90.dp), strong = true) { TvKeyboard.close() }
            }
        }
    }
}

@Composable
private fun KbKey(
    label: String,
    accent: Color,
    modifier: Modifier = Modifier,
    strong: Boolean = false,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .height(40.dp)
            .onFocusChanged { focused = it.isFocused }
            .clip(shape)
            .background(if (focused) accent else if (strong) accent.copy(alpha = 0.35f) else Color(0x1AFFFFFF))
            .border(if (focused) 2.dp else 1.dp, if (focused) Color.White else Color(0x22FFFFFF), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = if (label.length > 1) 13.sp else 17.sp,
            fontWeight = if (focused || strong) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}
