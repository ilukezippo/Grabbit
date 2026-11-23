package com.ilukezippo.grabbit.ui

import android.app.Application
import android.app.NotificationManager
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.ilukezippo.grabbit.Notifications
import com.ilukezippo.grabbit.recorder.RecordLauncherActivity
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class RecorderShortcutTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.appcompat.app.AppCompatActivity>()

    private fun shortcutShown(): Boolean {
        val nm = rule.activity.getSystemService(NotificationManager::class.java)
        return shadowOf(nm).allNotifications.isNotEmpty() && nm.activeNotifications.any { it.id == Notifications.ID_SHORTCUT }
    }

    @Test fun startRecordingOpensLauncherFromApp() {
        rule.setContent { GrabbitTheme { ToolsScreen() } }
        rule.onNodeWithText("Start recording").performScrollTo().performClick()
        val next = shadowOf(rule.activity).nextStartedActivity
        assertEquals(RecordLauncherActivity::class.java.name, next?.component?.className)
        assertTrue(next!!.getBooleanExtra(RecordLauncherActivity.EXTRA_FROM_APP, false))
    }

    @Test fun countdownChoiceIsSaved() {
        rule.setContent { GrabbitTheme { ToolsScreen() } }
        rule.onNodeWithText("10 s").performScrollTo().performClick()
        assertEquals(10, Prefs(rule.activity).recCountdown)
        rule.onNodeWithText("Off").performClick()
        assertEquals(0, Prefs(rule.activity).recCountdown)
    }

    @Test fun shortcutSwitchShowsAndHidesNotification() {
        Notifications.createChannels(rule.activity)
        Notifications.showRecorderShortcut(rule.activity)
        assertTrue(shortcutShown())
        Notifications.hideRecorderShortcut(rule.activity)
        assertFalse(rule.activity.getSystemService(NotificationManager::class.java).activeNotifications.any { it.id == Notifications.ID_SHORTCUT })
    }

    @Test fun launcherAsksForScreenCapture() {
        Prefs(org.robolectric.RuntimeEnvironment.getApplication()).recordAudio = false
        val a = Robolectric.buildActivity(RecordLauncherActivity::class.java).setup().get()
        val req = shadowOf(a).nextStartedActivityForResult
        assertNotNull("screen capture prompt should open", req)
    }
}
