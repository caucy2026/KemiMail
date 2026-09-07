package net.thunderbird.core.logging.config

import com.eygraber.uri.Uri
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import net.thunderbird.core.logging.LogEvent
import net.thunderbird.core.logging.LogLevel
import net.thunderbird.core.logging.LogSink
import net.thunderbird.core.logging.composite.CompositeLogSink
import net.thunderbird.core.logging.composite.CompositeLogSinkManager
import net.thunderbird.core.logging.file.FileLogSink

class DebugLogConfiguratorTest {
    private val compositeSink = FakeCompositeLogSink()
    private val fileLogSink = FakeFileLogSink()
    private var fileLogSinkCreationCount = 0
    private val testSubject = DebugLogConfigurator(
        syncDebugCompositeSink = compositeSink,
        syncDebugFileLogSink = lazy {
            fileLogSinkCreationCount++
            fileLogSink
        },
        platformInitializer = PlatformInitializer(),
    )

    @Test
    fun `disabled sync logging does not create the file sink`() {
        testSubject.updateSyncLogging(false)

        assertEquals(0, fileLogSinkCreationCount)
        assertNull(testSubject.getActiveSyncDebugFileLogSink())
    }

    @Test
    fun `enabled sync logging creates and attaches the file sink once`() {
        testSubject.updateSyncLogging(true)
        testSubject.updateSyncLogging(true)

        assertEquals(1, fileLogSinkCreationCount)
        assertSame(fileLogSink, testSubject.getActiveSyncDebugFileLogSink())
        assertEquals(listOf(fileLogSink), compositeSink.manager.getAll())
    }

    private class FakeCompositeLogSink : CompositeLogSink {
        override val level = LogLevel.DEBUG
        override val manager = FakeCompositeLogSinkManager()

        override fun log(event: LogEvent) = Unit
    }

    private class FakeCompositeLogSinkManager : CompositeLogSinkManager {
        private val sinks = mutableListOf<LogSink>()

        override fun getAll(): List<LogSink> = sinks.toList()

        override fun add(sink: LogSink) {
            if (sink !in sinks) sinks.add(sink)
        }

        override fun addAll(sinks: List<LogSink>) {
            sinks.forEach(::add)
        }

        override fun remove(sink: LogSink) {
            sinks.remove(sink)
        }

        override fun removeAll() {
            sinks.clear()
        }
    }

    private class FakeFileLogSink : FileLogSink {
        override val level = LogLevel.DEBUG

        override fun log(event: LogEvent) = Unit
        override suspend fun export(uri: Uri) = Unit
        override suspend fun flushAndCloseBuffer() = Unit
    }
}
