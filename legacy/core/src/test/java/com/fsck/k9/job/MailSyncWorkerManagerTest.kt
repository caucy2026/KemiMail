package com.fsck.k9.job

import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.fsck.k9.FakePlatformConfigProvider
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import net.thunderbird.core.android.account.LegacyAccountDto
import net.thunderbird.core.logging.legacy.Log
import net.thunderbird.core.logging.testing.TestLogger
import net.thunderbird.core.preference.BackgroundOps
import net.thunderbird.core.preference.GeneralSettings
import net.thunderbird.core.preference.GeneralSettingsManager
import net.thunderbird.core.preference.network.NetworkSettings
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalTime::class)
class MailSyncWorkerManagerTest {
    private val workManager = mock<WorkManager>()
    private val account = LegacyAccountDto(ACCOUNT_UUID).apply {
        automaticCheckIntervalMinutes = 15
        lastSyncTime = 0L
    }

    @Before
    fun setUp() {
        Log.logger = TestLogger()
    }

    @Test
    fun `scheduleMailSync updates existing periodic work`() {
        val testSubject = createTestSubject(BackgroundOps.ALWAYS)

        testSubject.scheduleMailSync(account)

        verify(workManager).enqueueUniquePeriodicWork(
            eq("MailSync:$ACCOUNT_UUID"),
            eq(ExistingPeriodicWorkPolicy.UPDATE),
            any<PeriodicWorkRequest>(),
        )
        verify(workManager, never()).cancelUniqueWork(any())
    }

    @Test
    fun `scheduleMailSync cancels account work when background operations are disabled`() {
        val testSubject = createTestSubject(BackgroundOps.NEVER)

        testSubject.scheduleMailSync(account)

        verify(workManager).cancelUniqueWork("MailSync:$ACCOUNT_UUID")
        verify(workManager, never()).enqueueUniquePeriodicWork(any(), any(), any())
    }

    @Test
    fun `scheduleMailSync cancels account work when periodic sync is disabled`() {
        account.automaticCheckIntervalMinutes = LegacyAccountDto.INTERVAL_MINUTES_NEVER
        val testSubject = createTestSubject(BackgroundOps.ALWAYS)

        testSubject.scheduleMailSync(account)

        verify(workManager).cancelUniqueWork("MailSync:$ACCOUNT_UUID")
        verify(workManager, never()).enqueueUniquePeriodicWork(any(), any(), any())
    }

    private fun createTestSubject(backgroundOps: BackgroundOps): MailSyncWorkerManager {
        val settings = GeneralSettings(
            platformConfigProvider = FakePlatformConfigProvider(),
            network = NetworkSettings(backgroundOps),
        )
        val generalSettingsManager = mock<GeneralSettingsManager> {
            on { getConfig() } doReturn settings
        }

        return MailSyncWorkerManager(
            workManager = workManager,
            clock = Clock.System,
            syncDebugLogger = TestLogger(),
            generalSettingsManager = generalSettingsManager,
        )
    }

    private companion object {
        const val ACCOUNT_UUID = "00000000-0000-4000-8000-000000000003"
    }
}
