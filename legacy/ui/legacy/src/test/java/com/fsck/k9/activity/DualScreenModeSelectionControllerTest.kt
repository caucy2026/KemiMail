package com.fsck.k9.activity

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlin.test.Test
import net.thunderbird.core.preference.DualScreenMode

class DualScreenModeSelectionControllerTest {
    private val persistedModes = mutableListOf<DualScreenMode>()
    private var recreationCount = 0

    @Test
    fun `selecting current mode should not persist or recreate`() {
        val testSubject = createTestSubject(initialMode = DualScreenMode.IMMERSIVE)

        val changed = testSubject.select(DualScreenMode.IMMERSIVE)

        assertThat(changed).isFalse()
        assertThat(persistedModes).isEmpty()
        assertThat(recreationCount).isEqualTo(0)
    }

    @Test
    fun `selecting another mode should persist and recreate once`() {
        val testSubject = createTestSubject(initialMode = DualScreenMode.IMMERSIVE)

        val changed = testSubject.select(DualScreenMode.SMART)

        assertThat(changed).isTrue()
        assertThat(persistedModes).containsExactly(DualScreenMode.SMART)
        assertThat(recreationCount).isEqualTo(1)
    }

    @Test
    fun `selecting the newly active mode again should not repeat side effects`() {
        val testSubject = createTestSubject(initialMode = DualScreenMode.IMMERSIVE)

        testSubject.select(DualScreenMode.SMART)
        val changedAgain = testSubject.select(DualScreenMode.SMART)

        assertThat(changedAgain).isFalse()
        assertThat(persistedModes).containsExactly(DualScreenMode.SMART)
        assertThat(recreationCount).isEqualTo(1)
    }

    private fun createTestSubject(initialMode: DualScreenMode): DualScreenModeSelectionController {
        return DualScreenModeSelectionController(
            initialMode = initialMode,
            persistMode = { mode -> persistedModes.add(mode) },
            recreateActivity = { recreationCount++ },
        )
    }
}
