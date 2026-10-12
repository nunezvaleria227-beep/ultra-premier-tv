package com.example.mitvplayer.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private const val FOCUS_COUNT = 16

@Composable
fun HomeScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val requesters = remember { List(FOCUS_COUNT) { FocusRequester() } }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    var confirmExit by remember { mutableStateOf(false) }

    // Al entrar (o al volver de otra pantalla) el foco regresa al último botón usado.
    LaunchedEffect(Unit) {
        withFrameNanos { }
        withFrameNanos { }
        requesters[vm.homeFocus.coerceIn(0, FOCUS_COUNT - 1)].tryRequestFocus()
    }
    // Reloj y "hace X min" se actualizan solos.
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(15_000)
        }
    }

    fun Modifier.slot(index: Int): Modifier =
        this.focusRequester(requesters[index]).onFocusChanged { if (it.isFocused) vm.homeFocus = index }

    val account = vm.account
    val expiresAt = account?.expiresAt
    val daysLeft = expiresAt?.let { (it * 1000 - now) / 86_400_000L }
    val hasAlert = (daysLeft != null && daysLeft <= 7) || !account?.message.isNullOrBlank()

    AppBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 12.dp)
        ) {
            // ------------------------------------------------ encabezado
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AppLogo(Modifier.height(50.dp))
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(formatClock(now), color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(formatDateLong(now), color = TextSecondary, fontSize = 12.sp)
                }
                Spacer(Modifier.weight(1f))
                FocusCard(
                    onClick = { vm.open(Screen.Search(SearchScope.ALL)) },
                    modifier = Modifier.slot(9),
                    shape = RoundedCornerShape(24.dp),
                    scaleOnFocus = 1.04f
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Búsqueda maestra", color = TextPrimary, fontSize = 14.sp, maxLines = 1)
                    }
                }
                Spacer(Modifier.weight(1f))
                IconAction(onClick = { vm.open(Screen.Radio) }, modifier = Modifier.slot(10), size = 44.dp) {
                    GlyphIcon(Glyph.RADIO, Modifier.size(28.dp))
                }
                IconAction(onClick = { vm.open(Screen.Notifications) }, modifier = Modifier.slot(11), size = 44.dp) {
                    Icon(Icons.Filled.Notifications, contentDescription = "Avisos", tint = Color.White, modifier = Modifier.size(28.dp))
                    if (hasAlert) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 8.dp, end = 8.dp)
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFF4D4D))
                        )
                    }
                }
                IconAction(onClick = { vm.open(Screen.Account) }, modifier = Modifier.slot(12), size = 44.dp) {
                    Icon(Icons.Filled.Person, contentDescription = "Mi cuenta", tint = Color.White, modifier = Modifier.size(28.dp))
                }
                IconAction(onClick = { vm.open(Screen.Recordings) }, modifier = Modifier.slot(13), size = 44.dp) {
                    GlyphIcon(Glyph.REC, Modifier.size(28.dp))
                }
                IconAction(onClick = { vm.open(Screen.Settings) }, modifier = Modifier.slot(14), size = 44.dp) {
                    Icon(Icons.Filled.Settings, contentDescription = "Ajustes", tint = Color.White, modifier = Modifier.size(28.dp))
                }
                IconAction(onClick = { confirmExit = true }, modifier = Modifier.slot(15), size = 44.dp) {
                    GlyphIcon(Glyph.POWER, Modifier.size(28.dp))
                }
            }

            // ------------------------------------------------ tres mosaicos
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                SectionTile(
                    title = "TV EN DIRECTO",
                    glyph = Glyph.TV,
                    status = statusText(vm.live, now),
                    onOpen = { vm.open(Screen.Live) },
                    onRefresh = { vm.live.refresh() },
                    openModifier = Modifier.slot(0),
                    refreshModifier = Modifier.slot(1),
                    modifier = Modifier.weight(1f)
                )
                SectionTile(
                    title = "CINE",
                    glyph = Glyph.CLAPPER,
                    status = statusText(vm.movies, now),
                    onOpen = { vm.open(Screen.Movies) },
                    onRefresh = { vm.movies.refresh() },
                    openModifier = Modifier.slot(2),
                    refreshModifier = Modifier.slot(3),
                    modifier = Modifier.weight(1f)
                )
                SectionTile(
                    title = "SERIES",
                    glyph = Glyph.SERIES,
                    status = statusText(vm.series, now),
                    onOpen = { vm.open(Screen.SeriesList) },
                    onRefresh = { vm.series.refresh() },
                    openModifier = Modifier.slot(4),
                    refreshModifier = Modifier.slot(5),
                    modifier = Modifier.weight(1f)
                )
            }

            // ------------------------------------------------ barras de abajo
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                BottomBarButton("EPG", Glyph.EPG, { vm.open(Screen.Epg) }, Modifier.weight(1f).slot(6))
                BottomBarButton("PANTALLA MÚLTIPLE", Glyph.GRID, { vm.open(Screen.Multi) }, Modifier.weight(1f).slot(7))
                BottomBarButton("CATCH UP", Glyph.CHECK, { vm.open(Screen.CatchUp) }, Modifier.weight(1f).slot(8))
            }

            Text(
                text = if (expiresAt != null) "EXPIRACIÓN: ${formatDateLong(expiresAt * 1000)}" else "SIN FECHA DE VENCIMIENTO",
                color = TextPrimary,
                fontSize = 13.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }

    if (confirmExit) {
        OptionsDialog(
            title = "¿Qué quieres hacer?",
            options = listOf(
                Opt("Cerrar sesión") { vm.logout() },
                Opt("Salir de la aplicación") { context.findActivity()?.finish() },
                Opt("Cancelar") { }
            ),
            onDismiss = { confirmExit = false }
        )
    }
}

private fun statusText(state: BrowseState<*>, now: Long): String = when {
    state.loading && state.catalog == null -> "Cargando…"
    state.loading -> "Actualizando…"
    state.catalog == null && state.error != null -> "No se pudo cargar. Toca para reintentar."
    state.catalog == null -> "Pendiente de cargar"
    else -> "Última actualización: ${agoText(state.updatedAt, now)}"
}

@Composable
private fun SectionTile(
    title: String,
    glyph: Glyph,
    status: String,
    onOpen: () -> Unit,
    onRefresh: () -> Unit,
    openModifier: Modifier,
    refreshModifier: Modifier,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(22.dp))
            .background(PanelColor)
    ) {
        FocusCard(
            onClick = onOpen,
            modifier = openModifier
                .weight(1f)
                .fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            scaleOnFocus = 1f
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                GlyphIcon(glyph, Modifier.size(64.dp))
                Spacer(Modifier.height(8.dp))
                Text(title, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 10.dp, top = 4.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = status,
                color = TextSecondary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            FocusCard(
                onClick = onRefresh,
                modifier = refreshModifier.size(36.dp),
                shape = RoundedCornerShape(8.dp),
                scaleOnFocus = 1.1f,
                background = Color.White,
                focusedBackground = Color(0xFFD7E3FF)
            ) {
                Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                    GlyphIcon(Glyph.SWAP, Modifier.size(22.dp), tint = Color(0xFF1B2050))
                }
            }
        }
    }
}

@Composable
private fun BottomBarButton(
    label: String,
    glyph: Glyph,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FocusCard(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = RoundedCornerShape(6.dp),
        scaleOnFocus = 1.03f,
        background = Color(0x33FFFFFF),
        focusedBackground = PanelFocusedColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlyphIcon(glyph, Modifier.size(32.dp))
            Spacer(Modifier.width(14.dp))
            Text(label, color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}
