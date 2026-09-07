package net.thunderbird.app.common.feature.smartassistant

import kotlin.test.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify

class AppCommonSmartAssistantModuleTest {
    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun `module provides the smart assistant API contracts`() {
        appCommonSmartAssistantModule.verify()
    }
}
