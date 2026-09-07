package com.fsck.k9.ui.base

import android.app.Activity
import android.os.Build
import android.view.Display
import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class ActivityDisplayCompatTest {
    @Test
    @Config(sdk = [Build.VERSION_CODES.P])
    fun `display id should resolve before Android 11`() {
        // Arrange
        val testSubject = Robolectric.buildActivity(Activity::class.java).setup().get()

        // Act
        val displayId = testSubject.getDisplayIdCompat()

        // Assert
        assertThat(displayId).isEqualTo(Display.DEFAULT_DISPLAY)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.R])
    fun `display id should resolve on Android 11 and later`() {
        // Arrange
        val testSubject = Robolectric.buildActivity(Activity::class.java).setup().get()

        // Act
        val displayId = testSubject.getDisplayIdCompat()

        // Assert
        assertThat(displayId).isEqualTo(Display.DEFAULT_DISPLAY)
    }
}
