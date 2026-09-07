package com.fsck.k9.ui.choosefolder

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test

class ChooseFolderOverlayWindowSizeTest {
    @Test
    fun `eligible secondary display uses overlay chooser`() {
        val mode = ChooseFolderLaunchMode.resolve(hasEligibleSecondaryDisplay = true)

        assertThat(mode).isEqualTo(ChooseFolderLaunchMode.OVERLAY)
    }

    @Test
    fun `regular device keeps full screen chooser`() {
        val mode = ChooseFolderLaunchMode.resolve(hasEligibleSecondaryDisplay = false)

        assertThat(mode).isEqualTo(ChooseFolderLaunchMode.FULL_SCREEN)
    }

    @Test
    fun `dual-screen panel stays compact within the current display`() {
        val size = ChooseFolderOverlayWindowSize.calculate(
            availableWidth = 1920,
            availableHeight = 1280,
            density = 2f,
        )

        assertThat(size).isEqualTo(ChooseFolderOverlaySize(width = 1200, height = 960))
    }

    @Test
    fun `small display preserves margins around the panel`() {
        val size = ChooseFolderOverlayWindowSize.calculate(
            availableWidth = 360,
            availableHeight = 640,
            density = 1f,
        )

        assertThat(size).isEqualTo(ChooseFolderOverlaySize(width = 296, height = 480))
    }
}
