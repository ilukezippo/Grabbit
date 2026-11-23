package com.ilukezippo.grabbit.ui

import android.app.Application
import android.content.ContentValues
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import com.ilukezippo.grabbit.download.DownloadSpec
import com.ilukezippo.grabbit.download.DownloadTask
import com.ilukezippo.grabbit.download.Downloads
import com.ilukezippo.grabbit.download.SavedFile
import com.ilukezippo.grabbit.download.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class DownloadsEditTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.appcompat.app.AppCompatActivity>()

    private fun task(id: String, title: String, file: SavedFile) =
        DownloadTask(id, DownloadSpec(file.uri, title, null, null), Status.DONE, 100f, files = listOf(file))

    private fun f(n: String) = SavedFile("content://media/external/downloads/$n", "$n.mp4", "video/mp4", 1000)

    @Before fun setUp() {
        Downloads.setForPreview(listOf(task("1", "First video", f("1")), task("2", "Second video", f("2"))))
        rule.setContent { GrabbitTheme { DownloadsScreen() } }
    }

    @Test fun finishedItemsShowOnlyShare() {
        rule.onAllNodesWithText("Share").assertCountEquals(2)
        rule.onAllNodesWithText("Open").assertCountEquals(0)
        rule.onAllNodesWithText("Remove").assertCountEquals(0)
    }

    @Test fun clearOnlyRemovesFromList() {
        rule.onNodeWithText("Edit").performClick()
        rule.onNodeWithText("First video").performClick()
        rule.onNodeWithText("Clear").performClick()
        assertEquals(listOf("2"), Downloads.tasks.value.map { it.id })
        assertTrue(Downloads.lastDeleted.isEmpty())
    }

    @Test fun longPressSelectsAndDeleteRemovesFiles() {
        rule.onNodeWithText("Second video").performTouchInput { longClick() }
        rule.onNodeWithText("1 selected").assertExists()
        rule.onNodeWithText("Delete").performClick()
        rule.onNodeWithText("Delete 1 file?").assertExists()
        rule.onNodeWithText("Delete from phone").performClick()
        rule.waitUntil(5_000) { org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle(); Downloads.tasks.value.none { it.id == "2" } }
        assertEquals(listOf("1"), Downloads.tasks.value.map { it.id })
        assertEquals(listOf(f("2").uri), Downloads.lastDeleted)
    }

    @Test fun convertSheetOffersPictureFormatsForPictures() {
        val pic = SavedFile("content://media/external/downloads/9", "brunch.jpg", "image/jpeg", 1000)
        Downloads.setForPreview(listOf(task("9", "Brunch photo", pic)))
        rule.onNodeWithText("Brunch photo").performTouchInput { longClick() }
        rule.onNodeWithText("Convert").performClick()
        rule.onNodeWithText("PICTURES TO").assertExists()
        rule.onNodeWithText("PDF").assertExists()
        rule.onNodeWithText("VIDEOS AND SONGS TO").assertDoesNotExist()
        rule.onNodeWithText("WEBP").performClick()
    }

    @Test fun selectAllThenClear() {
        rule.onNodeWithText("Edit").performClick()
        rule.onNodeWithText("Select all").performClick()
        rule.onNodeWithText("2 selected").assertExists()
        rule.onNodeWithText("Clear").performClick()
        assertTrue(Downloads.tasks.value.isEmpty())
    }
}
