package com.fsck.k9.ui.messageview

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isEqualTo
import com.fsck.k9.ui.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PlaceholderFragmentTest {
    @Test
    fun `custom message is rendered for smart workspace`() {
        val applicationContext = ApplicationProvider.getApplicationContext<Context>()
        val themedContext = ContextThemeWrapper(
            applicationContext,
            com.google.android.material.R.style.Theme_Material3_Light_NoActionBar,
        )
        val fragment = PlaceholderFragment.newInstance(R.string.dual_screen_smart_empty_message)
        val view = fragment.onCreateView(LayoutInflater.from(themedContext), null, null)!!

        fragment.onViewCreated(view, null)

        val emptyMessage = view.findViewById<TextView>(R.id.message_view_empty_text)
        assertThat(emptyMessage.text.toString()).isEqualTo(
            themedContext.getString(R.string.dual_screen_smart_empty_message),
        )
    }
}
