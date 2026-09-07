package com.fsck.k9

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.junit.Test
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication

class AppCoroutineScopeModuleTest {
    @Test
    fun `closing Koin should cancel the application coroutine scope`() {
        val koinApplication = koinApplication {
            modules(mainModule)
        }
        val applicationScope = koinApplication.koin.get<CoroutineScope>(named("AppCoroutineScope"))
        val applicationJob = applicationScope.coroutineContext[Job]

        koinApplication.close()

        assertThat(applicationJob).isNotNull()
        assertThat(applicationJob?.isCancelled).isEqualTo(true)
    }
}
