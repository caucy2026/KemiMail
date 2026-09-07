package com.fsck.k9.activity

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View.MeasureSpec
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.view.ActionMode
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import com.fsck.k9.ui.R
import com.google.android.material.appbar.MaterialToolbar
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DualScreenActionModeControllerTest {
    @Test
    fun `action mode should be hosted in the projected root`() {
        // Arrange
        val rootView = FrameLayout(themedContext())
        val callback = RecordingActionModeCallback()
        val testSubject = createTestSubject(rootView)

        // Act
        val actionMode = testSubject.start(callback)
        actionMode?.title = "3 selected"

        // Assert
        assertThat(actionMode).isNotNull()
        assertThat(rootView.childCount).isEqualTo(1)
        assertThat(rootView.findViewById<TextView>(R.id.dual_screen_action_mode_title).text.toString())
            .isEqualTo("3 selected")
        assertThat(callback.prepareCount).isEqualTo(1)
    }

    @Test
    fun `finishing action mode should remove it from the projected root`() {
        // Arrange
        val rootView = FrameLayout(themedContext())
        val callback = RecordingActionModeCallback()
        val testSubject = createTestSubject(rootView)
        testSubject.start(callback)

        // Act
        val result = testSubject.finish()

        // Assert
        assertThat(result).isEqualTo(true)
        assertThat(rootView.childCount).isEqualTo(0)
        assertThat(callback.destroyedMode).isNotNull()
    }

    @Test
    fun `action mode host should stay at toolbar height when parent requests full canvas height`() {
        // Arrange
        val rootView = FrameLayout(themedContext())
        val testSubject = createTestSubject(rootView)
        testSubject.start(RecordingActionModeCallback())
        val toolbarHost = rootView.getChildAt(0)
        val toolbar = rootView.findViewById<MaterialToolbar>(R.id.dual_screen_action_mode)

        // Act
        toolbarHost.measure(
            MeasureSpec.makeMeasureSpec(1_920, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(2_560, MeasureSpec.EXACTLY),
        )

        // Assert
        assertThat(toolbarHost.measuredHeight).isEqualTo(toolbar.minimumHeight)
        assertThat(toolbarHost.measuredHeight).isGreaterThan(0)
    }

    @Test
    fun `rejected action mode should not remain in the projected root`() {
        // Arrange
        val rootView = FrameLayout(themedContext())
        val testSubject = createTestSubject(rootView)
        val callback = RecordingActionModeCallback(acceptCreation = false)

        // Act
        val result = testSubject.start(callback)

        // Assert
        assertThat(result).isNull()
        assertThat(rootView.childCount).isEqualTo(0)
    }

    private fun themedContext(): Context {
        return ContextThemeWrapper(
            ApplicationProvider.getApplicationContext(),
            R.style.Theme_Legacy_Test,
        )
    }

    private fun createTestSubject(rootView: FrameLayout): DualScreenActionModeController {
        return DualScreenActionModeController(rootView, MenuInflater(rootView.context))
    }

    private class RecordingActionModeCallback(
        private val acceptCreation: Boolean = true,
    ) : ActionMode.Callback {
        var prepareCount = 0
        var destroyedMode: ActionMode? = null

        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
            menu.add("Delete")
            return acceptCreation
        }

        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
            prepareCount++
            return true
        }

        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean = true

        override fun onDestroyActionMode(mode: ActionMode) {
            destroyedMode = mode
        }
    }
}
