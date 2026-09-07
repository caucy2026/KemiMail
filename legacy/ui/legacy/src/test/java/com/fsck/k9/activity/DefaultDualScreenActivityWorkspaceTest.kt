package com.fsck.k9.activity

import android.content.pm.ActivityInfo
import android.view.Surface
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlin.test.Test
import net.thunderbird.core.preference.DualScreenMode

class DefaultDualScreenActivityWorkspaceTest {
    @Test
    fun `resolve should prepare immersive workspace when secondary display is available`() {
        val deviceProfile = createDeviceProfile()

        val result = DefaultDualScreenActivityWorkspace.resolve(
            savedMode = DualScreenMode.IMMERSIVE,
            deviceProfile = deviceProfile,
        )

        assertThat(result.runtimeState).isEqualTo(DualScreenRuntimeState.IMMERSIVE)
        assertThat(result.deviceProfile).isEqualTo(deviceProfile)
    }

    @Test
    fun `resolve should prepare projected workspace for smart mode on a regular activity`() {
        val deviceProfile = createDeviceProfile()

        val result = DefaultDualScreenActivityWorkspace.resolve(
            savedMode = DualScreenMode.SMART,
            deviceProfile = deviceProfile,
        )

        assertThat(result.runtimeState).isEqualTo(DualScreenRuntimeState.SMART)
        assertThat(result.deviceProfile).isEqualTo(deviceProfile)
    }

    @Test
    fun `resolve should use single screen workspace without a secondary display`() {
        val result = DefaultDualScreenActivityWorkspace.resolve(
            savedMode = DualScreenMode.IMMERSIVE,
            deviceProfile = null,
        )

        assertThat(result.runtimeState).isEqualTo(DualScreenRuntimeState.SINGLE_SCREEN)
        assertThat(result.deviceProfile).isNull()
    }

    private fun createDeviceProfile(): DualScreenDeviceProfile {
        return DualScreenDeviceProfile(
            name = "test",
            primaryDisplayId = 0,
            preferredSecondaryDisplayId = 2,
            physicalViewportWidth = 1080,
            physicalViewportHeight = 1920,
            logicalViewportWidth = 1080,
            logicalViewportHeight = 1920,
            supportedSecondaryRotations = setOf(Surface.ROTATION_0),
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
        )
    }
}
