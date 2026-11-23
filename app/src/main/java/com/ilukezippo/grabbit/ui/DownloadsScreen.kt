package com.ilukezippo.grabbit.ui

import android.content.ActivityNotFoundException
import android.app.Activity
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.ilukezippo.grabbit.engine.ImageConverter

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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DownloadsScreen(previewEditing: Boolean = false, previewSelected: Set<String> = emptySet()) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val tasks by Downloads.tasks.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(previewEditing) }
    var selected by remember { mutableStateOf(previewSelected) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showConvert by remember { mutableStateOf(false) }
    var pendingConsent by remember { mutableStateOf(setOf<String>()) }
    val selectable = tasks.filter { !it.active }
    // Keep the selection to items that still exist and aren't running.
    LaunchedEffect(tasks) { selected = selected.intersect(selectable.map { it.id }.toSet()) }
    fun exitEdit() { editing = false; selected = emptySet() }
    fun toggle(id: String) { selected = if (id in selected) selected - id else selected + id }

    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            Downloads.clear(ctx, pendingConsent)
            Toast.makeText(ctx, R.string.deleted_toast, Toast.LENGTH_SHORT).show()
        }
        pendingConsent = emptySet()
    }

    BackHandler(enabled = editing) { exitEdit() }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (editing) {
                Text(stringResource(R.string.n_selected, selected.size), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton({ selected = if (selected.size == selectable.size) emptySet() else selectable.map { it.id }.toSet() }) {
                    Text(stringResource(R.string.select_all))
                }
                TextButton({ exitEdit() }) { Text(stringResource(R.string.edit_done)) }
            } else {
                Text(stringResource(R.string.tab_downloads), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
                if (selectable.isNotEmpty()) TextButton({ editing = true }) {
                    Icon(Icons.Rounded.Checklist, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.edit))
                }
            }
        }
        if (editing) Text(stringResource(R.string.edit_hint), color = G.muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 8.dp))

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
        } else LazyColumn(Modifier.weight(1f).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(tasks, key = { it.id }) { t ->
                TaskRow(
                    t, editing = editing, selected = t.id in selected,
                    onToggle = { if (!t.active) toggle(t.id) },
                    onLongPress = { if (!t.active) { editing = true; selected = selected + t.id } },
                )
            }
        }

        if (editing) {
            val sel = tasks.filter { it.id in selected }
            val convertible = sel.any { t -> t.status == Status.DONE && t.files.any { Downloads.isMedia(it.mime) || ImageConverter.isImage(it.mime) } }
            Row(Modifier.fillMaxWidth().background(G.surface).padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                BarAction(Icons.Rounded.Transform, stringResource(R.string.act_convert), enabled = convertible) { showConvert = true }
                BarAction(Icons.Rounded.ClearAll, stringResource(R.string.act_clear), enabled = sel.isNotEmpty()) {
                    Downloads.clear(ctx, selected)
                    Toast.makeText(ctx, R.string.cleared_toast, Toast.LENGTH_SHORT).show()
                    exitEdit()
                }
                BarAction(Icons.Rounded.DeleteOutline, stringResource(R.string.act_delete), enabled = sel.isNotEmpty(), tint = G.red) { confirmDelete = true }
            }
        }
    }

    if (confirmDelete) {
        val n = tasks.filter { it.id in selected }.sumOf { it.files.size.coerceAtLeast(1) }
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(Icons.Rounded.DeleteForever, null, tint = G.red) },
            title = { Text(pluralStringResource(R.plurals.delete_title, n, n)) },
            text = { Text(stringResource(R.string.delete_body)) },
            confirmButton = {
                TextButton({
                    confirmDelete = false
                    val ids = selected
                    exitEdit()
                    scope.launch {
                        val out = Downloads.deleteFiles(ctx, ids)
                        if (out.needConsent.isNotEmpty() && Build.VERSION.SDK_INT >= 30) {
                            pendingConsent = out.consentIds
                            val req = MediaStore.createDeleteRequest(ctx.contentResolver, out.needConsent)
                            consent.launch(IntentSenderRequest.Builder(req.intentSender).build())
                        } else withContext(Dispatchers.Main) {
                            val msg = if (out.needConsent.isEmpty()) ctx.getString(R.string.deleted_toast)
                                else ctx.getString(R.string.delete_some_failed, out.needConsent.size)
                            Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
                        }
                    }
                }) { Text(stringResource(R.string.delete_confirm), color = G.red, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton({ confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
            containerColor = G.surface,
        )
    }

    if (showConvert) {
        val files = tasks.filter { it.id in selected && it.status == Status.DONE }.flatMap { it.files }
        val hasMedia = files.any { Downloads.isMedia(it.mime) }
        val hasImages = files.any { ImageConverter.isImage(it.mime) }
        var mediaTarget by remember { mutableStateOf("mp3") }
        var imageTarget by remember { mutableStateOf("jpg") }
        ModalBottomSheet(onDismissRequest = { showConvert = false }, containerColor = G.bg) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(stringResource(R.string.convert_to_title), style = MaterialTheme.typography.titleLarge)
                if (hasMedia) {
                    SectionLabel(stringResource(R.string.conv_media))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        MEDIA_TARGETS.forEach { t -> ChoiceChip(t.uppercase(), mediaTarget == t) { mediaTarget = t } }
                    }
                }
                if (hasImages) {
                    SectionLabel(stringResource(R.string.conv_images))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ImageConverter.TARGETS.forEach { t -> ChoiceChip(t.uppercase(), imageTarget == t) { imageTarget = t } }
                    }
                }
                GradientButton({
                    Downloads.convert(ctx, selected, mediaTarget.takeIf { hasMedia }, imageTarget.takeIf { hasImages })
                    Toast.makeText(ctx, R.string.convert_started, Toast.LENGTH_SHORT).show()
                    showConvert = false
                    exitEdit()
                }, Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.AutoFixHigh, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.conv_start))
                }
            }
        }
    }
}

val MEDIA_TARGETS = listOf("mp3", "m4a", "opus", "mp4", "mkv", "webm", "gif")

@Composable
private fun BarAction(icon: ImageVector, label: String, enabled: Boolean, tint: androidx.compose.ui.graphics.Color = G.text, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(14.dp)).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 18.dp, vertical = 6.dp)
            .alpha(if (enabled) 1f else .35f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, tint = tint)
        Text(label, color = tint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TaskRow(t: DownloadTask, editing: Boolean, selected: Boolean, onToggle: () -> Unit, onLongPress: () -> Unit) {
    val ctx = LocalContext.current
    val openable = t.status == Status.DONE && t.files.isNotEmpty()
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(if (selected) G.violet.copy(alpha = .18f) else G.surface)
            .then(if (selected) Modifier.border(1.5.dp, G.violet, shape) else Modifier)
            .combinedClickable(
                onClick = { if (editing) onToggle() else if (openable) openFile(ctx, t.files.first()) },
                onLongClick = onLongPress,
            )
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (editing) {
                Icon(
                    if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null,
                    tint = if (t.active) G.stroke else if (selected) G.violet else G.muted,
                )
                Spacer(Modifier.width(10.dp))
            }
            Box(Modifier.size(64.dp, 48.dp).clip(RoundedCornerShape(12.dp)).background(G.surface2), contentAlignment = Alignment.Center) {
                val thumb = t.spec.thumbnail ?: t.files.firstOrNull { it.mime.startsWith("image") || it.mime.startsWith("video") }?.uri
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
            if (t.spec.isLive) LinearProgressIndicator({ 1f }, Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)), color = G.red, trackColor = G.surface2, strokeCap = StrokeCap.Round, gapSize = 0.dp, drawStopIndicator = {})
            else if (t.progress >= 0) LinearProgressIndicator({ t.progress / 100f }, Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)), color = G.pink, trackColor = G.surface2, strokeCap = StrokeCap.Round, gapSize = 0.dp, drawStopIndicator = {})
            else LinearProgressIndicator(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)), color = G.pink, trackColor = G.surface2)
        }
        t.error?.takeIf { t.status == Status.FAILED }?.let { Text(it, color = G.muted, fontSize = 12.sp, maxLines = 3, modifier = Modifier.padding(top = 8.dp)) }
        if (!editing) Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.End) {
            when (t.status) {
                Status.QUEUED -> Action(Icons.Rounded.Close, stringResource(R.string.cancel)) { Downloads.stop(t.id) }
                Status.RUNNING -> if (t.spec.isLive) Action(Icons.Rounded.Stop, stringResource(R.string.stop_save)) { Downloads.stop(t.id) }
                    else Action(Icons.Rounded.Close, stringResource(R.string.cancel)) { Downloads.stop(t.id) }
                Status.DONE -> Action(Icons.Rounded.Share, stringResource(R.string.share)) { shareFiles(ctx, t.files) }
                Status.FAILED, Status.CANCELED ->
                    if (t.spec.choice != null || t.spec.direct.isNotEmpty() || t.spec.convertTo != null)
                        Action(Icons.Rounded.Refresh, stringResource(R.string.retry)) { Downloads.retry(ctx, t.id) }
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
