package com.fsck.k9.activity

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.drawerlayout.widget.DrawerLayout
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import com.fsck.k9.ui.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SmartMessageListLayoutTest {
    @Test
    fun `smart workspace places message above lower list workspace`() {
        val root = inflateSmartWorkspace(DualScreenDeviceProfiles.KEMI_GENERATION_1)
        val workspace = root.findViewById<FrameLayout>(R.id.smart_workspace)
        val workspaceContent = workspace.findViewById<LinearLayout>(R.id.smart_workspace_content)

        assertThat(workspaceContent.getChildAt(0).id).isEqualTo(R.id.dual_screen_attachment_upper_workspace)
        assertThat(workspaceContent.getChildAt(0).findViewById<ViewGroup>(R.id.message_view_container)).isNotNull()
        assertThat(
            workspaceContent.getChildAt(0).findViewById<ViewGroup>(R.id.dual_screen_attachment_preview_host).visibility,
        ).isEqualTo(View.GONE)
        assertThat(workspaceContent.getChildAt(2).id).isEqualTo(R.id.coordinator_layout)
        assertThat(workspaceContent.getChildAt(2).findViewById<ViewGroup>(R.id.message_list_container)).isNotNull()
        assertThat(
            workspaceContent.getChildAt(2).findViewById<ViewGroup>(R.id.smart_assistant_panel_host).visibility,
        ).isEqualTo(View.GONE)
        assertThat(
            workspaceContent.getChildAt(2).findViewById<ViewGroup>(R.id.dual_screen_attachment_action_host).visibility,
        ).isEqualTo(View.GONE)
        assertThat(workspace.findViewById<ViewGroup>(R.id.dual_screen_continuous_reader_host).visibility)
            .isEqualTo(View.GONE)
    }

    @Test
    fun `navigation drawer is constrained to physical lower viewport`() {
        val deviceProfile = DualScreenDeviceProfiles.KEMI_GENERATION_1
        val root = inflateSmartWorkspace(deviceProfile)
        val drawer = root.getChildAt(1) as ViewGroup
        val layoutParams = drawer.layoutParams as DrawerLayout.LayoutParams

        assertThat(layoutParams.height).isEqualTo(deviceProfile.logicalViewportHeight)
        assertThat(layoutParams.gravity and Gravity.BOTTOM == Gravity.BOTTOM).isTrue()
    }

    @Test
    fun `navigation drawer height follows an explicit later device profile`() {
        val laterProfile = DualScreenDeviceProfiles.KEMI_GENERATION_1.copy(
            name = "KEMI generation 2 test",
            logicalViewportHeight = 1440,
        )
        val root = inflateSmartWorkspace(laterProfile)

        val drawer = root.getChildAt(1) as ViewGroup

        assertThat(drawer.layoutParams.height).isEqualTo(1440)
    }

    private fun inflateSmartWorkspace(deviceProfile: DualScreenDeviceProfile): DrawerLayout {
        val applicationContext = ApplicationProvider.getApplicationContext<Context>()
        val themedContext = ContextThemeWrapper(
            applicationContext,
            com.google.android.material.R.style.Theme_Material3_Light_NoActionBar,
        )
        return (
            LayoutInflater.from(themedContext)
                .inflate(R.layout.smart_message_list, null, false) as DrawerLayout
            ).also { rootView -> SmartDualScreenLayoutConfigurator.apply(rootView, deviceProfile) }
    }
}
