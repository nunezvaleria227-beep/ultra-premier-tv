package com.example.mitvplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Películas y episodios que se dejaron a medias, con su barra de avance. */
@Composable
fun ContinueScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val entries = vm.resumeEntries
    val first = remember { FocusRequester() }

    LaunchedEffect(entries.isNotEmpty()) {
        withFrameNanos { }
        withFrameNanos { }
        first.tryRequestFocus()
    }

    AppBackground(dim = 0.45f) {
        Column(Modifier.fillMaxSize()) {
            TopBar(title = "Continuar viendo", centerTitle = true, onBack = { vm.back() })
            if (entries.isEmpty()) {
                CenterMessage("Todavía no tienes nada a medias.\nCuando dejes una película o serie a la mitad, aparecerá aquí para seguirla.")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(entries) { index, entry ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FocusCard(
                                onClick = { vm.playResume(context, entry) },
                                modifier = Modifier
                                    .weight(1f)
                                    .then(if (index == 0) Modifier.focusRequester(first) else Modifier),
                                shape = RoundedCornerShape(10.dp),
                                scaleOnFocus = 1.01f
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    PosterImage(
                                        url = entry.image,
                                        title = entry.title,
                                        modifier = Modifier
                                            .width(104.dp)
                                            .aspectRatio(16f / 9f)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                    Spacer(Modifier.width(14.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            text = entry.title,
                                            color = TextPrimary,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        val subtitle = entry.subtitle
                                        if (subtitle != null) {
                                            Text(
                                                text = subtitle,
                                                color = TextSecondary,
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        val total = if (entry.durationMs > 0) " de ${formatDuration(entry.durationMs / 1000)}" else ""
                                        Text(
                                            text = "Vas por ${formatDuration(entry.positionMs / 1000)}$total",
                                            color = AccentColor,
                                            fontSize = 12.sp
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        Box(
                                            Modifier
                                                .fillMaxWidth()
                                                .height(4.dp)
                                                .background(Color(0x33FFFFFF), RoundedCornerShape(2.dp))
                                        ) {
                                            Box(
                                                Modifier
                                                    .fillMaxWidth(entry.fraction.coerceIn(0.02f, 1f))
                                                    .fillMaxHeight()
                                                    .background(AccentColor, RoundedCornerShape(2.dp))
                                            )
                                        }
                                    }
                                }
                            }
                            TvButton(text = "Quitar", onClick = { vm.removeResume(entry) }, compact = true)
                        }
                    }
                }
            }
        }
    }
}
