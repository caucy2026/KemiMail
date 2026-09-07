package com.fsck.k9.activity

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlin.test.Test
import net.thunderbird.core.preference.DualScreenMode

class DualScreenRuntimeStateTest {
    @Test
    fun `missing secondary display always resolves to single screen`() {
        DualScreenMode.entries.forEach { savedMode ->
            val state = DualScreenRuntimeState.resolve(
                savedMode = savedMode,
                isEligibleSecondaryDisplayAvailable = false,
            )

            assertThat(state).isEqualTo(DualScreenRuntimeState.SINGLE_SCREEN)
        }
    }

    @Test
    fun `eligible secondary display with immersive preference resolves to immersive`() {
        val state = DualScreenRuntimeState.resolve(
            savedMode = DualScreenMode.IMMERSIVE,
            isEligibleSecondaryDisplayAvailable = true,
        )

        assertThat(state).isEqualTo(DualScreenRuntimeState.IMMERSIVE)
        assertThat(state.isDualScreenAvailable).isTrue()
        assertThat(state.usesImmersiveCanvas).isTrue()
        assertThat(state.usesProjectedCanvas).isTrue()
        assertThat(state.usesSmartWorkspace).isFalse()
    }

    @Test
    fun `eligible secondary display with smart preference resolves to smart without immersive canvas`() {
        val state = DualScreenRuntimeState.resolve(
            savedMode = DualScreenMode.SMART,
            isEligibleSecondaryDisplayAvailable = true,
        )

        assertThat(state).isEqualTo(DualScreenRuntimeState.SMART)
        assertThat(state.isDualScreenAvailable).isTrue()
        assertThat(state.usesImmersiveCanvas).isFalse()
        assertThat(state.usesProjectedCanvas).isTrue()
        assertThat(state.usesSmartWorkspace).isTrue()
    }

    @Test
    fun `single screen is not exposed as an available dual screen mode`() {
        val state = DualScreenRuntimeState.SINGLE_SCREEN

        assertThat(state.isDualScreenAvailable).isFalse()
        assertThat(state.usesImmersiveCanvas).isFalse()
        assertThat(state.usesProjectedCanvas).isFalse()
        assertThat(state.usesSmartWorkspace).isFalse()
    }

    @Test
    fun `workspace only activates when activity prepared the matching layout`() {
        assertThat(
            DualScreenRuntimeState.SMART.canActivatePreparedWorkspace(DualScreenRuntimeState.SMART),
        ).isTrue()
        assertThat(
            DualScreenRuntimeState.SINGLE_SCREEN.canActivatePreparedWorkspace(DualScreenRuntimeState.SMART),
        ).isFalse()
        assertThat(
            DualScreenRuntimeState.IMMERSIVE.canActivatePreparedWorkspace(DualScreenRuntimeState.SMART),
        ).isFalse()
        assertThat(
            DualScreenRuntimeState.SMART.canActivatePreparedWorkspace(DualScreenRuntimeState.SINGLE_SCREEN),
        ).isTrue()
    }

    @Test
    fun `foldable observer is isolated while projection or recovery owns the layout`() {
        assertThat(
            shouldHandleFoldableStateChange(
                preparedState = DualScreenRuntimeState.IMMERSIVE,
                currentState = DualScreenRuntimeState.SINGLE_SCREEN,
                recoveryPending = false,
            ),
        ).isFalse()
        assertThat(
            shouldHandleFoldableStateChange(
                preparedState = DualScreenRuntimeState.SINGLE_SCREEN,
                currentState = DualScreenRuntimeState.SMART,
                recoveryPending = false,
            ),
        ).isFalse()
        assertThat(
            shouldHandleFoldableStateChange(
                preparedState = DualScreenRuntimeState.SINGLE_SCREEN,
                currentState = DualScreenRuntimeState.SINGLE_SCREEN,
                recoveryPending = true,
            ),
        ).isFalse()
        assertThat(
            shouldHandleFoldableStateChange(
                preparedState = DualScreenRuntimeState.SINGLE_SCREEN,
                currentState = DualScreenRuntimeState.SINGLE_SCREEN,
                recoveryPending = false,
            ),
        ).isTrue()
    }
}
