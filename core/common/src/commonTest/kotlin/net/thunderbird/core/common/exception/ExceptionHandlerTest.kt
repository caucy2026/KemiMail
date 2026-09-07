package net.thunderbird.core.common.exception

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import kotlin.test.Test

class ExceptionHandlerTest {
    @Test
    fun `records without converting exception to text then delegates`() {
        val calls = mutableListOf<String>()
        val throwable = SensitiveThrowable()
        val defaultHandler = Thread.UncaughtExceptionHandler { _, received ->
            assertThat(received).isEqualTo(throwable)
            calls += "delegate"
        }
        val handler = ExceptionHandler(defaultHandler) { received ->
            assertThat(received).isEqualTo(throwable)
            calls += "record"
        }

        handler.uncaughtException(Thread.currentThread(), throwable)

        assertThat(calls).containsExactly("record", "delegate")
        assertThat(throwable.toStringCalls).isEqualTo(0)
    }

    @Test
    fun `delegates even when recorder fails`() {
        var delegated = false
        val handler = ExceptionHandler(
            defaultHandler = Thread.UncaughtExceptionHandler { _, _ -> delegated = true },
            exceptionRecorder = { error("storage unavailable") },
        )

        handler.uncaughtException(Thread.currentThread(), IllegalStateException("private message"))

        assertThat(delegated).isEqualTo(true)
    }

    @Test
    fun `recursive failure is recorded only once`() {
        var recordCount = 0
        var delegateCount = 0
        lateinit var handler: ExceptionHandler
        handler = ExceptionHandler(
            defaultHandler = Thread.UncaughtExceptionHandler { _, _ -> delegateCount++ },
            exceptionRecorder = {
                recordCount++
                handler.uncaughtException(Thread.currentThread(), IllegalStateException("nested private message"))
            },
        )

        handler.uncaughtException(Thread.currentThread(), IllegalStateException("original private message"))

        assertThat(recordCount).isEqualTo(1)
        assertThat(delegateCount).isEqualTo(2)
    }

    private class SensitiveThrowable : Throwable("private message") {
        var toStringCalls = 0

        override fun toString(): String {
            toStringCalls++
            return super.toString()
        }
    }
}
