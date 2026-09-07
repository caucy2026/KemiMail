package net.thunderbird.feature.smartassistant

import android.view.ViewGroup

interface SmartAssistantPanelHost {
    val isAvailable: Boolean

    fun attach(container: ViewGroup)

    fun updateContext(context: SmartAssistantContext)

    fun detach()
}
