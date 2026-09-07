package com.fsck.k9.activity

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewStub
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.fsck.k9.K9RobolectricTest
import com.fsck.k9.ui.R
import kotlin.test.Test

class DualScreenComposeLayoutTest : K9RobolectricTest() {
    @Test
    fun `smart compose puts one read-only reference above one authoritative editor`() {
        val root = inflateSmartCompose()
        root.findViewById<ViewStub>(R.id.message_compose_content).inflate()

        assertThat(root.getChildAt(0).id).isEqualTo(R.id.dual_screen_compose_reference_panel)
        assertThat(root.getChildAt(1).id).isEqualTo(R.id.dual_screen_compose_editor_panel)
        assertThat(root.countViewsWithId(R.id.message_content)).isEqualTo(1)
        assertThat(root.countViewsWithId(R.id.attachments)).isEqualTo(1)

        val referenceBody = root.findViewById<TextView>(R.id.dual_screen_compose_reference_body)
        assertThat(referenceBody is EditText).isFalse()
        assertThat(referenceBody.isTextSelectable).isTrue()
        assertThat(referenceBody.autoLinkMask).isEqualTo(0)
        assertThat(referenceBody.linksClickable).isFalse()
    }

    @Test
    fun `smart compose viewport heights follow the selected device profile`() {
        val root = inflateSmartCompose()
        val referencePanel = root.findViewById<View>(R.id.dual_screen_compose_reference_panel)
        val editorPanel = root.findViewById<View>(R.id.dual_screen_compose_editor_panel)

        assertThat(referencePanel.layoutParams.height).isEqualTo(1280)
        assertThat(editorPanel.layoutParams.height).isEqualTo(1280)
        assertThat((referencePanel.layoutParams as LinearLayout.LayoutParams).weight).isEqualTo(0f)
        assertThat((editorPanel.layoutParams as LinearLayout.LayoutParams).weight).isEqualTo(0f)
    }

    private fun inflateSmartCompose(): LinearLayout {
        val applicationContext = ApplicationProvider.getApplicationContext<Context>()
        val themedContext = ContextThemeWrapper(
            applicationContext,
            com.google.android.material.R.style.Theme_Material3_Light_NoActionBar,
        )
        return (
            LayoutInflater.from(themedContext)
                .inflate(R.layout.smart_message_compose, null, false) as LinearLayout
            ).also { rootView ->
            SmartDualScreenComposeLayoutConfigurator.apply(
                rootView = rootView,
                deviceProfile = DualScreenDeviceProfiles.KEMI_GENERATION_1,
            )
        }
    }
}

private fun View.countViewsWithId(targetId: Int): Int {
    val ownCount = if (id == targetId) 1 else 0
    if (this !is ViewGroup) return ownCount

    return ownCount + (0 until childCount).sumOf { childIndex ->
        getChildAt(childIndex).countViewsWithId(targetId)
    }
}
