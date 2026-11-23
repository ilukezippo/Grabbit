package com.ilukezippo.grabbit.ui

import android.app.Application
import android.content.Intent
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.ilukezippo.grabbit.download.DownloadSpec
import com.ilukezippo.grabbit.download.DownloadTask
import com.ilukezippo.grabbit.download.Downloads
import com.ilukezippo.grabbit.download.SavedFile
import com.ilukezippo.grabbit.download.Status
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class OpenOnTapTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.appcompat.app.AppCompatActivity>()

    @Test fun tappingAFinishedItemOpensTheFile() {
        val f = SavedFile("content://media/external/downloads/42", "Grabbit_rec.mp4", "video/mp4", 13_300_000)
        Downloads.setForPreview(listOf(DownloadTask("1", DownloadSpec(f.uri, "Screen recording", null, null), Status.DONE, 100f, files = listOf(f))))
        rule.setContent { GrabbitTheme { DownloadsScreen() } }
        rule.onNodeWithText("Screen recording").performClick()
        val started = shadowOf(rule.activity.application).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started?.action)
        assertEquals(f.uri, started?.dataString)
        assertEquals("video/mp4", started?.type)
    }
}
