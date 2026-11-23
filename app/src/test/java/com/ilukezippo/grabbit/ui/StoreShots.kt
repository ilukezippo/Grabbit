package com.ilukezippo.grabbit.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.test.core.app.ApplicationProvider
import coil.Coil
import coil.ImageLoader
import com.github.takahirom.roborazzi.captureRoboImage
import com.ilukezippo.grabbit.core.AudioFormat
import com.ilukezippo.grabbit.core.Choice
import com.ilukezippo.grabbit.core.VideoContainer
import com.ilukezippo.grabbit.download.DownloadSpec
import com.ilukezippo.grabbit.download.DownloadTask
import com.ilukezippo.grabbit.download.Downloads
import com.ilukezippo.grabbit.download.SavedFile
import com.ilukezippo.grabbit.download.Status
import com.ilukezippo.grabbit.engine.DirectItem
import com.ilukezippo.grabbit.engine.Engine
import com.ilukezippo.grabbit.engine.EngineState
import com.ilukezippo.grabbit.engine.MediaInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Renders full app screens for the README and website. Output: app/build/store/. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xxhdpi", application = Application::class)
class StoreShots {
    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private fun thumb(name: String): String = File(javaClass.classLoader!!.getResource("thumbs/$name")!!.toURI()).absolutePath

    private val video get() = MediaInfo("https://youtu.be/k8Xx2", "Kuwait City skyline at night — 4K drone tour", thumb("city.jpg"), 754,
        "Gulf Drones", "YouTube", listOf(2160, 1440, 1080, 720, 480, 360), false, 1)

    @Before fun setUp() {
        Coil.setImageLoader(ImageLoader.Builder(app).dispatcher(Dispatchers.Unconfined).crossfade(false).build())
        @Suppress("UNCHECKED_CAST")
        (Engine::class.java.getDeclaredField("_state").apply { isAccessible = true }.get(Engine) as MutableStateFlow<EngineState>).value =
            EngineState.Ready("2026.11.15")
        Downloads.setForPreview(emptyList())
    }

    private fun shot(name: String, tab: Int, vm: GrabViewModel = GrabViewModel(app)) =
        captureRoboImage("build/store/$name.png") {
            GrabbitTheme { Box(Modifier.fillMaxSize().background(G.bg)) { AppScaffold(vm, tab) {} } }
        }

    @Test fun a_home() = shot("home", 0, GrabViewModel(app).apply { showForPreview(video.url, video) })

    @Test fun b_idle() = shot("idle", 0)

    @Test fun c_instagram() {
        val ig = MediaInfo("https://www.instagram.com/p/C9xYz/", "Weekend brunch spots in Kuwait", thumb("ig0.jpg"), 0, "foodie.kw",
            "Instagram", emptyList(), false, 4, (0..3).map { DirectItem("x$it", it == 2, thumb("ig$it.jpg")) })
        shot("instagram", 0, GrabViewModel(app).apply { showForPreview(ig.url, ig) })
    }

    @Test fun d_downloads() { d_downloadsSetup(); shot("downloads", 1) }

    private fun d_downloadsSetup() {
        fun f(n: String, mime: String, size: Long) = SavedFile("content://x/$n", n, mime, size)
        Downloads.setForPreview(listOf(
            DownloadTask("1", DownloadSpec("u", "Kuwait City skyline at night — 4K drone tour", thumb("city.jpg"), Choice.Video(2160, VideoContainer.MP4)),
                Status.RUNNING, 62f, 48),
            DownloadTask("2", DownloadSpec("u", "LIVE: Gulf sunset cam 24/7", thumb("desert.jpg"), Choice.Video(720, VideoContainer.MP4), isLive = true), Status.RUNNING),
            DownloadTask("3", DownloadSpec("u", "Ocean waves — 1 hour relaxing sounds", thumb("ocean.jpg"), Choice.Audio(AudioFormat.MP3)),
                Status.DONE, 100f, files = listOf(f("ocean.mp3", "audio/mpeg", 86_400_000))),
            DownloadTask("4", DownloadSpec("u", "Weekend brunch spots in Kuwait", thumb("ig1.jpg"), null),
                Status.DONE, 100f, files = List(4) { f("brunch ($it).jpg", "image/jpeg", 410_000) }),
            DownloadTask("5", DownloadSpec("u", "Alps timelapse in 8K", thumb("mountains.jpg"), Choice.Video(1080, VideoContainer.MKV)),
                Status.DONE, 100f, files = listOf(f("alps.mkv", "video/x-matroska", 412_000_000))),
        ))
    }

    @Test fun h_edit() {
        d_downloadsSetup()
        captureRoboImage("build/store/edit.png") {
            GrabbitTheme { Box(Modifier.fillMaxSize().background(G.bg)) { DownloadsScreen(previewEditing = true, previewSelected = setOf("3", "5")) } }
        }
    }

    @Test fun e_tools() = shot("tools", 2)

    @Test fun f_settings() = shot("settings", 3)

    @Config(qualifiers = "ar-w400dp-h860dp-xxhdpi")
    @Test fun g_arabic() {
        val ar = video.copy(title = "جولة بالطائرة فوق مدينة الكويت ليلًا", uploader = "Gulf Drones", thumbnail = thumb("concert.jpg"))
        shot("arabic", 0, GrabViewModel(app).apply { showForPreview(ar.url, ar) })
    }
}
