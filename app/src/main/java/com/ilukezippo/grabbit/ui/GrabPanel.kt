package com.ilukezippo.grabbit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ilukezippo.grabbit.R
import com.ilukezippo.grabbit.core.AudioFormat
import com.ilukezippo.grabbit.core.Choice
import com.ilukezippo.grabbit.core.VideoContainer
import com.ilukezippo.grabbit.engine.MediaInfo

fun heightLabel(h: Int) = when { h >= 2160 -> "4K"; h >= 1440 -> "2K"; else -> "${h}p" }

fun formatDuration(sec: Int): String {
    if (sec <= 0) return ""
    val h = sec / 3600; val m = (sec % 3600) / 60; val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@Composable
fun MediaHeader(info: MediaInfo) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(G.surface)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16 / 9f).background(G.surface2)) {
            if (info.thumbnail != null) AsyncImage(info.thumbnail, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Icon(Icons.Rounded.Movie, null, Modifier.size(48.dp).align(Alignment.Center), tint = G.muted)
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC0E0D14)))))
            Row(Modifier.align(Alignment.TopStart).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Badge(info.site)
                if (info.isLive) Badge(stringResource(R.string.live_now), G.red)
                if (info.entryCount > 1) Badge(stringResource(R.string.items_n, info.entryCount), G.violet)
            }
            val d = formatDuration(info.durationSec)
            if (d.isNotEmpty()) Badge(d, Color(0xAA000000), Modifier.align(Alignment.BottomEnd).padding(12.dp))
        }
        Column(Modifier.padding(16.dp)) {
            Text(info.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            info.uploader?.let { Text(it, color = G.muted, fontSize = 13.sp, maxLines = 1) }
        }
    }
}

@Composable
fun Badge(text: String, color: Color = Color(0x99000000), modifier: Modifier = Modifier) {
    Text(text, modifier.clip(RoundedCornerShape(50)).background(color).padding(horizontal = 10.dp, vertical = 4.dp),
        color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
}

@Composable
fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier.clip(shape)
            .then(if (selected) Modifier.background(G.gradient) else Modifier.background(G.surface2).border(1.dp, G.stroke, shape))
            .clickableNoRipple(onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) { Text(label, color = if (selected) Color.White else G.text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
}

@Composable
fun SectionLabel(text: String) = Text(text.uppercase(), color = G.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)

/** The options + download button shown after a link is resolved (home screen and share sheet). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GrabPanel(info: MediaInfo, onDownload: (Choice?) -> Unit) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        MediaHeader(info)
        if (info.isDirect) {
            if (info.direct.size > 1) LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(info.direct) { _, item ->
                    Box(Modifier.size(78.dp).clip(RoundedCornerShape(14.dp)).background(G.surface2)) {
                        AsyncImage(item.thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        if (item.isVideo) Icon(Icons.Rounded.PlayCircle, null, Modifier.align(Alignment.Center), tint = Color.White)
                    }
                }
            }
            GradientButton({ onDownload(null) }, Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Download, null); Spacer(Modifier.width(8.dp))
                Text(if (info.direct.size > 1) stringResource(R.string.download_n, info.direct.size) else stringResource(R.string.download))
            }
            return@Column
        }

        var audio by remember { mutableStateOf(prefs.audioMode && !info.isLive) }
        val heights = info.heights
        var height by remember {
            mutableStateOf((com.ilukezippo.grabbit.core.DefaultChoice.pick(false, prefs.height, prefs.container, prefs.audioFormat, heights) as Choice.Video).height)
        }
        var container by remember { mutableStateOf(prefs.container) }
        var afmt by remember { mutableStateOf(prefs.audioFormat) }

        if (!info.isLive) Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(G.surface2).padding(4.dp)) {
            listOf(false to Pair(R.string.video, Icons.Rounded.Videocam), true to Pair(R.string.audio, Icons.Rounded.MusicNote)).forEach { (isAudio, p) ->
                val sel = audio == isAudio
                Row(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).then(if (sel) Modifier.background(G.gradient) else Modifier)
                        .clickableNoRipple { audio = isAudio }.padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(p.second, null, Modifier.size(18.dp), tint = if (sel) Color.White else G.muted)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(p.first), color = if (sel) Color.White else G.muted, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (!audio) {
            SectionLabel(stringResource(R.string.quality))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip(stringResource(R.string.best), height == null) { height = null }
                heights.forEach { h -> ChoiceChip(heightLabel(h), height == h) { height = h } }
            }
            if (!info.isLive) {
                SectionLabel(stringResource(R.string.format))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VideoContainer.entries.forEach { c -> ChoiceChip(c.name, container == c) { container = c } }
                }
            }
        } else {
            SectionLabel(stringResource(R.string.format))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AudioFormat.entries.forEach { f -> ChoiceChip(f.name, afmt == f) { afmt = f } }
            }
        }

        if (info.isLive) Text(stringResource(R.string.live_hint), color = G.muted, fontSize = 13.sp)
        GradientButton({
            prefs.audioMode = audio
            if (!audio) { prefs.height = height ?: 0; prefs.container = container } else prefs.audioFormat = afmt
            onDownload(if (audio) Choice.Audio(afmt) else Choice.Video(height, container))
        }, Modifier.fillMaxWidth()) {
            Icon(if (info.isLive) Icons.Rounded.FiberManualRecord else Icons.Rounded.Download, null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(if (info.isLive) R.string.record_stream else R.string.download))
        }
    }
}
