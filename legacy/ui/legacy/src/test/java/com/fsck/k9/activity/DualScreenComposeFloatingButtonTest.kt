package com.fsck.k9.activity

import android.app.Application
import android.view.ContextThemeWrapper
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DualScreenComposeFloatingButtonTest {
    @Test
    fun `dual screen compose button should remain visible during scrolling`() {
        val context = ContextThemeWrapper(
            ApplicationProvider.getApplicationContext<Application>(),
            com.google.android.material.R.style.Theme_Material3_Light_NoActionBar,
        )
        val button = FloatingActionButton(context).apply {
            layoutParams = CoordinatorLayout.LayoutParams(100, 100).apply {
                behavior = object : CoordinatorLayout.Behavior<FloatingActionButton>() {}
            }
            translationY = 200f
        }

        button.keepVisibleDuringDualScreenScrolling()

        val layoutParams = button.layoutParams as CoordinatorLayout.LayoutParams
        assertThat(layoutParams.behavior).isNull()
        assertThat(button.translationY).isEqualTo(0f)
    }
}
