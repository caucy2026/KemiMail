package com.fsck.k9.job

import androidx.work.WorkManager
import net.thunderbird.core.android.account.LegacyAccountDto
import net.thunderbird.core.android.account.LegacyAccountDtoManager
import net.thunderbird.core.logging.legacy.Log
import net.thunderbird.core.logging.testing.TestLogger
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify

class K9JobManagerTest {
    private val workManager = mock<WorkManager>()
    private val accountOne = LegacyAccountDto("00000000-0000-4000-8000-000000000001")
    private val accountTwo = LegacyAccountDto("00000000-0000-4000-8000-000000000002")
    private val accountManager = mock<LegacyAccountDtoManager> {
        on { getAccounts() } doReturn listOf(accountOne, accountTwo)
    }
    private val mailSyncWorkerManager = mock<MailSyncWorkerManager>()
    private val syncDebugFileLogManager = mock<FileLogLimitWorkManager>()
    private val testSubject = K9JobManager(
        workManager = workManager,
        accountManager = accountManager,
        mailSyncWorkerManager = mailSyncWorkerManager,
        syncDebugFileLogManager = syncDebugFileLogManager,
    )

    @Before
    fun setUp() {
        Log.logger = TestLogger()
    }

    @Test
    fun `scheduleAllMailJobs updates each account without globally cancelling work`() {
        testSubject.scheduleAllMailJobs()

        verify(mailSyncWorkerManager).scheduleMailSync(accountOne)
        verify(mailSyncWorkerManager).scheduleMailSync(accountTwo)
        verify(workManager, never()).cancelAllWorkByTag(MailSyncWorkerManager.MAIL_SYNC_TAG)
    }

    @Test
    fun `scheduleMailSync delegates without cancelling the existing periodic work`() {
        testSubject.scheduleMailSync(accountOne)

        verify(mailSyncWorkerManager).scheduleMailSync(accountOne)
        verify(mailSyncWorkerManager, never()).cancelMailSync(accountOne)
    }

    @Test
    fun `cancelMailSync cancels only the requested account work`() {
        testSubject.cancelMailSync(accountOne)

        verify(mailSyncWorkerManager).cancelMailSync(accountOne)
        verify(workManager, never()).cancelAllWorkByTag(MailSyncWorkerManager.MAIL_SYNC_TAG)
    }

    @Test
    fun `cancelAllMailJobs cancels all tagged sync work`() {
        testSubject.cancelAllMailJobs()

        verify(workManager).cancelAllWorkByTag(MailSyncWorkerManager.MAIL_SYNC_TAG)
    }
}
