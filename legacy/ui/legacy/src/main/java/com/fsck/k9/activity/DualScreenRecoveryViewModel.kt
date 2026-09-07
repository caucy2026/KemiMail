package com.fsck.k9.activity

import androidx.lifecycle.ViewModel

/** Retains transient dual-screen workspace state across Activity recreation, not across process death or a new task. */
internal class DualScreenRecoveryViewModel : ViewModel() {
    var recoveryPending: Boolean = false
    var modeControlPosition: DualScreenFloatingControlPosition = DualScreenFloatingControlPosition.EDGE
    var composeControlPosition: DualScreenFloatingControlPosition = DualScreenFloatingControlPosition.EDGE
}
