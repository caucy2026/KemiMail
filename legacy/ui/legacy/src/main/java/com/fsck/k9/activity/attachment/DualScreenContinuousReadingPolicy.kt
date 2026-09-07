package com.fsck.k9.activity.attachment

import android.os.Build
import com.fsck.k9.mailstore.AttachmentViewInfo
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.sqrt

internal enum class ContinuousReadingType {
    IMAGE,
    PDF,
}

internal data class RenderSize(
    val width: Int,
    val height: Int,
)

internal object DualScreenContinuousReadingPolicy {
    const val MAX_IMAGE_BYTES: Long = 20L * 1024 * 1024
    const val MAX_PDF_BYTES: Long = 12L * 1024 * 1024
    const val MAX_PDF_PAGE_COUNT: Int = 50

    private const val MAX_IMAGE_DIMENSION = 100_000
    private const val MAX_SOURCE_IMAGE_PIXELS = 80_000_000L
    private const val MAX_DECODED_IMAGE_PIXELS = 16_000_000L
    private const val MAX_PDF_PAGE_DIMENSION = 10_000
    private const val MAX_PDF_PAGE_ASPECT_RATIO = 4.0
    private const val MAX_RENDERED_PDF_PAGE_PIXELS = 6_500_000.0
    private const val CONTENT_SCHEME = "content"

    private val supportedImageMimeTypes = setOf(
        "image/jpeg",
        "image/png",
        "image/webp",
    )

    fun canRead(attachment: AttachmentViewInfo): Boolean {
        return canReadOnPlatform(attachment, Build.VERSION.SDK_INT)
    }

    fun canReadOnPlatform(attachment: AttachmentViewInfo, sdkInt: Int): Boolean {
        return when (readingType(attachment)) {
            ContinuousReadingType.IMAGE -> true
            ContinuousReadingType.PDF -> sdkInt >= Build.VERSION_CODES.O
            null -> false
        }
    }

    fun readingType(attachment: AttachmentViewInfo): ContinuousReadingType? {
        val isEligibleContent = attachment.isContentAvailable &&
            attachment.internalUri.scheme == CONTENT_SCHEME &&
            attachment.size > 0
        return if (!isEligibleContent) {
            null
        } else {
            when (attachment.mimeType?.lowercase(Locale.ROOT)) {
                in supportedImageMimeTypes -> {
                    ContinuousReadingType.IMAGE.takeIf { attachment.size <= MAX_IMAGE_BYTES }
                }
                "application/pdf" -> ContinuousReadingType.PDF.takeIf { attachment.size <= MAX_PDF_BYTES }
                else -> null
            }
        }
    }

    fun imageSampleSize(width: Int, height: Int): Int? {
        val hasValidDimensions = width in 1..MAX_IMAGE_DIMENSION && height in 1..MAX_IMAGE_DIMENSION
        val sourcePixels = width.toLong() * height.toLong()
        return if (!hasValidDimensions || sourcePixels > MAX_SOURCE_IMAGE_PIXELS) {
            null
        } else {
            var sampleSize = 1
            while (sampledPixels(width, height, sampleSize) > MAX_DECODED_IMAGE_PIXELS) {
                sampleSize *= 2
            }
            sampleSize
        }
    }

    fun acceptsPdfPageCount(pageCount: Int): Boolean = pageCount in 1..MAX_PDF_PAGE_COUNT

    fun pdfRenderSize(pageWidth: Int, pageHeight: Int, targetWidth: Int): RenderSize? {
        val hasValidDimensions = pageWidth in 1..MAX_PDF_PAGE_DIMENSION &&
            pageHeight in 1..MAX_PDF_PAGE_DIMENSION &&
            targetWidth > 0
        val aspectRatio = pageHeight.toDouble() / pageWidth.toDouble()
        val hasValidAspectRatio = aspectRatio in
            (1.0 / MAX_PDF_PAGE_ASPECT_RATIO)..MAX_PDF_PAGE_ASPECT_RATIO
        return if (!hasValidDimensions || !hasValidAspectRatio) {
            null
        } else {
            val widthScale = targetWidth.toDouble() / pageWidth.toDouble()
            val pixelScale = sqrt(MAX_RENDERED_PDF_PAGE_PIXELS / (pageWidth.toDouble() * pageHeight.toDouble()))
            val scale = minOf(widthScale, pixelScale)
            RenderSize(
                width = (pageWidth * scale).roundToInt().coerceAtLeast(1),
                height = (pageHeight * scale).roundToInt().coerceAtLeast(1),
            )
        }
    }

    private fun sampledPixels(width: Int, height: Int, sampleSize: Int): Long {
        val sampledWidth = (width + sampleSize - 1L) / sampleSize
        val sampledHeight = (height + sampleSize - 1L) / sampleSize
        return sampledWidth * sampledHeight
    }
}
