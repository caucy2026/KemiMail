package com.fsck.k9.activity

/**
 * Keeps display hot-plug decisions independent from Android callbacks.
 *
 * A projection that was only suspended by the Activity lifecycle can resume automatically. A newly attached,
 * replaced, or unexpectedly dismissed secondary display requires an explicit user recovery action.
 */
@Suppress("TooManyFunctions")
internal class DualScreenProjectionLifecycle(
    recoveryPending: Boolean = false,
) {
    private var phase = if (recoveryPending) Phase.RECOVERY_PENDING else Phase.INITIAL

    fun onStart(
        isSecondaryDisplayAvailable: Boolean,
        isWorkspacePrepared: Boolean,
        wasProjectionExpected: Boolean,
    ): DualScreenProjectionDecision {
        return when (phase) {
            Phase.INITIAL -> handleInitialStart(
                isSecondaryDisplayAvailable = isSecondaryDisplayAvailable,
                isWorkspacePrepared = isWorkspacePrepared,
                wasProjectionExpected = wasProjectionExpected,
            )
            Phase.PROJECTED,
            Phase.BACKGROUND_SUSPENDED,
            -> resumeSuspendedProjection(isSecondaryDisplayAvailable, isWorkspacePrepared)
            Phase.SINGLE_SCREEN -> startFromSingleScreen(isSecondaryDisplayAvailable)
            Phase.RECOVERY_PENDING -> requireRecovery(isSecondaryDisplayAvailable)
        }
    }

    private fun handleInitialStart(
        isSecondaryDisplayAvailable: Boolean,
        isWorkspacePrepared: Boolean,
        wasProjectionExpected: Boolean,
    ): DualScreenProjectionDecision {
        return when {
            isSecondaryDisplayAvailable && isWorkspacePrepared -> activateProjection()
            isSecondaryDisplayAvailable || wasProjectionExpected -> requireRecovery(isSecondaryDisplayAvailable)
            else -> useSingleScreen()
        }
    }

    private fun resumeSuspendedProjection(
        isSecondaryDisplayAvailable: Boolean,
        isWorkspacePrepared: Boolean,
    ): DualScreenProjectionDecision {
        return if (isSecondaryDisplayAvailable && isWorkspacePrepared) {
            activateProjection()
        } else {
            requireRecovery(isSecondaryDisplayAvailable)
        }
    }

    private fun startFromSingleScreen(isSecondaryDisplayAvailable: Boolean): DualScreenProjectionDecision {
        return if (isSecondaryDisplayAvailable) {
            requireRecovery(recoveryAvailable = true)
        } else {
            useSingleScreen()
        }
    }

    fun onDisplayTopologyChanged(
        isSecondaryDisplayAvailable: Boolean,
        isCurrentProjectionReusable: Boolean,
    ): DualScreenProjectionDecision {
        return when {
            phase == Phase.PROJECTED && isSecondaryDisplayAvailable && isCurrentProjectionReusable -> {
                decision(DualScreenProjectionAction.NONE)
            }

            phase == Phase.PROJECTED -> requireRecovery(isSecondaryDisplayAvailable)
            isSecondaryDisplayAvailable -> requireRecovery(recoveryAvailable = true)
            phase == Phase.RECOVERY_PENDING -> requireRecovery(recoveryAvailable = false)
            else -> useSingleScreen()
        }
    }

    fun onStop(): DualScreenProjectionDecision {
        phase = when (phase) {
            Phase.PROJECTED -> Phase.BACKGROUND_SUSPENDED
            Phase.RECOVERY_PENDING -> Phase.RECOVERY_PENDING
            else -> Phase.SINGLE_SCREEN
        }

        return decision(DualScreenProjectionAction.DEACTIVATE, recoveryAvailable = false)
    }

    fun onPresentationDismissed(
        isSecondaryDisplayAvailable: Boolean,
    ): DualScreenProjectionDecision {
        return requireRecovery(isSecondaryDisplayAvailable)
    }

    fun onRecoveryAccepted(
        isSecondaryDisplayAvailable: Boolean,
        isWorkspacePrepared: Boolean,
    ): DualScreenProjectionDecision {
        return when {
            !isSecondaryDisplayAvailable -> requireRecovery(recoveryAvailable = false)
            isWorkspacePrepared -> activateProjection()
            else -> {
                phase = Phase.INITIAL
                decision(DualScreenProjectionAction.RECREATE_WORKSPACE)
            }
        }
    }

    private fun activateProjection(): DualScreenProjectionDecision {
        phase = Phase.PROJECTED
        return decision(DualScreenProjectionAction.ACTIVATE)
    }

    private fun requireRecovery(recoveryAvailable: Boolean): DualScreenProjectionDecision {
        val recoveryStarted = phase != Phase.RECOVERY_PENDING
        phase = Phase.RECOVERY_PENDING
        return decision(
            action = DualScreenProjectionAction.DEACTIVATE,
            recoveryAvailable = recoveryAvailable,
            recoveryStarted = recoveryStarted,
        )
    }

    private fun useSingleScreen(): DualScreenProjectionDecision {
        phase = Phase.SINGLE_SCREEN
        return decision(DualScreenProjectionAction.DEACTIVATE)
    }

    private fun decision(
        action: DualScreenProjectionAction,
        recoveryAvailable: Boolean = false,
        recoveryStarted: Boolean = false,
    ): DualScreenProjectionDecision {
        return DualScreenProjectionDecision(
            action = action,
            recoveryPending = phase == Phase.RECOVERY_PENDING,
            recoveryAvailable = recoveryAvailable,
            recoveryStarted = recoveryStarted,
        )
    }

    private enum class Phase {
        INITIAL,
        PROJECTED,
        BACKGROUND_SUSPENDED,
        SINGLE_SCREEN,
        RECOVERY_PENDING,
    }
}

internal data class DualScreenProjectionDecision(
    val action: DualScreenProjectionAction,
    val recoveryPending: Boolean,
    val recoveryAvailable: Boolean,
    val recoveryStarted: Boolean,
)

internal enum class DualScreenProjectionAction {
    NONE,
    ACTIVATE,
    DEACTIVATE,
    RECREATE_WORKSPACE,
}
