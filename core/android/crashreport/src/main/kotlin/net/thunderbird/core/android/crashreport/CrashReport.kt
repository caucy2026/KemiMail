package net.thunderbird.core.android.crashreport

/** A privacy-safe, structured representation of the most recent application crash. */
data class CrashReport(
    val formatVersion: Int,
    val occurredAtEpochSeconds: Long,
    val appVersion: String,
    val appVersionCode: Long,
    val androidSdk: Int,
    val manufacturer: String,
    val model: String,
    val exceptions: List<CrashException>,
    val fingerprint: String,
)

data class CrashException(
    val type: String,
    val frames: List<CrashStackFrame>,
)

data class CrashStackFrame(
    val className: String,
    val methodName: String,
    val lineNumber: Int,
)

/** Records a crash synchronously. Implementations must never include exception messages. */
fun interface CrashReportRecorder {
    fun record(throwable: Throwable)
}

interface CrashReportRepository {
    fun readLatest(): CrashReport?

    fun clear()
}
