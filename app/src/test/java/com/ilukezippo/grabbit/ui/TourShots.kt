package com.ilukezippo.grabbit.ui

import android.app.Application
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h860dp-xxhdpi", application = Application::class)
class TourShots {
    @Test fun frames() {
        listOf(0 to .3f, 0 to .76f, 1 to .3f, 1 to .62f, 2 to .45f, 2 to .9f, 3 to .35f, 3 to .7f, 4 to .25f).forEach { (s, t) ->
            captureRoboImage("build/tour/s${s}_${(t * 100).toInt()}.png") { GrabbitTheme { TourFrame(s, t, {}, {}) } }
        }
    }
    @Config(qualifiers = "ar-w400dp-h860dp-xxhdpi")
    @Test fun arabic() = captureRoboImage("build/tour/ar.png") { GrabbitTheme { TourFrame(1, .3f, {}, {}) } }
}
