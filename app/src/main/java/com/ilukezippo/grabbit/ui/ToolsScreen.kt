package com.ilukezippo.grabbit.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ilukezippo.grabbit.R
import com.ilukezippo.grabbit.core.FileNames
import com.ilukezippo.grabbit.download.Downloads
import com.ilukezippo.grabbit.download.MediaSaver
import com.ilukezippo.grabbit.engine.FFmpegRunner
import com.ilukezippo.grabbit.recorder.ScreenRecordService
import com.ilukezippo.grabbit.recorder.RecordLauncherActivity
import com.ilukezippo.grabbit.Notifications
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ToolCard(icon: ImageVector, title: String, desc: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(G.surface).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(G.gradient), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Color.White)
            }
            Spacer(Modifier.width(12.dp))
            Text(title, style = MaterialTheme.typography.titleLarge)
        }
        Text(desc, color = G.muted, fontSize = 14.sp)
        content()
    }
}

@Composable
fun ToolsScreen() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.tab_tools), style = MaterialTheme.typography.headlineLarge)
        RecorderCard()
        ConverterCard()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecorderCard() {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var withAudio by remember { mutableStateOf(prefs.recordAudio && Build.VERSION.SDK_INT >= 29) }
    var countdown by remember { mutableIntStateOf(prefs.recCountdown) }
    var shortcut by remember { mutableStateOf(prefs.recShortcut) }
    val since by ScreenRecordService.recordingSince.collectAsStateWithLifecycle()
    val counting by ScreenRecordService.countdown.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(since) { while (since != 0L) { now = SystemClock.elapsedRealtime(); delay(500) } }
    // The overlay permission is granted in another screen; re-check when we come back.
    var canOverlay by remember { mutableStateOf(Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(ctx)) }
    val focused = androidx.compose.ui.platform.LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(focused) { canOverlay = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(ctx) }

    ToolCard(Icons.Rounded.Videocam, stringResource(R.string.rec_title), stringResource(R.string.rec_desc)) {
        if (Build.VERSION.SDK_INT >= 29) Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.rec_audio), Modifier.weight(1f), fontSize = 14.sp)
            Switch(withAudio, { withAudio = it; prefs.recordAudio = it }, enabled = since == 0L)
        }
        SectionLabel(stringResource(R.string.rec_countdown))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0, 3, 5, 10).forEach { s ->
                ChoiceChip(if (s == 0) stringResource(R.string.off) else stringResource(R.string.n_seconds, s), countdown == s) {
                    countdown = s; prefs.recCountdown = s
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.rec_shortcut), fontSize = 14.sp)
                Text(stringResource(R.string.rec_shortcut_hint), color = G.muted, fontSize = 12.sp)
            }
            Switch(shortcut, {
                shortcut = it; prefs.recShortcut = it
                if (it) Notifications.showRecorderShortcut(ctx) else Notifications.hideRecorderShortcut(ctx)
            })
        }
        if (Build.VERSION.SDK_INT >= 33) OutlinedButton({
            val sbm = ctx.getSystemService(android.app.StatusBarManager::class.java)
            sbm.requestAddTileService(
                android.content.ComponentName(ctx, com.ilukezippo.grabbit.recorder.RecordTileService::class.java),
                ctx.getString(R.string.tile_label),
                android.graphics.drawable.Icon.createWithResource(ctx, R.drawable.ic_stat_rec),
                ctx.mainExecutor,
            ) { }
        }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Icon(Icons.Rounded.AddBox, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.rec_add_tile))
        } else Text(stringResource(R.string.rec_tile_hint), color = G.muted, fontSize = 12.sp)
        if (!canOverlay) {
            OutlinedButton({
                ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")))
            }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Rounded.BubbleChart, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.rec_overlay))
            }
        }
        when {
            counting != 0 -> Button({ ScreenRecordService.stop(ctx) }, Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = G.surface2)) {
                Icon(Icons.Rounded.Timer, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.rec_starts_in, counting))
            }
            since != 0L -> {
                val s = (now - since) / 1000
                Button({ ScreenRecordService.stop(ctx) }, Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = G.red)) {
                    Icon(Icons.Rounded.Stop, null); Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.rec_stop) + "  " + "%02d:%02d".format(s / 60, s % 60))
                }
            }
            else -> GradientButton({
                ctx.startActivity(Intent(ctx, RecordLauncherActivity::class.java).putExtra(RecordLauncherActivity.EXTRA_FROM_APP, true))
            }, Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.FiberManualRecord, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.rec_start))
            }
        }
    }
}

private fun displayName(ctx: Context, uri: Uri): String =
    ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    } ?: "file"

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConverterCard() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var picked by remember { mutableStateOf<Uri?>(null) }
    var name by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("mp3") }
    var progress by remember { mutableStateOf<Float?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { picked = uri; name = displayName(ctx, uri); message = null }
    }
    val targets = listOf("mp3", "m4a", "opus", "mp4", "mkv", "webm", "gif")

    ToolCard(Icons.Rounded.Transform, stringResource(R.string.conv_title), stringResource(R.string.conv_desc)) {
        OutlinedButton({ picker.launch(arrayOf("video/*", "audio/*")) }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), enabled = progress == null) {
            Icon(Icons.Rounded.FolderOpen, null); Spacer(Modifier.width(8.dp))
            Text(if (picked == null) stringResource(R.string.conv_pick) else name, maxLines = 1)
        }
        SectionLabel(stringResource(R.string.conv_to))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            targets.forEach { t -> ChoiceChip(t.uppercase(), target == t) { target = t } }
        }
        val p = progress
        if (p != null) {
            Text(stringResource(R.string.conv_running, p.toInt()), color = G.violet, fontWeight = FontWeight.SemiBold)
            LinearProgressIndicator({ p / 100f }, Modifier.fillMaxWidth(), color = G.pink, trackColor = G.surface2)
        } else GradientButton({
            val src = picked ?: return@GradientButton
            progress = 0f; message = null
            scope.launch {
                val work = File(ctx.cacheDir, "conv").apply { deleteRecursively(); mkdirs() }
                try {
                    val input = File(work, "in." + name.substringAfterLast('.', "bin"))
                    withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(src)!!.use { i -> input.outputStream().use { i.copyTo(it) } } }
                    val outName = FileNames.sanitize(name.substringBeforeLast('.')) + "." + target
                    val out = File(work, "out.$target")
                    FFmpegRunner.run(ctx, FFmpegRunner.convertArgs(input, out)) { progress = it }
                    val saved = withContext(Dispatchers.IO) { MediaSaver.save(ctx, out, outName) }
                    Downloads.addExternal(ctx, outName, saved)
                    message = ctx.getString(R.string.conv_done)
                } catch (e: Throwable) {
                    message = ctx.getString(R.string.conv_failed, e.message?.lines()?.lastOrNull { it.isNotBlank() } ?: "")
                } finally {
                    progress = null
                    work.deleteRecursively()
                }
            }
        }, Modifier.fillMaxWidth(), enabled = picked != null) {
            Icon(Icons.Rounded.AutoFixHigh, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.conv_start))
        }
        message?.let { Text(it, color = if (it == stringResource(R.string.conv_done)) G.green else G.red, fontSize = 13.sp) }
    }
}
