package net.thunderbird.feature.smartassistant.internal

import android.view.ViewGroup
import net.thunderbird.feature.smartassistant.SmartAssistantAccountReference
import net.thunderbird.feature.smartassistant.SmartAssistantAvailability
import net.thunderbird.feature.smartassistant.SmartAssistantContext
import net.thunderbird.feature.smartassistant.SmartAssistantEngine
import net.thunderbird.feature.smartassistant.SmartAssistantError
import net.thunderbird.feature.smartassistant.SmartAssistantExecutionResult
import net.thunderbird.feature.smartassistant.SmartAssistantPanelHost
import net.thunderbird.feature.smartassistant.SmartAssistantRequest

fun createNoOpSmartAssistantEngine(): SmartAssistantEngine = NoOpSmartAssistantEngine

fun createNoOpSmartAssistantPanelHost(): SmartAssistantPanelHost = NoOpSmartAssistantPanelHost

private object NoOpSmartAssistantEngine : SmartAssistantEngine {
    override val availability: SmartAssistantAvailability = SmartAssistantAvailability.UNAVAILABLE

    override suspend fun execute(request: SmartAssistantRequest): SmartAssistantExecutionResult {
        return SmartAssistantExecutionResult.Failure(SmartAssistantError.UNAVAILABLE)
    }

    override fun invalidate(context: SmartAssistantContext) = Unit

    override fun invalidateAccount(account: SmartAssistantAccountReference) = Unit
}

private object NoOpSmartAssistantPanelHost : SmartAssistantPanelHost {
    override val isAvailable: Boolean = false

    override fun attach(container: ViewGroup) = Unit

    override fun updateContext(context: SmartAssistantContext) = Unit

    override fun detach() = Unit
}
