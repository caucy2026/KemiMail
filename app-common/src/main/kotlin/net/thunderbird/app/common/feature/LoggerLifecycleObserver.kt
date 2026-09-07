package net.thunderbird.app.common.feature

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.thunderbird.core.logging.file.FileLogSink

class LoggerLifecycleObserver(
    private val fileLogSinkProvider: () -> FileLogSink?,
) : DefaultLifecycleObserver {
    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        fileLogSinkProvider()?.let {
            owner.lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    it.flushAndCloseBuffer()
                }
            }
        }
    }
}
