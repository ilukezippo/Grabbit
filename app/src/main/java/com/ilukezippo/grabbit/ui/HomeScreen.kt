package com.ilukezippo.grabbit.ui

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ilukezippo.grabbit.R
import com.ilukezippo.grabbit.core.LinkExtractor
import com.ilukezippo.grabbit.engine.Engine
import com.ilukezippo.grabbit.engine.EngineState
import com.ilukezippo.grabbit.update.AppUpdater

fun clipboardLink(ctx: Context): String? = runCatching {
    val cm = ctx.getSystemService(ClipboardManager::class.java)
    LinkExtractor.extract(cm.primaryClip?.getItemAt(0)?.coerceToText(ctx)?.toString())
}.getOrNull()

@Composable
fun HomeScreen(vm: GrabViewModel, onQueued: () -> Unit) {
    val ctx = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    val input by vm.input.collectAsStateWithLifecycle()
    val engine by Engine.state.collectAsStateWithLifecycle()
    var clip by remember { mutableStateOf<String?>(null) }
    // Android 10+ only lets an app read the clipboard once its window has focus, which comes after onResume.
    val focused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(focused) { if (focused) clip = clipboardLink(ctx)?.takeIf { it != Prefs(ctx).lastGrabbed } }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column {
            Text("Grabbit", style = gradientTitle)
            Text(stringResource(R.string.tagline), color = G.muted, fontSize = 15.sp)
        }

        AppUpdater.available.collectAsStateWithLifecycle().value?.let { UpdateBanner(it) }

        // Link box
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(G.gradientSoft).padding(1.dp)
            .clip(RoundedCornerShape(23.dp)).background(G.surface).padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = input, onValueChange = { vm.input.value = it },
                placeholder = { Text(stringResource(R.string.paste_hint), color = G.muted) },
                leadingIcon = { Icon(Icons.Rounded.Link, null, tint = G.violet) },
                trailingIcon = {
                    if (input.isNotEmpty()) IconButton({ vm.reset() }) { Icon(Icons.Rounded.Close, stringResource(R.string.clear)) }
                    else IconButton({
                        val link = clipboardLink(ctx)
                        if (link != null) { vm.input.value = link; vm.grab(link) }
                        else Toast.makeText(ctx, R.string.no_clip_link, Toast.LENGTH_SHORT).show()
                    }) { Icon(Icons.Rounded.ContentPaste, stringResource(R.string.paste)) }
                },
                singleLine = true, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { vm.grab() }),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = G.violet, unfocusedBorderColor = G.stroke, focusedContainerColor = G.surface2, unfocusedContainerColor = G.surface2),
            )
            GradientButton({ vm.grab() }, Modifier.fillMaxWidth(), enabled = input.isNotBlank() && state !is GrabState.Loading) {
                if (state is GrabState.Loading) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp)); Text(stringResource(R.string.fetching))
                } else { Icon(Icons.Rounded.Bolt, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.grab), fontSize = 16.sp) }
            }
        }

        val c = clip
        if (c != null && input.isBlank() && state is GrabState.Idle) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(G.surface2).clickableNoRipple { vm.input.value = c; vm.grab(c) }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AssignmentReturned, null, tint = G.pink)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.clipboard_found), fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, fontSize = 14.sp)
                    Text(c, color = G.muted, fontSize = 12.sp, maxLines = 1)
                }
            }
        }

        when (val e = engine) {
            is EngineState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.engine_loading), color = G.muted, fontSize = 13.sp)
            }
            is EngineState.Failed -> Text(stringResource(R.string.engine_failed, e.message), color = G.red, fontSize = 13.sp)
            else -> {}
        }

        val queued = state as? GrabState.Queued
        LaunchedEffect(queued) {
            if (queued != null) {
                Toast.makeText(ctx, ctx.getString(R.string.auto_started, queued.label), Toast.LENGTH_SHORT).show()
                vm.reset(); onQueued()
            }
        }

        AnimatedContent(state, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "grab") { s ->
            when (s) {
                is GrabState.Loaded -> GrabPanel(s.info) { choice ->
                    vm.download(s.info, choice)
                    Toast.makeText(ctx, R.string.added_queue, Toast.LENGTH_SHORT).show()
                    onQueued()
                }
                is GrabState.Error -> Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(G.red.copy(alpha = .12f)).padding(14.dp)) {
                    Icon(Icons.Rounded.ErrorOutline, null, tint = G.red); Spacer(Modifier.width(10.dp))
                    Text(s.message, color = G.text, fontSize = 14.sp)
                }
                else -> FeatureGrid()
            }
        }
    }
}

@Composable
private fun FeatureGrid() {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Feature(Icons.Rounded.HighQuality, "144p → 4K", Modifier.weight(1f))
            Feature(Icons.Rounded.MusicNote, "MP3 · M4A", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Feature(Icons.Rounded.PhotoLibrary, "Stories · Photos", Modifier.weight(1f))
            Feature(Icons.Rounded.FiberManualRecord, "LIVE", Modifier.weight(1f))
        }
        Text(stringResource(R.string.works_with), color = G.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun Feature(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier) {
    Row(modifier.clip(RoundedCornerShape(18.dp)).background(G.surface).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(G.gradient), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(18.dp), tint = Color.White)
        }
        Spacer(Modifier.width(10.dp))
        Text(label, fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, maxLines = 1)
    }
}
