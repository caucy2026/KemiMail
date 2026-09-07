package com.fsck.k9.ui.base

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.view.Display
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import kotlin.test.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class DefaultDisplayActivityRelocatorTest {
    private val testSubject = DefaultDisplayActivityRelocator

    @Test
    fun `activity should stay when Android does not support launch display options`() {
        // Act
        val shouldRelocate = testSubject.shouldRelocate(
            sdkInt = Build.VERSION_CODES.N_MR1,
            currentDisplayId = SECONDARY_DISPLAY_ID,
            relocationAttempted = false,
        )

        // Assert
        assertThat(shouldRelocate).isFalse()
    }

    @Test
    fun `activity should stay when it is already on the default display`() {
        // Act
        val shouldRelocate = testSubject.shouldRelocate(
            sdkInt = Build.VERSION_CODES.O,
            currentDisplayId = Display.DEFAULT_DISPLAY,
            relocationAttempted = false,
        )

        // Assert
        assertThat(shouldRelocate).isFalse()
    }

    @Test
    fun `activity should stay after one relocation attempt`() {
        // Act
        val shouldRelocate = testSubject.shouldRelocate(
            sdkInt = Build.VERSION_CODES.O,
            currentDisplayId = SECONDARY_DISPLAY_ID,
            relocationAttempted = true,
        )

        // Assert
        assertThat(shouldRelocate).isFalse()
    }

    @Test
    fun `activity should relocate from a secondary display`() {
        // Act
        val shouldRelocate = testSubject.shouldRelocate(
            sdkInt = Build.VERSION_CODES.O,
            currentDisplayId = SECONDARY_DISPLAY_ID,
            relocationAttempted = false,
        )

        // Assert
        assertThat(shouldRelocate).isTrue()
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.R])
    fun `relocation should preserve the incoming intent and target the default display`() {
        // Arrange
        val sourceIntent = Intent(Intent.ACTION_VIEW, Uri.parse("k9mail://messages")).apply {
            putExtra(TEST_EXTRA, TEST_VALUE)
        }
        val activity = Robolectric.buildActivity(Activity::class.java, sourceIntent).setup().get()

        // Act
        val relocated = testSubject.relocateIfNeeded(
            activity = activity,
            sourceIntent = sourceIntent,
            currentDisplayId = SECONDARY_DISPLAY_ID,
            sdkInt = Build.VERSION_CODES.R,
        )
        val startedActivity = shadowOf(activity).nextStartedActivityForResult
        val launchDisplayId = startedActivity.options.getInt(
            LAUNCH_DISPLAY_ID_OPTION,
            Display.INVALID_DISPLAY,
        )
        val relocatedAgain = testSubject.relocateIfNeeded(
            activity = activity,
            sourceIntent = startedActivity.intent,
            currentDisplayId = SECONDARY_DISPLAY_ID,
            sdkInt = Build.VERSION_CODES.R,
        )

        // Assert
        assertThat(relocated).isTrue()
        assertThat(activity.isFinishing).isTrue()
        assertThat(startedActivity.intent.action).isEqualTo(Intent.ACTION_VIEW)
        assertThat(startedActivity.intent.data).isEqualTo(Uri.parse("k9mail://messages"))
        assertThat(startedActivity.intent.getStringExtra(TEST_EXTRA)).isEqualTo(TEST_VALUE)
        assertThat(startedActivity.intent.component?.className).isEqualTo(Activity::class.java.name)
        assertThat(launchDisplayId).isEqualTo(Display.DEFAULT_DISPLAY)
        assertThat(relocatedAgain).isFalse()
        assertThat(shadowOf(activity).peekNextStartedActivityForResult()).isNull()
    }

    private companion object {
        const val SECONDARY_DISPLAY_ID = 2
        const val TEST_EXTRA = "test_extra"
        const val TEST_VALUE = "test_value"
        const val LAUNCH_DISPLAY_ID_OPTION = "android.activity.launchDisplayId"
    }
}
