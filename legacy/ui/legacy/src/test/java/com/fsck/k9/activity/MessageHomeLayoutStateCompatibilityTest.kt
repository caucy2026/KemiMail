package com.fsck.k9.activity

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import com.fsck.k9.K9RobolectricTest
import com.fsck.k9.ui.R
import org.junit.Test

class MessageHomeLayoutStateCompatibilityTest : K9RobolectricTest() {
    @Test
    fun `all message home layouts retain the same state owner IDs`() {
        MESSAGE_HOME_LAYOUTS.forEach { layoutId ->
            val root = inflate(layoutId)

            REQUIRED_STATE_OWNER_IDS.forEach { viewId ->
                val stateOwner = root.findViewById<View>(viewId)
                assertThat(stateOwner).isNotNull()
                assertThat(stateOwner.id).isEqualTo(viewId)
                assertThat(stateOwner.isSaveEnabled).isTrue()
            }
        }
    }

    private fun inflate(layoutId: Int): ViewGroup {
        val applicationContext = ApplicationProvider.getApplicationContext<Context>()
        val themedContext = ContextThemeWrapper(
            applicationContext,
            com.google.android.material.R.style.Theme_Material3_Light_NoActionBar,
        )
        return LayoutInflater.from(themedContext).inflate(layoutId, null, false) as ViewGroup
    }

    private companion object {
        val MESSAGE_HOME_LAYOUTS = listOf(
            R.layout.message_list,
            R.layout.split_message_list,
            R.layout.smart_message_list,
        )
        val REQUIRED_STATE_OWNER_IDS = listOf(
            R.id.navigation_drawer_layout,
            R.id.content_container,
            R.id.message_list_container,
            R.id.message_view_container,
            R.id.message_list_progress,
        )
    }
}
