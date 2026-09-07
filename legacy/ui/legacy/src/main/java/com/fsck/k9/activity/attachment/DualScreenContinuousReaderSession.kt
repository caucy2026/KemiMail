package com.fsck.k9.activity.attachment

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.os.ProxyFileDescriptorCallback
import android.os.storage.StorageManager
import androidx.annotation.RequiresApi
import com.fsck.k9.mailstore.AttachmentViewInfo
import java.io.Closeable
import java.io.DataInputStream
import java.io.IOException

internal sealed interface ContinuousReaderSession : Closeable

internal sealed interface ContinuousReaderOpenResult {
    data object Error : ContinuousReaderOpenResult
    data class Success(val session: ContinuousReaderSession) : ContinuousReaderOpenResult
}

internal sealed interface PdfPageRenderResult {
    data object Error : PdfPageRenderResult
    data class Success(val bitmap: Bitmap) : PdfPageRenderResult
}

internal class ImageReaderSession(
    val bitmap: Bitmap,
) : ContinuousReaderSession {
    override fun close() {
        if (!bitmap.isRecycled) bitmap.recycle()
    }
}

internal class PdfReaderSession(
    private val fileDescriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    val pageRenderSizes: List<RenderSize>,
    private val onClosed: () -> Unit,
) : ContinuousReaderSession {
    private val lock = Any()
    private var closed = false

    fun renderPage(index: Int): PdfPageRenderResult = try {
        PdfPageRenderResult.Success(renderPageBitmap(index))
    } catch (_: IllegalArgumentException) {
        PdfPageRenderResult.Error
    } catch (_: IllegalStateException) {
        PdfPageRenderResult.Error
    } catch (_: SecurityException) {
        PdfPageRenderResult.Error
    }

    private fun renderPageBitmap(index: Int): Bitmap = synchronized(lock) {
        check(!closed)
        val renderSize = pageRenderSizes[index]
        val bitmap = Bitmap.createBitmap(renderSize.width, renderSize.height, Bitmap.Config.ARGB_8888)
        var rendered = false
        try {
            renderer.openPage(index).use { page ->
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            }
            rendered = true
            bitmap
        } finally {
            if (!rendered) bitmap.recycle()
        }
    }

    override fun close() = synchronized(lock) {
        if (closed) return@synchronized
        closed = true
        try {
            renderer.close()
        } finally {
            try {
                fileDescriptor.close()
            } finally {
                onClosed()
            }
        }
    }
}

internal class DualScreenContinuousReaderSessionFactory(
    context: Context,
) {
    private val applicationContext = context.applicationContext
    private val contentResolver: ContentResolver = applicationContext.contentResolver

    fun open(attachment: AttachmentViewInfo): ContinuousReaderOpenResult = try {
        ContinuousReaderOpenResult.Success(openSession(attachment))
    } catch (_: IOException) {
        ContinuousReaderOpenResult.Error
    } catch (_: IllegalArgumentException) {
        ContinuousReaderOpenResult.Error
    } catch (_: IllegalStateException) {
        ContinuousReaderOpenResult.Error
    } catch (_: SecurityException) {
        ContinuousReaderOpenResult.Error
    }

    private fun openSession(attachment: AttachmentViewInfo): ContinuousReaderSession {
        return when (DualScreenContinuousReadingPolicy.readingType(attachment)) {
            ContinuousReadingType.IMAGE -> openImage(attachment)
            ContinuousReadingType.PDF -> openPdf(attachment)
            null -> throw IOException("Attachment is outside the continuous reading policy")
        }
    }

    private fun openImage(attachment: AttachmentViewInfo): ImageReaderSession {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        openAttachmentStream(attachment).use { inputStream ->
            BitmapFactory.decodeStream(inputStream, null, bounds)
        }
        val sampleSize = DualScreenContinuousReadingPolicy.imageSampleSize(bounds.outWidth, bounds.outHeight)
            ?: throw IOException("Image dimensions are outside the continuous reading policy")
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = openAttachmentStream(attachment).use { inputStream ->
            BitmapFactory.decodeStream(inputStream, null, decodeOptions)
        } ?: throw IOException("Image could not be decoded")
        return ImageReaderSession(bitmap)
    }

    private fun openPdf(attachment: AttachmentViewInfo): PdfReaderSession {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            throw IOException("Continuous PDF reading requires Android 8.0 or newer")
        }
        return openPdfWithProxyFileDescriptor(attachment)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun openPdfWithProxyFileDescriptor(attachment: AttachmentViewInfo): PdfReaderSession {
        val sourceBytes = readAttachmentBytes(attachment)
        val callbackThread = HandlerThread("KemiPdfReader").apply { start() }
        var fileDescriptor: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        var ownershipTransferred = false
        try {
            val storageManager = checkNotNull(applicationContext.getSystemService(StorageManager::class.java))
            val openedFileDescriptor = storageManager.openProxyFileDescriptor(
                ParcelFileDescriptor.MODE_READ_ONLY,
                ReadOnlyByteArrayProxy(sourceBytes),
                Handler(callbackThread.looper),
            )
            fileDescriptor = openedFileDescriptor
            val openedRenderer = PdfRenderer(openedFileDescriptor)
            renderer = openedRenderer
            val pageRenderSizes = inspectPdfPages(openedRenderer)
            val session = PdfReaderSession(
                fileDescriptor = openedFileDescriptor,
                renderer = openedRenderer,
                pageRenderSizes = pageRenderSizes,
                onClosed = {
                    sourceBytes.fill(0)
                    callbackThread.quitSafely()
                },
            )
            ownershipTransferred = true
            return session
        } finally {
            if (!ownershipTransferred) {
                closeFailedPdfSetup(renderer, fileDescriptor, sourceBytes, callbackThread)
            }
        }
    }

    private fun closeFailedPdfSetup(
        renderer: PdfRenderer?,
        fileDescriptor: ParcelFileDescriptor?,
        sourceBytes: ByteArray,
        callbackThread: HandlerThread,
    ) {
        try {
            renderer?.close()
        } finally {
            try {
                fileDescriptor?.close()
            } finally {
                sourceBytes.fill(0)
                callbackThread.quitSafely()
            }
        }
    }

    private fun inspectPdfPages(renderer: PdfRenderer): List<RenderSize> {
        if (!DualScreenContinuousReadingPolicy.acceptsPdfPageCount(renderer.pageCount)) {
            throw IOException("PDF page count is outside the continuous reading policy")
        }
        return List(renderer.pageCount) { index ->
            renderer.openPage(index).use { page ->
                DualScreenContinuousReadingPolicy.pdfRenderSize(
                    pageWidth = page.width,
                    pageHeight = page.height,
                    targetWidth = PDF_RENDER_TARGET_WIDTH,
                ) ?: throw IOException("PDF page dimensions are outside the continuous reading policy")
            }
        }
    }

    private fun readAttachmentBytes(attachment: AttachmentViewInfo): ByteArray {
        val expectedByteCount = attachment.size.toInt()
        val sourceBytes = ByteArray(expectedByteCount)
        var completed = false
        try {
            DataInputStream(openAttachmentStream(attachment)).use { inputStream ->
                inputStream.readFully(sourceBytes)
                if (inputStream.read() != -1) {
                    throw IOException("Attachment did not match its declared size")
                }
            }
            completed = true
            return sourceBytes
        } finally {
            if (!completed) sourceBytes.fill(0)
        }
    }

    private fun openAttachmentStream(attachment: AttachmentViewInfo) =
        contentResolver.openInputStream(attachment.internalUri)
            ?: throw IOException("Attachment content could not be opened")

    private companion object {
        const val PDF_RENDER_TARGET_WIDTH = 1_440
    }
}

@RequiresApi(Build.VERSION_CODES.O)
internal class ReadOnlyByteArrayProxy(
    private val sourceBytes: ByteArray,
) : ProxyFileDescriptorCallback() {
    override fun onGetSize(): Long = sourceBytes.size.toLong()

    override fun onRead(offset: Long, size: Int, data: ByteArray): Int {
        if (offset < 0 || offset >= sourceBytes.size) return 0
        val readCount = minOf(size, data.size, sourceBytes.size - offset.toInt())
        sourceBytes.copyInto(data, endIndex = offset.toInt() + readCount, startIndex = offset.toInt())
        return readCount
    }

    override fun onRelease() = Unit
}
