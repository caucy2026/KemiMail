package net.thunderbird.core.android.crashreport

import android.util.AtomicFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

internal interface CrashReportFile {
    fun writeAtomically(payload: ByteArray)

    fun read(): ByteArray?

    fun delete()
}

internal class AndroidAtomicCrashReportFile(
    file: File,
) : CrashReportFile {
    private val atomicFile = AtomicFile(file)

    override fun writeAtomically(payload: ByteArray) {
        require(payload.size <= CrashReportCodec.MAX_PAYLOAD_BYTES)
        atomicFile.baseFile.parentFile?.mkdirs()
        var output: FileOutputStream? = null
        try {
            output = atomicFile.startWrite()
            output.write(payload)
            atomicFile.finishWrite(output)
        } catch (exception: IOException) {
            output?.let(atomicFile::failWrite)
            throw exception
        }
    }

    override fun read(): ByteArray? {
        return when {
            !atomicFile.baseFile.exists() -> null
            atomicFile.baseFile.length() > CrashReportCodec.MAX_PAYLOAD_BYTES -> {
                delete()
                null
            }
            else -> readBoundedPayload()
        }
    }

    private fun readBoundedPayload(): ByteArray? {
        val result = ByteArrayOutputStream()
        var oversized = false
        atomicFile.openRead().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var count = input.read(buffer)
            while (count >= 0 && !oversized) {
                oversized = result.size() + count > CrashReportCodec.MAX_PAYLOAD_BYTES
                if (!oversized) result.write(buffer, 0, count)
                count = input.read(buffer)
            }
        }

        return if (oversized) {
            delete()
            null
        } else {
            result.toByteArray()
        }
    }

    override fun delete() {
        atomicFile.delete()
    }
}
