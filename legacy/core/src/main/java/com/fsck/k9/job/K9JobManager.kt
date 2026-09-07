package com.fsck.k9.job

import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.flow.Flow
import net.thunderbird.core.android.account.LegacyAccountDto
import net.thunderbird.core.android.account.LegacyAccountDtoManager
import net.thunderbird.core.logging.legacy.Log

class K9JobManager(
    private val workManager: WorkManager,
    private val accountManager: LegacyAccountDtoManager,
    private val mailSyncWorkerManager: MailSyncWorkerManager,
    private val syncDebugFileLogManager: FileLogLimitWorkManager,
) {
    fun scheduleDebugLogLimit(contentUriString: String): Flow<WorkInfo?> {
        return syncDebugFileLogManager.scheduleFileLogTimeLimit(contentUriString)
    }

    fun cancelDebugLogLimit() {
        syncDebugFileLogManager.cancelFileLogTimeLimit()
    }

    fun scheduleAllMailJobs() {
        Log.v("scheduling all jobs")
        scheduleMailSync()
    }

    fun scheduleMailSync(account: LegacyAccountDto) {
        mailSyncWorkerManager.scheduleMailSync(account)
    }

    fun cancelMailSync(account: LegacyAccountDto) {
        mailSyncWorkerManager.cancelMailSync(account)
    }

    fun cancelAllMailJobs() {
        Log.v("canceling all mail sync jobs")
        workManager.cancelAllWorkByTag(MailSyncWorkerManager.MAIL_SYNC_TAG)
    }

    private fun scheduleMailSync() {
        accountManager.getAccounts().forEach { account ->
            mailSyncWorkerManager.scheduleMailSync(account)
        }
    }
}
