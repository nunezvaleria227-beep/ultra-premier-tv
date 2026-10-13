package com.example.mitvplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.mitvplayer.UpdateManager

/** Avisos de versión nueva: pregunta si se quiere actualizar y muestra el avance de la descarga. */
@Composable
fun UpdateDialogs(vm: AppViewModel) {
    val context = LocalContext.current
    when (val state = vm.update) {
        is UpdateState.Available -> if (!vm.updateDismissed) {
            OptionsDialog(
                title = "Hay una versión nueva",
                message = "Ya está disponible la versión 1.0.${state.version} (tienes ${UpdateManager.currentName(context)}). " +
                    "Se descarga sola y se instala encima de la que tienes; no pierdes tu sesión ni tus favoritos.",
                options = listOf(
                    Opt("Actualizar ahora") { vm.startUpdate() },
                    Opt("Más tarde") { }
                ),
                onDismiss = { vm.updateDismissed = true }
            )
        }
        is UpdateState.Downloading -> Dialog(
            onDismissRequest = { },
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF1B2050))
                    .padding(22.dp)
                    .width(300.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Descargando la actualización…", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(Color(0x33FFFFFF), RoundedCornerShape(4.dp))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth((state.percent / 100f).coerceIn(0.02f, 1f))
                            .fillMaxHeight()
                            .background(AccentColor, RoundedCornerShape(4.dp))
                    )
                }
                Text("${state.percent}%", color = TextSecondary, fontSize = 13.sp)
            }
        }
        UpdateState.Idle -> Unit
    }
}
