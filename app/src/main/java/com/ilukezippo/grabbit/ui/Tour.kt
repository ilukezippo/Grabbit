package com.ilukezippo.grabbit.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import com.ilukezippo.grabbit.R
import kotlinx.coroutines.flow.MutableStateFlow

/** Lets Settings replay the tour. */
object TourState {
    val show = MutableStateFlow(false)
}

private const val SCENE_MS = 4500

/** 0..1 progress of [t] between [a] and [b]. */
private fun seg(t: Float, a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)
private fun smooth(f: Float) = f * f * (3 - 2 * f)

private class Scene(val title: Int, val text: Int, val draw: @Composable BoxScope.(Float) -> Unit)

/**
 * A short animated walkthrough shown on first launch, like a video in five scenes: an animated
 * finger taps through each step on a mock phone. Auto-advances; tap to skip ahead.
 */
@Composable
fun Tour(onDone: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    val time = remember { Animatable(0f) }
    LaunchedEffect(step) {
        time.snapTo(0f)
        time.animateTo(1f, tween(SCENE_MS, easing = LinearEasing))
        if (step < scenes.lastIndex) step++
    }
    TourFrame(step, time.value, onNext = { if (step < scenes.lastIndex) step++ }, onDone = onDone)
}

private val scenes = listOf(
    Scene(R.string.tour1_title, R.string.tour1_text) { t -> ShareScene(t) },
    Scene(R.string.tour2_title, R.string.tour2_text) { t -> QualityScene(t) },
    Scene(R.string.tour3_title, R.string.tour3_text) { t -> DownloadsScene(t) },
    Scene(R.string.tour4_title, R.string.tour4_text) { t -> RecordScene(t) },
    Scene(R.string.tour5_title, R.string.tour5_text) { t -> SettingsScene(t) },
)

/** One frame of the walkthrough: scene [step] at time [t] (0..1). Separate so it can be previewed. */
@Composable
fun TourFrame(step: Int, t: Float, onNext: () -> Unit, onDone: () -> Unit) {
    val last = step == scenes.lastIndex
    val s = scenes[step]

    Column(
        Modifier.fillMaxSize().background(G.bg).systemBarsPadding()
            .clickable(remember { MutableInteractionSource() }, null) { if (!last) onNext() }
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // story-style progress bars
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            scenes.indices.forEach { i ->
                val f = when { i < step -> 1f; i == step -> t; else -> 0f }
                Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(G.surface2)) {
                    Box(Modifier.fillMaxHeight().fillMaxWidth(f).background(G.gradient))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Grabbit", style = gradientTitle.copy(fontSize = 22.sp), modifier = Modifier.weight(1f))
            TextButton(onDone) { Text(stringResource(R.string.tour_skip), color = G.muted) }
        }
        Spacer(Modifier.weight(1f))
        Phone { s.draw(this, t) }
        Spacer(Modifier.height(24.dp))
        Text(stringResource(s.title), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(s.text), color = G.muted, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.heightIn(min = 66.dp))
        Spacer(Modifier.weight(1f))
        GradientButton({ if (last) onDone() else onNext() }, Modifier.fillMaxWidth()) {
            Text(stringResource(if (last) R.string.tour_start else R.string.tour_next), fontSize = 16.sp)
        }
    }
}

// ---------- building blocks ----------

private val PW = 230.dp
private val PH = 410.dp

/** Where each named button sits inside the mock phone, so the finger lands on it in any language or size. */
private class Targets {
    var root: LayoutCoordinates? = null
    val centers = mutableStateMapOf<String, Offset>()
}
private val LocalTargets = staticCompositionLocalOf { Targets() }

/** Marks this element as a place the finger can go to. */
private fun Modifier.target(name: String): Modifier = composed {
    val targets = LocalTargets.current
    onGloballyPositioned { c ->
        val root = targets.root ?: return@onGloballyPositioned
        if (c.isAttached && root.isAttached) targets.centers[name] = root.localPositionOf(c, Offset(c.size.width / 2f, c.size.height / 2f))
    }
}

@Composable
private fun Phone(content: @Composable BoxScope.() -> Unit) {
    val targets = remember { Targets() }
    Box(
        Modifier.size(PW + 16.dp, PH + 16.dp).shadow(24.dp, RoundedCornerShape(38.dp))
            .clip(RoundedCornerShape(38.dp)).background(Color(0xFF16151C)).border(2.dp, Color(0xFF3A3646), RoundedCornerShape(38.dp)).padding(8.dp)
    ) {
        CompositionLocalProvider(LocalTargets provides targets) {
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(30.dp)).background(Color(0xFF0E0D14))
                    .onGloballyPositioned { targets.root = it },
                content = content,
            )
        }
    }
}

/**
 * The animated finger. [path] is (time, target name) keyframes: the finger glides between targets;
 * it starts from the bottom middle of the screen. [taps] are times when it taps (a ring grows).
 */
@Composable
private fun BoxScope.Finger(t: Float, path: List<Pair<Float, String>>, taps: List<Float>, visible: ClosedFloatingPointRange<Float> = 0f..1f) {
    if (t !in visible) return
    val targets = LocalTargets.current
    val density = LocalDensity.current
    val home = with(density) { Offset(PW.toPx() / 2, (PH - 30.dp).toPx()) }
    fun at(name: String) = targets.centers[name] ?: home
    var pos = home
    var prevT = 0f; var prevP = home
    for ((kt, name) in path) {
        val p = at(name)
        if (t <= kt) { val f = ((t - prevT) / (kt - prevT).coerceAtLeast(.001f)).coerceIn(0f, 1f); pos = prevP + (p - prevP) * smooth(f); break }
        pos = p; prevT = kt; prevP = p
    }
    val tap = taps.map { seg(t, it, it + .1f) }.firstOrNull { it in 0.01f..0.99f } ?: 0f
    val half = with(density) { 22.dp.toPx() }
    val ring = tap
    Box(Modifier.align(AbsoluteAlignment.TopLeft).absoluteOffset { IntOffset((pos.x - half).toInt(), (pos.y - half).toInt()) }.size(44.dp), contentAlignment = Alignment.Center) {
        if (ring > 0f) Box(Modifier.size(44.dp).scale(0.6f + ring).alpha(1f - ring).border(2.dp, Color.White, CircleShape))
        Box(Modifier.size(26.dp).scale(if (ring > 0f) 0.85f else 1f).shadow(6.dp, CircleShape).background(Color.White.copy(alpha = .92f), CircleShape))
    }
}

@Composable
private fun Bar(w: Dp, h: Dp = 8.dp, color: Color = G.surface2) = Box(Modifier.size(w, h).clip(RoundedCornerShape(4.dp)).background(color))

@Composable
private fun MiniChip(text: String, on: Boolean) {
    Box(
        Modifier.clip(RoundedCornerShape(8.dp)).then(if (on) Modifier.background(G.gradient) else Modifier.background(G.surface2))
            .padding(horizontal = 8.dp, vertical = 5.dp)
    ) { Text(text, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White) }
}

@Composable
private fun AppIcon(size: Dp) {
    // The launcher icon is an adaptive icon; draw its two layers (Compose can't load adaptive icons directly).
    Box(Modifier.size(size).clip(RoundedCornerShape(size / 4))) {
        Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.fillMaxSize())
        Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.fillMaxSize().scale(1.4f))
    }
}

// ---------- scenes (t: 0..1 over one scene) ----------

@Composable
private fun BoxScope.ShareScene(t: Float) {
    // A video playing in another app
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Box(Modifier.fillMaxWidth().height(250.dp).clip(RoundedCornerShape(16.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF28AADC), Color(0xFF0A285A))))) {
            Icon(Icons.Rounded.PlayCircle, null, Modifier.size(54.dp).align(Alignment.Center), tint = Color.White.copy(alpha = .9f))
        }
        Spacer(Modifier.height(10.dp))
        Bar(150.dp, 10.dp, Color(0xFF3A3646)); Spacer(Modifier.height(6.dp)); Bar(90.dp)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            listOf(Icons.Rounded.FavoriteBorder, Icons.Rounded.ChatBubbleOutline, Icons.Rounded.Share).forEach {
                Icon(it, null, tint = if (it == Icons.Rounded.Share && t > .32f) G.pink else G.muted,
                    modifier = if (it == Icons.Rounded.Share) Modifier.target("share") else Modifier)
            }
        }
    }
    // Share sheet slides up
    val sheet = seg(t, .38f, .52f)
    if (sheet > 0f) {
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().offset(y = 170.dp * (1 - sheet))
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)).background(Color(0xFF1C1A25)).padding(14.dp)
        ) {
            Text(stringResource(R.string.tour_share_with), fontSize = 11.sp, color = G.muted)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                repeat(3) { Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(G.surface2)) }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.target("grabbit").scale(1f + .12f * seg(t, .74f, .8f) * (1 - seg(t, .8f, .86f)))) { AppIcon(40.dp) }
                    Text("Grabbit", fontSize = 9.sp, color = G.text)
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
    // Grabbit sheet appears
    val grab = seg(t, .86f, .96f)
    if (grab > 0f) Box(
        Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(150.dp).offset(y = 150.dp * (1 - grab))
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)).background(G.bg).border(1.dp, G.violet.copy(alpha = .5f), RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Grabbit", style = gradientTitle.copy(fontSize = 16.sp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { MiniChip("Best", false); MiniChip("1080p", true); MiniChip("720p", false) }
            Box(Modifier.fillMaxWidth().height(30.dp).clip(RoundedCornerShape(10.dp)).background(G.gradient))
        }
    }
    Finger(t, listOf(.05f to "home", .28f to "share", .55f to "share", .72f to "grabbit"), taps = listOf(.3f, .74f), visible = 0f..0.86f)
}

@Composable
private fun BoxScope.QualityScene(t: Float) {
    val picked720 = t > .3f
    val dl = seg(t, .62f, .95f)
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Grabbit", style = gradientTitle.copy(fontSize = 20.sp))
        Box(Modifier.fillMaxWidth().height(36.dp).clip(RoundedCornerShape(10.dp)).background(G.surface2).padding(horizontal = 10.dp), contentAlignment = Alignment.CenterStart) {
            Text("https://youtu.be/k8Xx2", fontSize = 11.sp, color = G.text)
        }
        Box(Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF0C0E28), Color(0xFF46205A)))))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(G.surface2).padding(3.dp)) {
            Box(Modifier.weight(1f).height(26.dp).clip(RoundedCornerShape(8.dp)).background(G.gradient), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.video), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Box(Modifier.weight(1f).height(26.dp), contentAlignment = Alignment.Center) { Text(stringResource(R.string.audio), fontSize = 11.sp, color = G.muted) }
        }
        Text(stringResource(R.string.quality).uppercase(), fontSize = 9.sp, color = G.muted, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            MiniChip("Best", false); MiniChip("1080p", !picked720); Box(Modifier.target("720")) { MiniChip("720p", picked720) }; MiniChip("480p", false)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { MiniChip("MP4", true); MiniChip("MKV", false); MiniChip("WEBM", false) }
        Box(Modifier.fillMaxWidth().height(38.dp).target("dl").clip(RoundedCornerShape(12.dp)).background(G.gradient), contentAlignment = Alignment.Center) {
            if (dl > 0f) Box(Modifier.align(Alignment.CenterStart).fillMaxHeight().fillMaxWidth(dl).background(Color.White.copy(alpha = .25f)))
            Text(if (dl <= 0f) "⬇  " + stringResource(R.string.download) else if (dl < 1f) "${(dl * 100).toInt()}%" else "✓", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
    Finger(t, listOf(.05f to "home", .25f to "720", .42f to "720", .58f to "dl"), taps = listOf(.26f, .58f), visible = 0f..0.9f)
}

@Composable
private fun BoxScope.DownloadsScene(t: Float) {
    val prog = seg(t, 0f, .35f)
    val selected = t > .52f
    val bar = seg(t, .58f, .7f)
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.tab_downloads), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        listOf(
            Triple(Color(0xFF46205A), "Kuwait City at night", true),
            Triple(Color(0xFF0A5A8C), "Ocean waves", false),
            Triple(Color(0xFF2B8159), "Brunch photos", false),
        ).forEachIndexed { i, (c, title, running) ->
            val sel = i == 1 && selected
            Row(
                Modifier.fillMaxWidth().then(if (i == 1) Modifier.target("item") else Modifier).clip(RoundedCornerShape(12.dp)).background(if (sel) G.violet.copy(alpha = .2f) else G.surface)
                    .then(if (sel) Modifier.border(1.dp, G.violet, RoundedCornerShape(12.dp)) else Modifier).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selected) { Icon(if (sel) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null, Modifier.size(16.dp), tint = if (sel) G.violet else G.muted); Spacer(Modifier.width(6.dp)) }
                Box(Modifier.size(40.dp, 30.dp).clip(RoundedCornerShape(6.dp)).background(c))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    if (running && prog < 1f) {
                        Text("${(prog * 100).toInt()}%", fontSize = 9.sp, color = G.violet)
                        Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(G.surface2)) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth(prog).background(G.pink))
                        }
                    } else Text(stringResource(R.string.done), fontSize = 9.sp, color = G.green)
                }
                if (!selected) Icon(Icons.Rounded.Share, null, Modifier.size(14.dp), tint = G.violet)
            }
        }
    }
    if (bar > 0f) Row(
        Modifier.align(Alignment.BottomCenter).fillMaxWidth().offset(y = 60.dp * (1 - bar)).background(G.surface).padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        listOf(Icons.Rounded.Transform to R.string.act_convert, Icons.Rounded.ClearAll to R.string.act_clear, Icons.Rounded.DeleteOutline to R.string.act_delete).forEach { (ic, l) ->
            Column(Modifier.then(if (l == R.string.act_convert) Modifier.target("convert") else Modifier), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(ic, null, Modifier.size(18.dp), tint = if (l == R.string.act_delete) G.red else G.text)
                Text(stringResource(l), fontSize = 9.sp, color = if (l == R.string.act_delete) G.red else G.text)
            }
        }
    }
    Finger(t, listOf(.28f to "home", .42f to "item", .72f to "item", .84f to "convert"), taps = listOf(.42f, .86f), visible = .28f..1f)
}

@Composable
private fun BoxScope.RecordScene(t: Float) {
    // a game/app on screen
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF2A1458), Color(0xFF0E0D14)))))
    Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Rounded.SportsEsports, null, Modifier.size(64.dp), tint = Color.White.copy(alpha = .35f))
    }
    // notification shade drops, then goes away
    val down = seg(t, .05f, .2f) * (1 - seg(t, .42f, .52f))
    if (down > 0f) Column(
        Modifier.fillMaxWidth().offset(y = (-170).dp * (1 - down)).background(Color(0xF21C1A25)).padding(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { repeat(4) { Box(Modifier.size(34.dp).clip(CircleShape).background(G.surface2)) } }
        Spacer(Modifier.height(12.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(G.surface2).padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.FiberManualRecord, null, Modifier.size(14.dp), tint = G.pink); Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.shortcut_title), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.shortcut_record).uppercase(), fontSize = 11.sp, color = G.violet, fontWeight = FontWeight.Bold, modifier = Modifier.target("record"))
        }
    }
    // countdown 3-2-1 then REC bubble
    val c = seg(t, .52f, .88f)
    if (c > 0f && c < 1f) {
        val n = 3 - (c * 3).toInt().coerceAtMost(2)
        Box(Modifier.align(Alignment.Center).size(110.dp).clip(CircleShape).background(G.gradient), contentAlignment = Alignment.Center) {
            Text("$n", fontSize = 54.sp, fontWeight = FontWeight.Black, color = Color.White)
        }
    }
    if (t > .9f) Text(
        "■  00:0${((t - .9f) * 60).toInt().coerceAtMost(5)}", Modifier.padding(12.dp).clip(RoundedCornerShape(20.dp)).background(G.red).padding(horizontal = 10.dp, vertical = 5.dp),
        color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold,
    )
    Finger(t, listOf(.2f to "home", .32f to "record"), taps = listOf(.33f), visible = .18f..0.46f)
}

@Composable
private fun BoxScope.SettingsScene(t: Float) {
    val auto = t > .3f
    val ig = t > .68f
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.tab_settings), fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(G.surface).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.when_grab).uppercase(), fontSize = 9.sp, color = G.muted, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                MiniChip(stringResource(R.string.ask_every_time), !auto); Box(Modifier.target("auto")) { MiniChip(stringResource(R.string.download_right_away), auto) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) { MiniChip("1080p", true); MiniChip("MP4", true); MiniChip("MP3", false) }
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(G.surface).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CameraAlt, null, Modifier.size(16.dp), tint = G.violet); Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.set_instagram), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            if (ig) Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.CheckCircle, null, Modifier.size(16.dp), tint = G.green); Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.ig_connected), fontSize = 11.sp)
            } else Box(Modifier.fillMaxWidth().height(30.dp).target("ig").clip(RoundedCornerShape(10.dp)).background(G.gradient), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.ig_connect), fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(G.surface).padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Speed, null, Modifier.size(16.dp), tint = G.violet); Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.fast_title), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Box(Modifier.size(30.dp, 18.dp).clip(CircleShape).background(G.violet), contentAlignment = Alignment.CenterEnd) {
                    Box(Modifier.padding(2.dp).size(14.dp).clip(CircleShape).background(Color.White))
                }
            }
        }
    }
    Finger(t, listOf(.05f to "home", .22f to "auto", .4f to "auto", .56f to "ig"), taps = listOf(.23f, .58f), visible = 0f..0.8f)
}
