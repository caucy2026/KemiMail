package com.fsck.k9.activity.smartassistant

import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.isVisible
import net.thunderbird.feature.smartassistant.SmartAssistantContext
import net.thunderbird.feature.smartassistant.SmartAssistantPanelHost

internal class SmartAssistantPanelCoordinator(
    private val container: ViewGroup,
    private val panelHost: SmartAssistantPanelHost,
) {
    val isAvailable: Boolean
        get() = panelHost.isAvailable

    init {
        if (panelHost.isAvailable) {
            container.isVisible = true
            panelHost.attach(container)
        } else {
            hideAndClearContainer()
        }
    }

    fun updateContext(contextProvider: () -> SmartAssistantContext) {
        if (!panelHost.isAvailable) return

        panelHost.updateContext(contextProvider())
    }

    fun destroy() {
        if (panelHost.isAvailable) panelHost.detach()
        hideAndClearContainer()
    }

    private fun hideAndClearContainer() {
        container.removeAllViews()
        container.isGone = true
    }
}
