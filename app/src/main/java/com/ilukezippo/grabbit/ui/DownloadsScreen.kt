package com.ilukezippo.grabbit.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.ilukezippo.grabbit.R
import com.ilukezippo.grabbit.download.DownloadTask
import com.ilukezippo.grabbit.download.Downloads
import com.ilukezippo.grabbit.download.SavedFile
import com.ilukezippo.grabbit.download.Status

fun openFile(ctx: Context, f: SavedFile) {
    val i = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(f.uri), f.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    try { ctx.startActivity(i) } catch (_: ActivityNotFoundException) { Toast.makeText(ctx, R.string.no_app_open, Toast.LENGTH_SHORT).show() }
}

fun shareFiles(ctx: Context, files: List<SavedFile>) {
    val uris = ArrayList(files.map { Uri.parse(it.uri) })
    val i = if (uris.size == 1) Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0]).setType(files[0].mime)
    else Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris).setType("*/*")
    ctx.startActivity(Intent.createChooser(i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun formatSize(b: Long): String = when {
    b <= 0 -> ""; b < 1 shl 20 -> "%.0f KB".format(b / 1024.0); b < 1 shl 30 -> "%.1f MB".format(b / 1048576.0); else -> "%.2f GB".format(b / 1073741824.0)
}

@Composable
fun DownloadsScreen() {
    val ctx = LocalContext.current
    val tasks by Downloads.tasks.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.tab_downloads), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
            if (tasks.any { !it.active }) TextButton({ Downloads.clearFinished(ctx) }) { Text(stringResource(R.string.clear_finished)) }
        }
        if (tasks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(72.dp).clip(RoundedCornerShape(22.dp)).background(G.gradientSoft), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.DownloadForOffline, null, Modifier.size(36.dp), tint = G.violet)
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(stringResource(R.string.downloads_empty), color = G.muted, textAlign = TextAlign.Center)
                }
            }
        } else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(tasks, key = { it.id }) { TaskRow(it) }
        }
    }
}

@Composable
private fun TaskRow(t: DownloadTask) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(G.surface).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp, 48.dp).clip(RoundedCornerShape(12.dp)).background(G.surface2), contentAlignment = Alignment.Center) {
                val thumb = t.spec.thumbnail ?: t.files.firstOrNull { it.mime.startsWith("image") }?.uri
                if (thumb != null) AsyncImage(thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Icon(if (t.files.firstOrNull()?.mime?.startsWith("audio") == true) Icons.Rounded.MusicNote else Icons.Rounded.Movie, null, tint = G.muted)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t.spec.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                val (label, color) = when (t.status) {
                    Status.QUEUED -> stringResource(R.string.queued) to G.muted
                    Status.RUNNING -> (if (t.spec.isLive) "● REC" else if (t.progress >= 0) "%.0f%%".format(t.progress) else stringResource(R.string.preparing)) to G.violet
                    Status.DONE -> (stringResource(R.string.done) + t.files.sumOf { it.size }.let { if (it > 0) " · " + formatSize(it) else "" }) to G.green
                    Status.FAILED -> stringResource(R.string.failed) to G.red
                    Status.CANCELED -> stringResource(R.string.canceled) to G.muted
                }
                Row {
                    Text(label, color = color, fontSize = 12.sp)
                    if (t.status == Status.RUNNING && t.etaSec > 0) Text("  ·  " + stringResource(R.string.eta, formatDuration(t.etaSec.toInt())), color = G.muted, fontSize = 12.sp)
                }
            }
        }
        if (t.status == Status.RUNNING) {
            Spacer(Modifier.height(10.dp))
            if (t.progress >= 0 && !t.spec.isLive) LinearProgressIndicator({ t.progress / 100f }, Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)), color = G.pink, trackColor = G.surface2, strokeCap = StrokeCap.Round, gapSize = 0.dp, drawStopIndicator = {})
            else LinearProgressIndicator(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)), color = if (t.spec.isLive) G.red else G.pink, trackColor = G.surface2)
        }
        t.error?.takeIf { t.status == Status.FAILED }?.let { Text(it, color = G.muted, fontSize = 12.sp, maxLines = 3, modifier = Modifier.padding(top = 8.dp)) }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.End) {
            when (t.status) {
                Status.QUEUED -> Action(Icons.Rounded.Close, stringResource(R.string.cancel)) { Downloads.stop(t.id) }
                Status.RUNNING -> if (t.spec.isLive) Action(Icons.Rounded.Stop, stringResource(R.string.stop_save)) { Downloads.stop(t.id) }
                    else Action(Icons.Rounded.Close, stringResource(R.string.cancel)) { Downloads.stop(t.id) }
                Status.DONE -> {
                    Action(Icons.Rounded.Share, stringResource(R.string.share)) { shareFiles(ctx, t.files) }
                    Action(Icons.Rounded.PlayArrow, stringResource(R.string.open)) { t.files.firstOrNull()?.let { openFile(ctx, it) } }
                    Action(Icons.Rounded.DeleteOutline, stringResource(R.string.remove)) { Downloads.remove(ctx, t.id) }
                }
                Status.FAILED, Status.CANCELED -> {
                    if (t.spec.choice != null || t.spec.direct.isNotEmpty()) Action(Icons.Rounded.Refresh, stringResource(R.string.retry)) { Downloads.retry(ctx, t.id) }
                    Action(Icons.Rounded.DeleteOutline, stringResource(R.string.remove)) { Downloads.remove(ctx, t.id) }
                }
            }
        }
    }
}

@Composable
private fun Action(icon: ImageVector, label: String, onClick: () -> Unit) {
    TextButton(onClick, contentPadding = PaddingValues(horizontal = 10.dp)) {
        Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(label, fontSize = 13.sp)
    }
}
