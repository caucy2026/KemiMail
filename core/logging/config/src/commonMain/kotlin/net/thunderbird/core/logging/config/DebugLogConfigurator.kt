package net.thunderbird.core.logging.config

import net.thunderbird.core.logging.composite.CompositeLogSink
import net.thunderbird.core.logging.file.FileLogSink

class DebugLogConfigurator(
    private val syncDebugCompositeSink: CompositeLogSink,
    private val syncDebugFileLogSink: Lazy<FileLogSink>,
    private val platformInitializer: PlatformInitializer,
) {
    private var activeSyncDebugFileLogSink: FileLogSink? = null

    fun updateLoggingStatus(isDebugLoggingEnabled: Boolean) {
        platformInitializer.setUp(isDebugLoggingEnabled)
    }

    fun updateSyncLogging(isSyncLoggingEnabled: Boolean) {
        if (isSyncLoggingEnabled) {
            val fileLogSink = activeSyncDebugFileLogSink ?: syncDebugFileLogSink.value.also {
                activeSyncDebugFileLogSink = it
            }
            syncDebugCompositeSink.manager.add(fileLogSink)
        } else {
            activeSyncDebugFileLogSink?.let(syncDebugCompositeSink.manager::remove)
        }
    }

    fun getActiveSyncDebugFileLogSink(): FileLogSink? = activeSyncDebugFileLogSink
}
