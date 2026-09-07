package net.thunderbird.core.common.exception

class ExceptionHandler(
    private val defaultHandler: Thread.UncaughtExceptionHandler?,
    private val exceptionRecorder: (Throwable) -> Unit = {},
) : Thread.UncaughtExceptionHandler {
    @Volatile
    private var handlingException = false

    override fun uncaughtException(t: Thread, e: Throwable) {
        if (!handlingException) {
            handlingException = true
            try {
                exceptionRecorder(e)
            } catch (_: Throwable) {
                // The original uncaught exception must always reach the platform handler.
            } finally {
                handlingException = false
            }
        }
        defaultHandler?.uncaughtException(t, e)
    }
}
