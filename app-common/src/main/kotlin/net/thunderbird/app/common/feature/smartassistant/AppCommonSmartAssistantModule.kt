package net.thunderbird.app.common.feature.smartassistant

import net.thunderbird.feature.smartassistant.SmartAssistantEngine
import net.thunderbird.feature.smartassistant.SmartAssistantPanelHost
import net.thunderbird.feature.smartassistant.internal.createNoOpSmartAssistantEngine
import net.thunderbird.feature.smartassistant.internal.createNoOpSmartAssistantPanelHost
import org.koin.dsl.module

internal val appCommonSmartAssistantModule = module {
    single<SmartAssistantEngine> { createNoOpSmartAssistantEngine() }
    single<SmartAssistantPanelHost> { createNoOpSmartAssistantPanelHost() }
}
