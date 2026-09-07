package com.fsck.k9.activity

import android.view.Surface
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlin.test.Test

class DualScreenDeviceProfileTest {
    private val testSubject = DualScreenDeviceProfiles.KEMI_GENERATION_1

    @Test
    fun `current KEMI profile preserves the two viewport canvas`() {
        assertThat(testSubject.physicalViewportWidth).isEqualTo(1920)
        assertThat(testSubject.physicalViewportHeight).isEqualTo(1280)
        assertThat(testSubject.logicalViewportWidth).isEqualTo(1920)
        assertThat(testSubject.logicalViewportHeight).isEqualTo(1280)
        assertThat(testSubject.logicalCanvasHeight).isEqualTo(2560)
        assertThat(testSubject.primaryTranslationY).isEqualTo(-1280f)
        assertThat(testSubject.primaryDisplayId).isEqualTo(0)
        assertThat(testSubject.preferredSecondaryDisplayId).isEqualTo(2)
    }

    @Test
    fun `current KEMI profile keeps its strict physical size whitelist`() {
        assertThat(testSubject.matchesSecondaryDisplay(1920, 1280, Surface.ROTATION_0)).isTrue()
        assertThat(testSubject.matchesSecondaryDisplay(1280, 1920, Surface.ROTATION_0)).isFalse()
        assertThat(testSubject.matchesSecondaryDisplay(1920, 1080, Surface.ROTATION_0)).isFalse()
        assertThat(testSubject.matchesSecondaryDisplay(1920, 1280, rotation = 4)).isFalse()
    }

    @Test
    fun `physical viewport scaling maps rendering and touch into the logical upper viewport`() {
        assertThat(testSubject.presentationScaleX(viewportWidth = 3840)).isEqualTo(2f)
        assertThat(testSubject.presentationScaleY(viewportHeight = 2560)).isEqualTo(2f)

        assertThat(
            testSubject.secondaryLogicalPoint(
                viewportX = 1920f,
                viewportY = 1280f,
                viewportWidth = 3840,
                viewportHeight = 2560,
            ),
        ).isEqualTo(DualScreenLogicalPoint(x = 960f, y = 640f))
    }
}
