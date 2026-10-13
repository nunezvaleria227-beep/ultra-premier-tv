package com.example.mitvplayer.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mitvplayer.R
import com.example.mitvplayer.RecordingScheduler
import com.example.mitvplayer.data.EpgEntry
import com.example.mitvplayer.data.LiveChannel
import kotlinx.coroutines.delay

private const val SLOT_MIN = 30
private const val WINDOW_SLOTS = 8
private val DP_PER_MIN = 4.dp
private val CHANNEL_COLUMN = 118.dp
private val ROW_HEIGHT = 56.dp

/**
 * Guía de TV en cuadrícula: los canales de una categoría en filas y las horas en columnas.
 * Se desplaza de lado (dedo o flechas del control) y con "◀ 3 h" / "3 h ▶" se mueve por el día.
 * Al elegir un programa: ver, grabar lo que queda, programar su grabación o verlo en Catch up.
 */
@Composable
fun EpgGridScreen(vm: AppViewModel, categoryId: String, title: String) {
    val context = LocalContext.current
    val state = vm.live
    val channels = remember(categoryId, state.visibleItems) { state.itemsFor(categoryId) }
    var nowSec by remember { mutableStateOf(System.currentTimeMillis() / 1000) }
    var shiftHours by remember { mutableStateOf(0) }
    var picked by remember { mutableStateOf<Pair<LiveChannel, EpgEntry>?>(null) }
    val hScroll = rememberScrollState()

    LaunchedEffect(Unit) { state.ensureLoaded() }
    LaunchedEffect(Unit) {
        while (true) {
            nowSec = System.currentTimeMillis() / 1000
            delay(30_000)
        }
    }
    LaunchedEffect(shiftHours) { hScroll.scrollTo(0) }

    val slotSec = SLOT_MIN * 60L
    // La cuadrícula empieza media hora antes de la media hora actual.
    val windowStart = (nowSec / slotSec) * slotSec - slotSec + shiftHours * 3600L
    val windowEnd = windowStart + WINDOW_SLOTS * slotSec
    val slotWidth: Dp = DP_PER_MIN * SLOT_MIN.toFloat()
    val totalWidth: Dp = slotWidth * WINDOW_SLOTS.toFloat()

    AppBackground(art = R.drawable.art_live, dim = 0.62f) {
        Column(Modifier.fillMaxSize()) {
            TopBar(
                title = title,
                centerTitle = true,
                onBack = { vm.back() },
                trailing = {
                    TvButton(text = "◀ 3 h", onClick = { shiftHours -= 3 }, compact = true)
                    Spacer(Modifier.width(6.dp))
                    TvButton(text = "Ahora", onClick = { shiftHours = 0 }, compact = true, selected = shiftHours == 0)
                    Spacer(Modifier.width(6.dp))
                    TvButton(text = "3 h ▶", onClick = { shiftHours += 3 }, compact = true)
                    Spacer(Modifier.width(8.dp))
                }
            )

            // Encabezado con las horas
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatDayShort(windowStart * 1000),
                    color = TextSecondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    modifier = Modifier
                        .width(CHANNEL_COLUMN)
                        .padding(start = 10.dp)
                )
                Box(
                    Modifier
                        .weight(1f)
                        .horizontalScroll(hScroll, enabled = false)
                ) {
                    Row(Modifier.width(totalWidth)) {
                        repeat(WINDOW_SLOTS) { i ->
                            Text(
                                text = formatHm(windowStart + i * slotSec),
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                modifier = Modifier
                                    .width(slotWidth)
                                    .padding(start = 4.dp)
                            )
                        }
                    }
                }
            }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (channels.isEmpty()) {
                    CenterMessage(if (state.catalog == null) "Cargando canales…" else "No hay canales en esta categoría")
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        itemsIndexed(channels) { _, channel ->
                            GuideRow(
                                vm = vm,
                                channel = channel,
                                windowStart = windowStart,
                                windowEnd = windowEnd,
                                nowSec = nowSec,
                                totalWidth = totalWidth,
                                scroll = hScroll,
                                onPick = { entry -> picked = channel to entry }
                            )
                        }
                    }
                }
            }
        }
    }

    val target = picked
    if (target != null) {
        val channel = target.first
        val entry = target.second
        val airing = nowSec >= entry.start && nowSec < entry.end
        val past = entry.end <= nowSec
        val scheduleId = RecordingScheduler.store(context).idFor(channel.id, entry.start)
        val scheduled = vm.isScheduled(channel.id, entry.start)
        val channelIndex = channels.indexOfFirst { it.id == channel.id }.coerceAtLeast(0)

        val options = ArrayList<Opt>()
        if (airing) {
            options.add(Opt("Ver ahora") { vm.playLive(context, channels, channelIndex) })
            options.add(
                Opt("Grabar lo que queda") {
                    vm.startRecording(context, channel, ((entry.end - nowSec) / 60).toInt() + 3)
                }
            )
        } else if (past) {
            if (entry.hasArchive || channel.archiveDays > 0) {
                options.add(Opt("Ver desde el archivo (Catch up)") { vm.playArchive(context, channel, entry) })
            }
        } else {
            if (scheduled) {
                options.add(Opt("Quitar grabación programada") { vm.cancelScheduled(context, scheduleId) })
            } else {
                options.add(Opt("Programar grabación") { vm.scheduleRecording(context, channel, entry) })
            }
            options.add(Opt("Ver este canal ahora") { vm.playLive(context, channels, channelIndex) })
        }
        options.add(Opt("Cerrar") { })

        val description = entry.description.trim().take(200)
        OptionsDialog(
            title = entry.title.ifBlank { channel.name },
            message = "${channel.name} · ${formatHm(entry.start)} – ${formatHm(entry.end)}" +
                (if (description.isNotEmpty()) "\n\n$description" else "") +
                (if (!airing && !past && entry.end - entry.start > 7200) "\n\nSe grabarán como máximo 2 horas." else ""),
            options = options,
            onDismiss = { picked = null }
        )
    }
}

@Composable
private fun GuideRow(
    vm: AppViewModel,
    channel: LiveChannel,
    windowStart: Long,
    windowEnd: Long,
    nowSec: Long,
    totalWidth: Dp,
    scroll: ScrollState,
    onPick: (EpgEntry) -> Unit
) {
    val table by produceState<List<EpgEntry>?>(null, channel.id) { value = vm.epgTableFor(channel.id) }
    val programs = remember(table, windowStart) {
        table.orEmpty().filter { it.end > windowStart && it.start < windowEnd }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .width(CHANNEL_COLUMN)
                .fillMaxHeight()
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChannelLogo(icon = channel.icon, name = channel.name, size = 34.dp)
            Spacer(Modifier.width(6.dp))
            Text(
                text = channel.name,
                color = TextPrimary,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .horizontalScroll(scroll)
        ) {
            Box(
                Modifier
                    .width(totalWidth)
                    .fillMaxHeight()
            ) {
                if (table == null) {
                    Text(
                        text = "Cargando…",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .align(Alignment.CenterStart)
                    )
                } else if (programs.isEmpty()) {
                    Text(
                        text = "Sin información de programación",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .align(Alignment.CenterStart)
                    )
                }
                programs.forEach { entry ->
                    val from = maxOf(entry.start, windowStart)
                    val to = minOf(entry.end, windowEnd)
                    val width = DP_PER_MIN * ((to - from) / 60f) - 3.dp
                    if (width > 12.dp) {
                        val airing = nowSec >= entry.start && nowSec < entry.end
                        val past = entry.end <= nowSec
                        val scheduled = vm.isScheduled(channel.id, entry.start)
                        FocusCard(
                            onClick = { onPick(entry) },
                            modifier = Modifier
                                .offset(x = DP_PER_MIN * ((from - windowStart) / 60f))
                                .width(width)
                                .fillMaxHeight()
                                .padding(vertical = 3.dp),
                            shape = RoundedCornerShape(6.dp),
                            scaleOnFocus = 1f,
                            background = when {
                                airing -> Color(0xFF2A4BA0)
                                past -> Color(0x66232A63)
                                else -> PanelColor
                            },
                            focusedBackground = PanelFocusedColor
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = (if (scheduled) "● " else "") + entry.title.ifBlank { "Sin título" },
                                    color = if (scheduled) Color(0xFFFF8A8A) else TextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${formatHm(entry.start)} – ${formatHm(entry.end)}",
                                    color = TextSecondary,
                                    fontSize = 10.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
