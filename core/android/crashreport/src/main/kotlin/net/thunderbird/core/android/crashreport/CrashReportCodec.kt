package net.thunderbird.core.android.crashreport

internal object CrashReportCodec {
    const val MAX_PAYLOAD_BYTES = 16 * 1_024

    private const val MIN_PRINTABLE_ASCII = 32
    private const val MAX_PRINTABLE_ASCII = 126
    private const val MIN_ANDROID_SDK = 1
    private const val MAX_ANDROID_SDK = 1_000
    private const val MAX_CAUSE_COUNT = 4
    private const val MAX_FRAME_COUNT = 12
    private const val MIN_LINE_NUMBER = -1
    private const val MAX_LINE_NUMBER = 1_000_000
    private val metadataPattern = Regex("[A-Za-z0-9._-]{1,64}")
    private val codeNamePattern = Regex("[A-Za-z0-9.\$_<>-]{1,160}")
    private val methodNamePattern = Regex("[A-Za-z0-9.\$_<>-]{1,80}")
    private val fingerprintPattern = Regex("[0-9a-f]{64}")

    fun encode(report: CrashReport): ByteArray {
        val payload = buildString {
            appendEntry("format", report.formatVersion)
            appendEntry("occurred_at_epoch_seconds", report.occurredAtEpochSeconds)
            appendEntry("app_version", report.appVersion)
            appendEntry("app_version_code", report.appVersionCode)
            appendEntry("android_sdk", report.androidSdk)
            appendEntry("manufacturer", report.manufacturer)
            appendEntry("model", report.model)
            appendEntry("fingerprint", report.fingerprint)
            appendEntry("exception_count", report.exceptions.size)
            report.exceptions.forEachIndexed { exceptionIndex, exception ->
                appendEntry("exception.$exceptionIndex.type", exception.type)
                appendEntry("exception.$exceptionIndex.frame_count", exception.frames.size)
                exception.frames.forEachIndexed { frameIndex, frame ->
                    val prefix = "exception.$exceptionIndex.frame.$frameIndex"
                    appendEntry("$prefix.class", frame.className)
                    appendEntry("$prefix.method", frame.methodName)
                    appendEntry("$prefix.line", frame.lineNumber)
                }
            }
        }.encodeToByteArray()

        require(payload.size <= MAX_PAYLOAD_BYTES) { "Crash report exceeds the local payload limit" }
        return payload
    }

    fun decode(payload: ByteArray): CrashReport? {
        if (!payload.hasValidShape()) return null

        return try {
            decodeValidPayload(payload)
        } catch (_: InvalidCrashReportException) {
            null
        }
    }

    private fun decodeValidPayload(payload: ByteArray): CrashReport {
        val entries = EntryReader(parseEntries(payload.decodeToString()))
        val formatVersion = entries.takeInt("format").requireValue { it == CRASH_REPORT_FORMAT_VERSION }
        val occurredAt = entries.takeLong("occurred_at_epoch_seconds").requireValue { it >= 0L }
        val appVersion = entries.takeMatching("app_version", metadataPattern)
        val appVersionCode = entries.takeLong("app_version_code").requireValue { it >= 0L }
        val androidSdk = entries.takeInt("android_sdk").requireValue { it in MIN_ANDROID_SDK..MAX_ANDROID_SDK }
        val manufacturer = entries.takeMatching("manufacturer", metadataPattern)
        val model = entries.takeMatching("model", metadataPattern)
        val fingerprint = entries.takeMatching("fingerprint", fingerprintPattern)
        val exceptionCount = entries.takeInt("exception_count").requireValue { it in 1..MAX_CAUSE_COUNT }
        val exceptions = decodeExceptions(entries, exceptionCount)
        entries.requireEmpty()

        return CrashReport(
            formatVersion = formatVersion,
            occurredAtEpochSeconds = occurredAt,
            appVersion = appVersion,
            appVersionCode = appVersionCode,
            androidSdk = androidSdk,
            manufacturer = manufacturer,
            model = model,
            exceptions = exceptions,
            fingerprint = fingerprint,
        )
    }

    private fun decodeExceptions(entries: EntryReader, count: Int): List<CrashException> {
        return List(count) { exceptionIndex ->
            val type = entries.takeMatching("exception.$exceptionIndex.type", codeNamePattern)
            val frameCount = entries.takeInt("exception.$exceptionIndex.frame_count")
                .requireValue { it in 0..MAX_FRAME_COUNT }
            CrashException(type = type, frames = decodeFrames(entries, exceptionIndex, frameCount))
        }
    }

    private fun decodeFrames(entries: EntryReader, exceptionIndex: Int, count: Int): List<CrashStackFrame> {
        return List(count) { frameIndex ->
            val prefix = "exception.$exceptionIndex.frame.$frameIndex"
            CrashStackFrame(
                className = entries.takeMatching("$prefix.class", codeNamePattern),
                methodName = entries.takeMatching("$prefix.method", methodNamePattern),
                lineNumber = entries.takeInt("$prefix.line")
                    .requireValue { it in MIN_LINE_NUMBER..MAX_LINE_NUMBER },
            )
        }
    }

    private fun parseEntries(text: String): MutableMap<String, String> {
        val entries = linkedMapOf<String, String>()
        text.lineSequence().filter(String::isNotEmpty).forEach { line ->
            val separatorIndex = line.indexOf('=')
            invalidIf(separatorIndex <= 0 || separatorIndex == line.lastIndex)
            val key = line.substring(0, separatorIndex)
            val value = line.substring(separatorIndex + 1)
            invalidIf(entries.put(key, value) != null)
        }
        return entries
    }

    private fun ByteArray.hasValidShape(): Boolean {
        return isNotEmpty() &&
            size <= MAX_PAYLOAD_BYTES &&
            decodeToString().all { character ->
                character == '\n' || character.code in MIN_PRINTABLE_ASCII..MAX_PRINTABLE_ASCII
            }
    }

    private fun StringBuilder.appendEntry(key: String, value: Any) {
        append(key).append('=').append(value).append('\n')
    }
}

private class EntryReader(
    private val entries: MutableMap<String, String>,
) {
    fun takeInt(key: String): Int = take(key).toIntOrNull() ?: invalidReport()

    fun takeLong(key: String): Long = take(key).toLongOrNull() ?: invalidReport()

    fun takeMatching(key: String, pattern: Regex): String = take(key).requireValue(pattern::matches)

    fun requireEmpty() {
        invalidIf(entries.isNotEmpty())
    }

    private fun take(key: String): String = entries.remove(key) ?: invalidReport()
}

private class InvalidCrashReportException : Exception()

private fun invalidIf(condition: Boolean) {
    if (condition) invalidReport()
}

private fun invalidReport(): Nothing = throw InvalidCrashReportException()

private inline fun <T> T.requireValue(predicate: (T) -> Boolean): T {
    invalidIf(!predicate(this))
    return this
}
