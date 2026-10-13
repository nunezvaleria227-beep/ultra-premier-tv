package com.example.mitvplayer.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.mitvplayer.data.SubtitleSettings

/** Ventana para escribir la clave (y, si se quiere, el usuario) de OpenSubtitles. */
@Composable
fun SubtitleAccountDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val existing = remember { SubtitleSettings.credentials(context) }
    var apiKey by remember { mutableStateOf(existing?.apiKey.orEmpty()) }
    var username by remember { mutableStateOf(existing?.username.orEmpty()) }
    var password by remember { mutableStateOf("") }

    val colors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = AccentColor,
        unfocusedBorderColor = Color(0x66FFFFFF),
        focusedLabelColor = AccentColor,
        unfocusedLabelColor = TextSecondary,
        cursorColor = AccentColor,
        focusedTextColor = TextPrimary,
        unfocusedTextColor = TextPrimary
    )

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 460.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1B2050))
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Subtítulos en internet", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(
                "Para buscar subtítulos en español o inglés cuando la película no los trae, la app usa OpenSubtitles " +
                    "(gratis). Crea una cuenta en opensubtitles.com, abre \"API consumers\" y crea tu clave. " +
                    "El usuario y la contraseña son opcionales, pero dan más descargas al día.",
                color = TextSecondary,
                fontSize = 12.sp
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("Clave (API key)") },
                singleLine = true,
                colors = colors,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Usuario (opcional)") },
                singleLine = true,
                colors = colors,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(if (existing?.password != null) "Contraseña (déjala vacía para no cambiarla)" else "Contraseña (opcional)") },
                singleLine = true,
                colors = colors,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            ) {
                TvButton(
                    text = "Guardar",
                    onClick = {
                        val saved = SubtitleSettings.saveCredentials(
                            context,
                            apiKey,
                            username,
                            password.ifEmpty { existing?.password.orEmpty() }
                        )
                        Toast.makeText(
                            context,
                            if (saved) "Guardado" else "Este equipo no pudo guardarlo de forma segura.",
                            Toast.LENGTH_LONG
                        ).show()
                        if (saved) onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                )
                TvButton(text = "Cancelar", onClick = onDismiss, modifier = Modifier.weight(1f))
            }
        }
    }
}
