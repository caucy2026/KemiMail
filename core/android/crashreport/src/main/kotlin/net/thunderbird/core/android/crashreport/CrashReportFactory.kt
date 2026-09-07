package net.thunderbird.core.android.crashreport

import java.security.MessageDigest
import java.util.Collections
import java.util.IdentityHashMap

internal const val CRASH_REPORT_FORMAT_VERSION = 1
private const val MAX_CAUSE_DEPTH = 4
private const val MAX_STORED_FRAMES_PER_CAUSE = 12
private const val MAX_FINGERPRINT_FRAMES_PER_CAUSE = 32
private const val MAX_SCANNED_FRAMES_PER_CAUSE = 64
private const val MAX_CODE_NAME_LENGTH = 160
private const val MAX_METHOD_NAME_LENGTH = 80
private const val MAX_METADATA_LENGTH = 64
private const val MILLISECONDS_PER_SECOND = 1_000L
private const val MIN_ANDROID_SDK = 1
private const val MAX_ANDROID_SDK = 1_000
private const val MIN_LINE_NUMBER = -1
private const val MAX_LINE_NUMBER = 1_000_000
private const val BITS_PER_HEX_DIGIT = 4
private const val LOW_NIBBLE_MASK = 0x0f
private const val UNSIGNED_BYTE_MASK = 0xff
private const val UNKNOWN_VALUE = "unknown"
private val APPLICATION_PACKAGE_PREFIXES = listOf("app.k9mail.", "com.fsck.k9.", "net.thunderbird.")
private val HEX_DIGITS = "0123456789abcdef".toCharArray()

internal data class CrashReportEnvironment(
    val appVersion: String,
    val appVersionCode: Long,
    val androidSdk: Int,
    val manufacturer: String,
    val model: String,
)

internal fun interface CrashReportEnvironmentProvider {
    fun get(): CrashReportEnvironment
}

internal class CrashReportFactory(
    private val environmentProvider: CrashReportEnvironmentProvider,
    private val epochSecondsProvider: () -> Long = { System.currentTimeMillis() / MILLISECONDS_PER_SECOND },
) {
    fun create(throwable: Throwable): CrashReport {
        val environment = environmentProvider.get()
        val exceptions = collectExceptions(throwable)

        return CrashReport(
            formatVersion = CRASH_REPORT_FORMAT_VERSION,
            occurredAtEpochSeconds = epochSecondsProvider().coerceAtLeast(0L),
            appVersion = normalizeMetadata(environment.appVersion),
            appVersionCode = environment.appVersionCode.coerceAtLeast(0L),
            androidSdk = environment.androidSdk.coerceIn(MIN_ANDROID_SDK, MAX_ANDROID_SDK),
            manufacturer = normalizeMetadata(environment.manufacturer),
            model = normalizeMetadata(environment.model),
            exceptions = exceptions.map { it.report },
            fingerprint = fingerprint(exceptions),
        )
    }

    private fun collectExceptions(throwable: Throwable): List<CollectedException> {
        val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
        val exceptions = mutableListOf<CollectedException>()
        var current: Throwable? = throwable

        while (current != null && exceptions.size < MAX_CAUSE_DEPTH && seen.add(current)) {
            val type = normalizeCodeName(current.javaClass.name, MAX_CODE_NAME_LENGTH)
            val boundedStackTrace = current.stackTrace.take(MAX_SCANNED_FRAMES_PER_CAUSE)
            val fingerprintFrames = boundedStackTrace
                .take(MAX_FINGERPRINT_FRAMES_PER_CAUSE)
                .map(::normalizeFrame)
            val storedFrames = boundedStackTrace
                .asSequence()
                .filter { frame -> APPLICATION_PACKAGE_PREFIXES.any(frame.className::startsWith) }
                .take(MAX_STORED_FRAMES_PER_CAUSE)
                .map(::normalizeFrame)
                .toList()

            exceptions += CollectedException(
                report = CrashException(type = type, frames = storedFrames),
                fingerprintFrames = fingerprintFrames,
            )
            current = current.cause
        }

        return exceptions
    }

    private fun normalizeFrame(frame: StackTraceElement): CrashStackFrame {
        return CrashStackFrame(
            className = normalizeCodeName(frame.className, MAX_CODE_NAME_LENGTH),
            methodName = normalizeCodeName(frame.methodName, MAX_METHOD_NAME_LENGTH),
            lineNumber = frame.lineNumber.coerceIn(MIN_LINE_NUMBER, MAX_LINE_NUMBER),
        )
    }

    private fun fingerprint(exceptions: List<CollectedException>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        exceptions.forEach { exception ->
            digest.updateFramed(exception.report.type)
            exception.fingerprintFrames.forEach { frame ->
                digest.updateFramed(frame.className)
                digest.updateFramed(frame.methodName)
                digest.updateFramed(frame.lineNumber.toString())
            }
        }
        return digest.digest().toLowerHex()
    }
}

private data class CollectedException(
    val report: CrashException,
    val fingerprintFrames: List<CrashStackFrame>,
)

private fun normalizeMetadata(value: String): String {
    val normalized = value
        .take(MAX_METADATA_LENGTH)
        .map { character ->
            if (character.isAsciiLetterOrDigit() || character in ".-_") character else '_'
        }
        .joinToString(separator = "")
        .trim('_')

    return normalized.ifEmpty { UNKNOWN_VALUE }
}

private fun normalizeCodeName(value: String, maximumLength: Int): String {
    val normalized = value
        .take(maximumLength)
        .map { character ->
            if (character.isAsciiLetterOrDigit() || character in ".\$<>_-") character else '_'
        }
        .joinToString(separator = "")
        .trim('_')

    return normalized.ifEmpty { UNKNOWN_VALUE }
}

private fun Char.isAsciiLetterOrDigit(): Boolean {
    return this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'
}

private fun MessageDigest.updateFramed(value: String) {
    val bytes = value.encodeToByteArray()
    update(bytes.size.toString().encodeToByteArray())
    update(0)
    update(bytes)
}

private fun ByteArray.toLowerHex(): String {
    return buildString(size * 2) {
        this@toLowerHex.forEach { byte ->
            val value = byte.toInt() and UNSIGNED_BYTE_MASK
            append(HEX_DIGITS[value ushr BITS_PER_HEX_DIGIT])
            append(HEX_DIGITS[value and LOW_NIBBLE_MASK])
        }
    }
}
