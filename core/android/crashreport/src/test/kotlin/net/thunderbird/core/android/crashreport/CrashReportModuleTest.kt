package net.thunderbird.core.android.crashreport

import android.content.Context
import kotlin.test.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify

class CrashReportModuleTest {
    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun `module provides recorder and repository`() {
        crashReportModule.verify(extraTypes = listOf(Context::class))
    }
}
