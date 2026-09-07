package com.fsck.k9.activity

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.View
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import com.fsck.k9.ui.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DualScreenSecondaryViewportTest {
    @Test
    fun `viewport uses theme background in light and dark modes`() {
        val lightContext = themedContext(com.google.android.material.R.style.Theme_Material3_Light_NoActionBar)
        val darkContext = themedContext(com.google.android.material.R.style.Theme_Material3_Dark_NoActionBar)

        val lightColor = createViewport(lightContext).backgroundColor
        val darkColor = createViewport(darkContext).backgroundColor

        assertThat(lightColor).isEqualTo(lightContext.themeBackgroundColor)
        assertThat(darkColor).isEqualTo(darkContext.themeBackgroundColor)
        assertThat(lightColor).isNotEqualTo(darkColor)
    }

    @Test
    fun `viewport exposes a basic accessibility explanation`() {
        val context = themedContext(com.google.android.material.R.style.Theme_Material3_Light_NoActionBar)
        val viewport = createViewport(context)

        assertThat(viewport.contentDescription).isEqualTo(
            context.getString(R.string.dual_screen_secondary_viewport_description),
        )
        assertThat(viewport.isClickable).isTrue()
        assertThat(viewport.isFocusable).isTrue()
        assertThat(viewport.importantForAccessibility).isEqualTo(View.IMPORTANT_FOR_ACCESSIBILITY_YES)
    }

    @Test
    fun `viewport accepts an activity-specific accessibility explanation`() {
        val context = themedContext(com.google.android.material.R.style.Theme_Material3_Light_NoActionBar)
        val viewport = DualScreenSecondaryViewport(
            context = context,
            sourceView = View(context),
            deviceProfile = DualScreenDeviceProfiles.KEMI_GENERATION_1,
            viewportContentDescription = "Read-only compose reference",
        )

        assertThat(viewport.contentDescription).isEqualTo("Read-only compose reference")
    }

    @Test
    fun `viewport maps scaled touch coordinates into the logical upper viewport`() {
        val context = themedContext(com.google.android.material.R.style.Theme_Material3_Light_NoActionBar)
        val sourceView = View(context).apply { layout(0, 0, 1920, 2560) }
        var forwardedEvent: MotionEvent? = null
        sourceView.setOnTouchListener { _, event ->
            forwardedEvent = MotionEvent.obtain(event)
            true
        }
        val viewport = DualScreenSecondaryViewport(
            context = context,
            sourceView = sourceView,
            deviceProfile = DualScreenDeviceProfiles.KEMI_GENERATION_1,
        ).apply { layout(0, 0, 3840, 2560) }
        val inputEvent = MotionEvent.obtain(0, 0, MotionEvent.ACTION_DOWN, 1920f, 1280f, 0)

        viewport.onTouchEvent(inputEvent)

        assertThat(forwardedEvent).isNotNull()
        assertThat(forwardedEvent?.x).isEqualTo(960f)
        assertThat(forwardedEvent?.y).isEqualTo(640f)
        inputEvent.recycle()
        forwardedEvent?.recycle()
    }

    private fun createViewport(context: Context): DualScreenSecondaryViewport {
        return DualScreenSecondaryViewport(
            context = context,
            sourceView = View(context),
            deviceProfile = DualScreenDeviceProfiles.KEMI_GENERATION_1,
        )
    }

    private fun themedContext(theme: Int): Context {
        val applicationContext = ApplicationProvider.getApplicationContext<Context>()
        return ContextThemeWrapper(applicationContext, theme)
    }

    private val View.backgroundColor: Int
        get() = (background as ColorDrawable).color

    private val Context.themeBackgroundColor: Int
        get() {
            val attributes = obtainStyledAttributes(intArrayOf(android.R.attr.colorBackground))
            return try {
                attributes.getColor(0, android.graphics.Color.TRANSPARENT)
            } finally {
                attributes.recycle()
            }
        }
}
