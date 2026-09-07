package com.fsck.k9.activity

import net.thunderbird.core.preference.DualScreenMode

internal class DualScreenModeSelectionController(
    initialMode: DualScreenMode,
    private val persistMode: (DualScreenMode) -> Unit,
    private val recreateActivity: () -> Unit,
) {
    private var currentMode = initialMode

    fun select(mode: DualScreenMode): Boolean {
        if (mode == currentMode) return false

        currentMode = mode
        persistMode(mode)
        recreateActivity()
        return true
    }
}
