package com.ilukezippo.grabbit.instagram

import android.app.Application
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.geometry.Offset
import com.ilukezippo.grabbit.ui.GrabbitTheme
import com.ilukezippo.grabbit.ui.SettingsScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class IgLoginTest {
    @get:Rule val rule = createAndroidComposeRule<androidx.appcompat.app.AppCompatActivity>()

    @Test fun signInButtonOpensLoginScreen() {
        rule.setContent { GrabbitTheme { SettingsScreen() } }
        rule.onNodeWithText("Sign in to Instagram").performScrollTo().performClick()
        val next = shadowOf(rule.activity).nextStartedActivity
        assertEquals(InstagramLoginActivity::class.java.name, next?.component?.className)
    }

    @Test fun tappingEdgeOfButtonAlsoWorks() {
        rule.setContent { GrabbitTheme { SettingsScreen() } }
        val node = rule.onNodeWithText("Sign in to Instagram").performScrollTo()
        val b = node.fetchSemanticsNode().boundsInRoot
        val w = rule.onRoot().fetchSemanticsNode().size.width
        // tap near the left end of the full-width gradient bar, well away from the label
        rule.onRoot().performTouchInput { click(Offset(w * 0.12f, b.center.y)) }
        val next = shadowOf(rule.activity).nextStartedActivity
        assertEquals(InstagramLoginActivity::class.java.name, next?.component?.className)
    }

    @Test fun loginActivityStarts() {
        Robolectric.buildActivity(InstagramLoginActivity::class.java).setup()
    }
}
