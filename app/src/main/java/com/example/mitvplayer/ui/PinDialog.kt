package com.example.mitvplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

const val PIN_LENGTH = 4

private val DigitKeys = mapOf(
    Key.Zero to '0', Key.One to '1', Key.Two to '2', Key.Three to '3', Key.Four to '4',
    Key.Five to '5', Key.Six to '6', Key.Seven to '7', Key.Eight to '8', Key.Nine to '9',
    Key.NumPad0 to '0', Key.NumPad1 to '1', Key.NumPad2 to '2', Key.NumPad3 to '3', Key.NumPad4 to '4',
    Key.NumPad5 to '5', Key.NumPad6 to '6', Key.NumPad7 to '7', Key.NumPad8 to '8', Key.NumPad9 to '9'
)

/**
 * Teclado numérico para escribir el PIN. Se maneja con el control remoto (flechas + OK),
 * con toques o con las teclas numéricas.
 *
 * [onSubmit] recibe los 4 dígitos y devuelve el texto del error, o null si todo salió bien
 * (entonces quien lo llamó decide si cierra la ventana o pide el siguiente paso).
 */
@Composable
fun PinDialog(
    title: String,
    message: String,
    onSubmit: (String) -> String?,
    onDismiss: () -> Unit
) {
    var digits by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        withFrameNanos { }
        first.tryRequestFocus()
    }

    fun press(digit: Char) {
        if (digits.length >= PIN_LENGTH) return
        digits += digit
        error = null
        if (digits.length == PIN_LENGTH) {
            val entered = digits
            val result = onSubmit(entered)
            if (result != null) {
                error = result
                digits = ""
            }
        }
    }

    fun backspace() {
        if (digits.isNotEmpty()) digits = digits.dropLast(1)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 420.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1B2050))
                .padding(horizontal = 18.dp, vertical = 14.dp)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val digit = DigitKeys[event.key]
                    when {
                        digit != null -> {
                            press(digit)
                            true
                        }
                        event.key == Key.Backspace || event.key == Key.Delete -> {
                            backspace()
                            true
                        }
                        else -> false
                    }
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp, textAlign = TextAlign.Center)
            Text(message, color = TextSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                repeat(PIN_LENGTH) { index ->
                    Box(
                        Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(if (index < digits.length) AccentColor else Color(0x44FFFFFF))
                    )
                }
            }

            val shown = error
            if (shown != null) {
                Text(shown, color = ErrorColor, fontSize = 12.sp, textAlign = TextAlign.Center)
            } else {
                Box(Modifier.height(15.dp))
            }

            val rows = listOf(
                listOf("1", "2", "3", "4", "5", "6"),
                listOf("7", "8", "9", "0", "⌫", "✕")
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rows.forEachIndexed { rowIndex, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEachIndexed { colIndex, label ->
                            FocusCard(
                                onClick = {
                                    when (label) {
                                        "⌫" -> backspace()
                                        "✕" -> onDismiss()
                                        else -> press(label[0])
                                    }
                                },
                                modifier = Modifier
                                    .size(width = 48.dp, height = 44.dp)
                                    .then(if (rowIndex == 0 && colIndex == 0) Modifier.focusRequester(first) else Modifier),
                                shape = RoundedCornerShape(10.dp),
                                scaleOnFocus = 1.08f
                            ) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(label, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
