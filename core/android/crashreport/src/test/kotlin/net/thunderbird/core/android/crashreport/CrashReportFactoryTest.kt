package net.thunderbird.core.android.crashreport

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import kotlin.test.Test

class CrashReportFactoryTest {
    private val environment = CrashReportEnvironment(
        appVersion = "20.1",
        appVersionCode = 39040,
        androidSdk = 31,
        manufacturer = "KEMI",
        model = "huanglong",
    )

    @Test
    fun `report and encoded payload exclude runtime sensitive text`() {
        val secret = "colin.mo@example.invalid subject-and-mail-body attachment-secret.pdf"
        val cause = IllegalArgumentException(secret).apply {
            stackTrace = arrayOf(
                StackTraceElement("net.thunderbird.mail.Reader", "decode", "Reader.kt", 42),
                StackTraceElement("outside.$secret", secret, secret, 9),
            )
        }
        val throwable = IllegalStateException(secret, cause).apply {
            addSuppressed(IllegalStateException(secret))
            stackTrace = arrayOf(
                StackTraceElement("com.fsck.k9.activity.MessageHomeActivity", "render", "MessageHomeActivity.kt", 73),
                StackTraceElement("third.party.Library", "invoke", "Library.kt", 10),
            )
        }

        val report = CrashReportFactory(
            environmentProvider = { environment },
            epochSecondsProvider = { 1_723_700_000L },
        ).create(throwable)
        val encoded = CrashReportCodec.encode(report).decodeToString()

        assertThat(encoded).doesNotContain(secret)
        assertThat(encoded).doesNotContain("example.invalid")
        assertThat(encoded).doesNotContain("attachment-secret")
        assertThat(report.exceptions.map { it.type }).containsExactly(
            "java.lang.IllegalStateException",
            "java.lang.IllegalArgumentException",
        )
        assertThat(report.exceptions.first().frames).containsExactly(
            CrashStackFrame(
                className = "com.fsck.k9.activity.MessageHomeActivity",
                methodName = "render",
                lineNumber = 73,
            ),
        )
    }

    @Test
    fun `cause depth and application frame count are bounded`() {
        val first = IllegalStateException("first").apply { stackTrace = emptyArray() }
        var current: Throwable = first
        repeat(10) { index ->
            val next = IllegalStateException("cause-$index").apply {
                stackTrace = Array(40) { frameIndex ->
                    StackTraceElement(
                        "net.thunderbird.feature.Component$frameIndex",
                        "method$frameIndex",
                        "Component.kt",
                        frameIndex,
                    )
                }
            }
            current.initCause(next)
            current = next
        }

        val report = CrashReportFactory(
            environmentProvider = { environment },
            epochSecondsProvider = { 1L },
        ).create(first)

        assertThat(report.exceptions.size).isEqualTo(4)
        assertThat(report.exceptions.first().frames.size).isEqualTo(0)
        assertThat(report.exceptions[1].frames.size).isEqualTo(12)
        assertThat(report.fingerprint.length).isEqualTo(64)
    }

    @Test
    fun `frames beyond the crash path scan budget are ignored`() {
        val throwable = IllegalStateException("private message").apply {
            stackTrace = Array(80) { index ->
                val className = if (index == 70) "net.thunderbird.mail.LateFrame" else "third.party.Frame$index"
                StackTraceElement(className, "method$index", "Frame.kt", index)
            }
        }

        val report = CrashReportFactory(
            environmentProvider = { environment },
            epochSecondsProvider = { 1L },
        ).create(throwable)

        assertThat(report.exceptions.single().frames.size).isEqualTo(0)
    }
}
