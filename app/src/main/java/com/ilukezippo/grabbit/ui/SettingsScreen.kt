package com.ilukezippo.grabbit.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDelegate
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ilukezippo.grabbit.BuildConfigInfo
import com.ilukezippo.grabbit.R
import com.ilukezippo.grabbit.core.AudioFormat
import com.ilukezippo.grabbit.core.VideoContainer
import com.ilukezippo.grabbit.engine.Engine
import com.ilukezippo.grabbit.engine.EngineState
import com.ilukezippo.grabbit.instagram.InstagramLoginActivity
import com.ilukezippo.grabbit.instagram.InstagramSession
import com.ilukezippo.grabbit.update.AppUpdater
import kotlinx.coroutines.launch

@Composable
private fun Section(icon: ImageVector, title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(G.surface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = G.violet); Spacer(Modifier.width(10.dp)); Text(title, style = MaterialTheme.typography.titleMedium)
        }
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val scope = rememberCoroutineScope()
    var igIn by remember { mutableStateOf(InstagramSession.isLoggedIn(ctx)) }
    val igLogin = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { igIn = InstagramSession.isLoggedIn(ctx) }
    val engine by Engine.state.collectAsStateWithLifecycle()
    var updating by remember { mutableStateOf(false) }
    var updateMsg by remember { mutableStateOf<String?>(null) }
    var height by remember { mutableIntStateOf(prefs.height) }
    var container by remember { mutableStateOf(prefs.container) }
    var afmt by remember { mutableStateOf(prefs.audioFormat) }
    val lang = AppCompatDelegate.getApplicationLocales().toLanguageTags().ifEmpty { java.util.Locale.getDefault().language }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(stringResource(R.string.tab_settings), style = MaterialTheme.typography.headlineLarge)

        Section(Icons.Rounded.Translate, stringResource(R.string.set_language)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip("English", !lang.startsWith("ar")) { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("en")) }
                ChoiceChip("العربية", lang.startsWith("ar")) { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ar")) }
            }
        }

        Section(Icons.Rounded.Tune, stringResource(R.string.set_defaults)) {
            var auto by remember { mutableStateOf(prefs.autoDownload) }
            SectionLabel(stringResource(R.string.when_grab))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip(stringResource(R.string.ask_every_time), !auto) { auto = false; prefs.autoDownload = false }
                ChoiceChip(stringResource(R.string.download_right_away), auto) { auto = true; prefs.autoDownload = true }
            }
            Text(stringResource(if (auto) R.string.auto_hint_on else R.string.auto_hint_off), color = G.muted, fontSize = 13.sp)
            SectionLabel(stringResource(R.string.mode_label))
            var audioMode by remember { mutableStateOf(prefs.audioMode) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceChip(stringResource(R.string.video), !audioMode) { audioMode = false; prefs.audioMode = false }
                ChoiceChip(stringResource(R.string.audio), audioMode) { audioMode = true; prefs.audioMode = true }
            }
            SectionLabel(stringResource(R.string.quality))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0, 2160, 1080, 720, 480, 360).forEach { h ->
                    ChoiceChip(if (h == 0) stringResource(R.string.best) else heightLabel(h), height == h) { height = h; prefs.height = h }
                }
            }
            SectionLabel(stringResource(R.string.video))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VideoContainer.entries.forEach { c -> ChoiceChip(c.name, container == c) { container = c; prefs.container = c } }
            }
            SectionLabel(stringResource(R.string.audio))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AudioFormat.entries.forEach { f -> ChoiceChip(f.name, afmt == f) { afmt = f; prefs.audioFormat = f } }
            }
        }

        Section(Icons.Rounded.CameraAlt, stringResource(R.string.set_instagram)) {
            Text(stringResource(R.string.ig_desc), color = G.muted, fontSize = 13.sp)
            if (igIn) Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, null, tint = G.green); Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.ig_connected), Modifier.weight(1f))
                TextButton({ InstagramSession.logout(ctx); igIn = false }) { Text(stringResource(R.string.ig_logout), color = G.red) }
            } else GradientButton({ igLogin.launch(Intent(ctx, InstagramLoginActivity::class.java)) }, Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.Login, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.ig_connect))
            }
        }

        Section(Icons.Rounded.SystemUpdate, stringResource(R.string.upd_section)) {
            val upd by AppUpdater.available.collectAsStateWithLifecycle()
            var checking by remember { mutableStateOf(false) }
            var checkMsg by remember { mutableStateOf<String?>(null) }
            upd?.let { UpdateBanner(it) }
            if (upd == null) {
                Text(stringResource(R.string.upd_auto), color = G.muted, fontSize = 13.sp)
                OutlinedButton({
                    checking = true; checkMsg = null
                    scope.launch {
                        checkMsg = runCatching { Prefs(ctx).skippedUpdate = null; AppUpdater.check(ctx, force = true) }.fold(
                            { if (it == null) ctx.getString(R.string.upd_latest, BuildConfigInfo.versionName(ctx)) else null },
                            { it.message ?: "Error" })
                        checking = false
                    }
                }, Modifier.fillMaxWidth(), enabled = !checking, shape = RoundedCornerShape(16.dp)) {
                    if (checking) { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.upd_checking)) }
                    else { Icon(Icons.Rounded.Refresh, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.upd_check)) }
                }
                checkMsg?.let { Text(it, color = G.muted, fontSize = 13.sp) }
            }
        }

        Section(Icons.Rounded.Memory, stringResource(R.string.set_engine)) {
            val v = (engine as? EngineState.Ready)?.version ?: "…"
            Text(stringResource(R.string.engine_version, v), fontSize = 14.sp)
            Text(stringResource(R.string.engine_update_hint), color = G.muted, fontSize = 13.sp)
            OutlinedButton({
                updating = true; updateMsg = null
                scope.launch {
                    updateMsg = runCatching { ctx.getString(R.string.engine_updated, Engine.update(ctx).substringAfter('|')) }
                        .getOrElse { it.message ?: "Error" }
                    updating = false
                }
            }, Modifier.fillMaxWidth(), enabled = !updating && engine is EngineState.Ready, shape = RoundedCornerShape(16.dp)) {
                if (updating) { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.engine_updating)) }
                else { Icon(Icons.Rounded.SystemUpdate, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.engine_update)) }
            }
            updateMsg?.let { Text(it, color = G.muted, fontSize = 13.sp) }
        }

        Section(Icons.Rounded.Info, stringResource(R.string.about)) {
            Text(stringResource(R.string.about_text, BuildConfigInfo.versionName(ctx)), color = G.muted, fontSize = 13.sp)
            TextButton({ ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://ilukezippo.github.io/"))) }) {
                Icon(Icons.Rounded.Apps, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.more_apps))
            }
        }
    }
}
