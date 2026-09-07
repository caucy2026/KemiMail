package net.thunderbird.feature.smartassistant.internal

import net.thunderbird.feature.smartassistant.SmartAssistantCapability
import net.thunderbird.feature.smartassistant.SmartAssistantInput
import net.thunderbird.feature.smartassistant.SmartAssistantText

internal data class MinimizedSmartAssistantRequest(
    val capability: SmartAssistantCapability,
    val input: SmartAssistantInput,
)

internal sealed interface SmartAssistantProviderResult {
    data class Success(val output: SmartAssistantText) : SmartAssistantProviderResult
    data object Failure : SmartAssistantProviderResult
}

internal interface SmartAssistantProvider {
    val isAvailable: Boolean

    suspend fun execute(request: MinimizedSmartAssistantRequest): SmartAssistantProviderResult
}
