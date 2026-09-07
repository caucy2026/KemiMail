package com.fsck.k9.activity

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlin.test.Test

class DualScreenProjectionLifecycleTest {
    @Test
    fun `initial prepared dual-screen workspace activates automatically`() {
        val lifecycle = DualScreenProjectionLifecycle()

        val decision = lifecycle.onStart(
            isSecondaryDisplayAvailable = true,
            isWorkspacePrepared = true,
            wasProjectionExpected = true,
        )

        assertThat(decision.action).isEqualTo(DualScreenProjectionAction.ACTIVATE)
        assertThat(decision.recoveryPending).isFalse()
        assertThat(decision.recoveryAvailable).isFalse()
    }

    @Test
    fun `single-screen launch stays inactive without a secondary display`() {
        val lifecycle = DualScreenProjectionLifecycle()

        val decision = lifecycle.onStart(
            isSecondaryDisplayAvailable = false,
            isWorkspacePrepared = true,
            wasProjectionExpected = false,
        )

        assertThat(decision.action).isEqualTo(DualScreenProjectionAction.DEACTIVATE)
        assertThat(decision.recoveryPending).isFalse()
    }

    @Test
    fun `hot-plug prompts instead of activating automatically`() {
        val lifecycle = DualScreenProjectionLifecycle()
        lifecycle.onStart(
            isSecondaryDisplayAvailable = false,
            isWorkspacePrepared = true,
            wasProjectionExpected = false,
        )

        val decision = lifecycle.onDisplayTopologyChanged(
            isSecondaryDisplayAvailable = true,
            isCurrentProjectionReusable = false,
        )

        assertThat(decision.action).isEqualTo(DualScreenProjectionAction.DEACTIVATE)
        assertThat(decision.recoveryPending).isTrue()
        assertThat(decision.recoveryAvailable).isTrue()
    }

    @Test
    fun `display loss deactivates an active projection and waits for recovery`() {
        val lifecycle = activeLifecycle()

        val decision = lifecycle.onDisplayTopologyChanged(
            isSecondaryDisplayAvailable = false,
            isCurrentProjectionReusable = false,
        )

        assertThat(decision.action).isEqualTo(DualScreenProjectionAction.DEACTIVATE)
        assertThat(decision.recoveryPending).isTrue()
        assertThat(decision.recoveryAvailable).isFalse()
        assertThat(decision.recoveryStarted).isTrue()
    }

    @Test
    fun `reconnected display exposes recovery without activating`() {
        val lifecycle = activeLifecycle()
        lifecycle.onDisplayTopologyChanged(
            isSecondaryDisplayAvailable = false,
            isCurrentProjectionReusable = false,
        )

        val decision = lifecycle.onDisplayTopologyChanged(
            isSecondaryDisplayAvailable = true,
            isCurrentProjectionReusable = false,
        )

        assertThat(decision.action).isEqualTo(DualScreenProjectionAction.DEACTIVATE)
        assertThat(decision.recoveryPending).isTrue()
        assertThat(decision.recoveryAvailable).isTrue()
        assertThat(decision.recoveryStarted).isFalse()
    }

    @Test
    fun `user can restore a prepared workspace after reconnect`() {
        val lifecycle = pendingRecoveryLifecycle()

        val decision = lifecycle.onRecoveryAccepted(
            isSecondaryDisplayAvailable = true,
            isWorkspacePrepared = true,
        )

        assertThat(decision.action).isEqualTo(DualScreenProjectionAction.ACTIVATE)
        assertThat(decision.recoveryPending).isFalse()
    }

    @Test
    fun `user recovery recreates an unprepared smart workspace`() {
        val lifecycle = pendingRecoveryLifecycle()

        val decision = lifecycle.onRecoveryAccepted(
            isSecondaryDisplayAvailable = true,
            isWorkspacePrepared = false,
        )

        assertThat(decision.action).isEqualTo(DualScreenProjectionAction.RECREATE_WORKSPACE)
        assertThat(decision.recoveryPending).isFalse()
    }

    @Test
    fun `Home and resume with the same display restores without prompting`() {
        val lifecycle = activeLifecycle()

        val stopDecision = lifecycle.onStop()
        val startDecision = lifecycle.onStart(
            isSecondaryDisplayAvailable = true,
            isWorkspacePrepared = true,
            wasProjectionExpected = true,
        )

        assertThat(stopDecision.action).isEqualTo(DualScreenProjectionAction.DEACTIVATE)
        assertThat(stopDecision.recoveryPending).isFalse()
        assertThat(startDecision.action).isEqualTo(DualScreenProjectionAction.ACTIVATE)
        assertThat(startDecision.recoveryAvailable).isFalse()
    }

    @Test
    fun `display loss while in background becomes pending on resume`() {
        val lifecycle = activeLifecycle()
        lifecycle.onStop()

        val decision = lifecycle.onStart(
            isSecondaryDisplayAvailable = false,
            isWorkspacePrepared = true,
            wasProjectionExpected = true,
        )

        assertThat(decision.action).isEqualTo(DualScreenProjectionAction.DEACTIVATE)
        assertThat(decision.recoveryPending).isTrue()
        assertThat(decision.recoveryAvailable).isFalse()
    }

    @Test
    fun `unexpected Presentation dismissal requires explicit recovery`() {
        val lifecycle = activeLifecycle()

        val decision = lifecycle.onPresentationDismissed(isSecondaryDisplayAvailable = true)

        assertThat(decision.action).isEqualTo(DualScreenProjectionAction.DEACTIVATE)
        assertThat(decision.recoveryPending).isTrue()
        assertThat(decision.recoveryAvailable).isTrue()
    }

    private fun activeLifecycle(): DualScreenProjectionLifecycle {
        return DualScreenProjectionLifecycle().apply {
            onStart(
                isSecondaryDisplayAvailable = true,
                isWorkspacePrepared = true,
                wasProjectionExpected = true,
            )
        }
    }

    private fun pendingRecoveryLifecycle(): DualScreenProjectionLifecycle {
        return DualScreenProjectionLifecycle(recoveryPending = true)
    }
}
