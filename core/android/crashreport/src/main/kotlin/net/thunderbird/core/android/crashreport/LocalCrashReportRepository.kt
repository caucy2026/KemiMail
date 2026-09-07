package net.thunderbird.core.android.crashreport

internal class LocalCrashReportRepository(
    private val reportFile: CrashReportFile,
    private val reportFactory: CrashReportFactory,
) : CrashReportRecorder, CrashReportRepository {
    private val lock = Any()

    override fun record(throwable: Throwable) {
        try {
            val payload = CrashReportCodec.encode(reportFactory.create(throwable))
            synchronized(lock) {
                reportFile.writeAtomically(payload)
            }
        } catch (_: Exception) {
            // Crash recording is best effort. Never mask or delay the original failure.
        }
    }

    override fun readLatest(): CrashReport? {
        return synchronized(lock) {
            try {
                val payload = reportFile.read() ?: return@synchronized null
                CrashReportCodec.decode(payload) ?: run {
                    reportFile.delete()
                    null
                }
            } catch (_: Exception) {
                deleteBestEffort()
                null
            }
        }
    }

    override fun clear() {
        synchronized(lock) {
            deleteBestEffort()
        }
    }

    private fun deleteBestEffort() {
        try {
            reportFile.delete()
        } catch (_: Exception) {
            // A failed cleanup must not crash the app or expose the report elsewhere.
        }
    }
}
