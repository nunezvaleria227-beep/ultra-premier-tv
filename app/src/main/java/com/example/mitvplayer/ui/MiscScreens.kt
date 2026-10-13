package com.example.mitvplayer.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mitvplayer.RecordingManager
import com.example.mitvplayer.RecordingService
import com.example.mitvplayer.SavedRecording
import com.example.mitvplayer.data.LiveChannel
import kotlinx.coroutines.delay
import java.io.File

/** Página sencilla con barra superior y contenido centrado. */
@Composable
private fun SimplePage(
    vm: AppViewModel,
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    AppBackground {
        Column(Modifier.fillMaxSize()) {
            TopBar(title = title, centerTitle = true, onBack = { vm.back() })
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    content = content
                )
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, color = TextSecondary)
        Text(text = value, color = TextPrimary, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PanelColor, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        content = content
    )
}

// --------------------------------------------------------------------- cuenta

@Composable
fun AccountScreen(vm: AppViewModel) {
    val account = vm.account
    val now = System.currentTimeMillis()
    val expires = account?.expiresAt?.let { formatDateLong(it * 1000) } ?: "Sin vencimiento"
    val daysLeft = account?.expiresAt?.let { (it * 1000 - now) / 86_400_000L }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        first.tryRequestFocus()
    }

    SimplePage(vm, "Mi cuenta") {
        Card {
            InfoRow("Usuario", account?.username ?: "—")
            InfoRow("Estado", account?.status ?: "—")
            InfoRow("Vence", expires)
            if (daysLeft != null) InfoRow("Días restantes", daysLeft.coerceAtLeast(0).toString())
            InfoRow("Conexiones", "${account?.activeConnections ?: "—"} de ${account?.maxConnections ?: "—"}")
        }
        TvButton(
            text = "Cerrar sesión",
            onClick = { vm.logout() },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(first)
        )
    }
}

// ---------------------------------------------------------------------- avisos

@Composable
fun NotificationsScreen(vm: AppViewModel) {
    val account = vm.account
    val now = System.currentTimeMillis()
    val expiresAt = account?.expiresAt
    val daysLeft = expiresAt?.let { (it * 1000 - now) / 86_400_000L }
    val message = account?.message

    SimplePage(vm, "Avisos") {
        var any = false
        if (!message.isNullOrBlank()) {
            any = true
            Card {
                Text("Mensaje de tu proveedor", color = AccentColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(message, color = TextPrimary)
            }
        }
        if (daysLeft != null) {
            any = true
            val text = when {
                daysLeft < 0 -> "Tu cuenta venció el ${formatDateLong((expiresAt ?: 0L) * 1000)}."
                daysLeft == 0L -> "Tu cuenta vence hoy."
                daysLeft <= 7 -> "Tu cuenta vence en $daysLeft día(s), el ${formatDateLong((expiresAt ?: 0L) * 1000)}."
                else -> "Tu cuenta está activa hasta el ${formatDateLong((expiresAt ?: 0L) * 1000)} ($daysLeft días)."
            }
            Card {
                Text("Vencimiento", color = AccentColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(text, color = if (daysLeft <= 7) Color(0xFFFFC93C) else TextPrimary)
            }
        }
        val maxConnections = account?.maxConnections
        if (maxConnections != null) {
            any = true
            Card {
                Text("Conexiones", color = AccentColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(
                    "Tu cuenta permite $maxConnections conexión(es) a la vez. " +
                        "Si ves un error de \"máximo de conexiones\", cierra la reproducción en otro dispositivo.",
                    color = TextPrimary
                )
            }
        }
        if (!any) {
            Text("No hay avisos por ahora.", color = TextSecondary)
        }
    }
}

// --------------------------------------------------------------------- ajustes

@Composable
fun SettingsScreen(vm: AppViewModel) {
    var confirm by remember { mutableStateOf<String?>(null) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        first.tryRequestFocus()
    }
    val formatName = if (vm.liveFormat == "m3u8") "HLS (m3u8)" else "MPEG-TS"

    SimplePage(vm, "Ajustes") {
        TvButton(
            text = "Formato de TV en vivo: $formatName (cambiar)",
            onClick = { vm.toggleLiveFormat() },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(first)
        )
        Text(
            "Si un canal se corta o no abre, prueba cambiando el formato.",
            color = TextSecondary,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(4.dp))
        TvButton(
            text = "Actualizar todas las listas ahora",
            onClick = { vm.refreshAll() },
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            "Vuelve a descargar canales, películas y series. Se actualizan solas cada 12 horas.",
            color = TextSecondary,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(4.dp))
        TvButton(
            text = "Borrar favoritos",
            onClick = { confirm = "favoritos" },
            modifier = Modifier.fillMaxWidth()
        )
        TvButton(
            text = "Borrar historial",
            onClick = { confirm = "historial" },
            modifier = Modifier.fillMaxWidth()
        )
    }

    val what = confirm
    if (what != null) {
        OptionsDialog(
            title = "¿Borrar $what?",
            message = "Esta acción no se puede deshacer.",
            options = listOf(
                Opt("Sí, borrar") { if (what == "favoritos") vm.clearFavorites() else vm.clearHistory() },
                Opt("Cancelar") { }
            ),
            onDismiss = { confirm = null }
        )
    }
}

// ------------------------------------------------------------------ grabaciones

@Composable
fun RecordingsScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val active = RecordingManager.current
    var files by remember { mutableStateOf(RecordingService.listSaved(context)) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var deleting by remember { mutableStateOf<SavedRecording?>(null) }
    var picking by remember { mutableStateOf(false) }

    // Cuando termina una grabación se actualiza la lista; mientras graba se actualiza el tiempo.
    LaunchedEffect(active == null) { files = RecordingService.listSaved(context) }
    LaunchedEffect(active != null) {
        while (active != null) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    SimplePage(vm, "Grabaciones") {
        Text(
            "Cada grabación dura como máximo 2 horas. Al terminar se guarda sola en la memoria de este dispositivo (Películas / Ultra Premier TV).",
            color = TextSecondary,
            fontSize = 12.sp
        )
        if (active != null) {
            Card {
                Text("● Grabando: ${active.channelName}", color = Color(0xFFFF6B6B), fontWeight = FontWeight.Bold)
                val elapsed = ((now - active.startedAt) / 1000).coerceAtLeast(0)
                val remaining = formatDuration(((active.endsAt - now) / 1000).coerceAtLeast(0))
                Text(
                    "${formatDuration(elapsed)} · ${formatSize(active.bytes)} · se guarda sola en $remaining",
                    color = TextPrimary
                )
                TvButton(text = "Detener y guardar", onClick = { RecordingManager.stop() }, modifier = Modifier.fillMaxWidth())
            }
        } else {
            TvButton(text = "● Grabar un canal…", onClick = { picking = true }, modifier = Modifier.fillMaxWidth())
        }

        if (files.isEmpty()) {
            Text(
                "Todavía no hay grabaciones. Abre En vivo, elige un canal y pulsa \"Grabar\".",
                color = TextSecondary,
                fontSize = 13.sp
            )
        }
        files.forEach { recording ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FocusCard(
                    onClick = { vm.playFile(context, recording.title, recording.url) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    scaleOnFocus = 1.01f
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            recording.title.replace('_', ' '),
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${formatSize(recording.size)} · ${formatDateLong(recording.dateMs)} ${formatClock(recording.dateMs)}",
                            color = TextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
                TvButton(text = "Borrar", onClick = { deleting = recording }, compact = true)
            }
        }
    }

    val toDelete = deleting
    if (toDelete != null) {
        OptionsDialog(
            title = "¿Borrar esta grabación?",
            message = toDelete.title.replace('_', ' '),
            options = listOf(
                Opt("Sí, borrar") {
                    if (!RecordingService.delete(context, toDelete)) {
                        Toast.makeText(context, "No se pudo borrar. Bórrala desde la app de Archivos.", Toast.LENGTH_LONG).show()
                    }
                    files = RecordingService.listSaved(context)
                },
                Opt("Cancelar") { }
            ),
            onDismiss = { deleting = null }
        )
    }

    if (picking) {
        ChannelPickerDialog(
            vm = vm,
            onPick = { channel ->
                picking = false
                vm.startRecording(context, channel)
            },
            onDismiss = { picking = false }
        )
    }
}
