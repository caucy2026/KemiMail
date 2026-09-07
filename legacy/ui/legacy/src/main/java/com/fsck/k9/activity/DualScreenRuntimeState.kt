package com.fsck.k9.activity

import net.thunderbird.core.preference.DualScreenMode

internal enum class DualScreenRuntimeState {
    SINGLE_SCREEN,
    IMMERSIVE,
    SMART,
    ;

    val isDualScreenAvailable: Boolean
        get() = this != SINGLE_SCREEN

    val usesImmersiveCanvas: Boolean
        get() = this == IMMERSIVE

    val usesProjectedCanvas: Boolean
        get() = this != SINGLE_SCREEN

    val usesSmartWorkspace: Boolean
        get() = this == SMART

    companion object {
        fun resolve(
            savedMode: DualScreenMode,
            isEligibleSecondaryDisplayAvailable: Boolean,
        ): DualScreenRuntimeState {
            if (!isEligibleSecondaryDisplayAvailable) return SINGLE_SCREEN

            return when (savedMode) {
                DualScreenMode.IMMERSIVE -> IMMERSIVE
                DualScreenMode.SMART -> SMART
            }
        }
    }
}

internal fun DualScreenRuntimeState.canActivatePreparedWorkspace(targetState: DualScreenRuntimeState): Boolean {
    return targetState == DualScreenRuntimeState.SINGLE_SCREEN || this == targetState
}

internal fun shouldHandleFoldableStateChange(
    preparedState: DualScreenRuntimeState,
    currentState: DualScreenRuntimeState,
    recoveryPending: Boolean,
): Boolean {
    return !preparedState.usesProjectedCanvas &&
        !currentState.usesProjectedCanvas &&
        !recoveryPending
}
