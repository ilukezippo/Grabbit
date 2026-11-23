package com.ilukezippo.grabbit.ui

import android.app.Application
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SettingsAutoTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.appcompat.app.AppCompatActivity>()

    @Test fun switchesBetweenAskAndAuto() {
        rule.setContent { GrabbitTheme { SettingsScreen() } }
        val prefs = Prefs(rule.activity)
        assertFalse(prefs.autoDownload)
        rule.onNodeWithText("Download right away").performScrollTo().performClick()
        assertTrue(prefs.autoDownload)
        rule.onNodeWithText("Live streams still ask first", substring = true).assertExists()
        rule.onNodeWithText("Ask every time").performClick()
        assertFalse(prefs.autoDownload)
    }
}
