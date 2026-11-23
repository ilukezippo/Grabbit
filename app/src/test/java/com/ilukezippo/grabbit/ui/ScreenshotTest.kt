package com.ilukezippo.grabbit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.ilukezippo.grabbit.engine.DirectItem
import com.ilukezippo.grabbit.engine.MediaInfo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xxhdpi")
class ScreenshotTest {
    private val video = MediaInfo("https://youtu.be/x", "Kuwait City skyline at night — 4K drone footage", null, 754, "Gulf Drones", "Youtube",
        listOf(2160, 1440, 1080, 720, 480, 360), false, 1)

    private fun shot(name: String, content: @androidx.compose.runtime.Composable () -> Unit) =
        captureRoboImage("build/shots/$name.png") { GrabbitTheme { Box(Modifier.background(G.bg).fillMaxSize().padding(20.dp)) { content() } } }

    @Test fun panelVideo() = shot("panel_video") { GrabPanel(video) {} }
    @Test fun panelLive() = shot("panel_live") { GrabPanel(video.copy(isLive = true, title = "LIVE: Makkah 24/7", durationSec = 0)) {} }
    @Test fun panelCarousel() = shot("panel_carousel") {
        GrabPanel(video.copy(site = "Instagram", entryCount = 4, durationSec = 0, direct = List(4) { DirectItem("u$it", it == 2) })) {}
    }
    @Test fun home() = captureRoboImage("build/shots/home.png") { GrabbitTheme { Box(Modifier.background(G.bg).fillMaxSize()) { ToolsScreen() } } }
}
