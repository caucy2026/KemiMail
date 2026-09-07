package com.fsck.k9.activity.attachment

import android.net.Uri
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import com.fsck.k9.mailstore.AttachmentViewInfo
import kotlin.test.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DualScreenContinuousReadingPolicyTest {
    private val testSubject = DualScreenContinuousReadingPolicy

    @Test
    fun `downloaded images and bounded pdfs can enter continuous reading`() {
        assertThat(createAttachment(mimeType = "image/jpeg").let(testSubject::readingType))
            .isEqualTo(ContinuousReadingType.IMAGE)
        assertThat(createAttachment(mimeType = "image/png").let(testSubject::readingType))
            .isEqualTo(ContinuousReadingType.IMAGE)
        assertThat(createAttachment(mimeType = "application/pdf", size = 4 * 1024 * 1024).let(testSubject::readingType))
            .isEqualTo(ContinuousReadingType.PDF)
    }

    @Test
    fun `unavailable unknown non content and unsupported attachments are rejected`() {
        assertThat(createAttachment(contentAvailable = false).let(testSubject::readingType)).isNull()
        assertThat(createAttachment(size = AttachmentViewInfo.UNKNOWN_SIZE).let(testSubject::readingType)).isNull()
        assertThat(
            createAttachment(uri = Uri.parse("file:///private/report.pdf")).let(testSubject::readingType),
        ).isNull()
        assertThat(createAttachment(mimeType = "image/gif").let(testSubject::readingType)).isNull()
        assertThat(createAttachment(mimeType = "image/svg+xml").let(testSubject::readingType)).isNull()
        assertThat(
            createAttachment(mimeType = "application/octet-stream", name = "report.pdf").let(testSubject::readingType),
        )
            .isNull()
    }

    @Test
    fun `pdf and image byte budgets are independent and strict`() {
        assertThat(
            createAttachment(
                mimeType = "application/pdf",
                size = testSubject.MAX_PDF_BYTES,
            ).let(testSubject::canRead),
        ).isTrue()
        assertThat(
            createAttachment(
                mimeType = "application/pdf",
                size = testSubject.MAX_PDF_BYTES + 1,
            ).let(testSubject::canRead),
        ).isFalse()
        assertThat(
            createAttachment(
                mimeType = "image/webp",
                size = testSubject.MAX_IMAGE_BYTES,
            ).let(testSubject::canRead),
        ).isTrue()
    }

    @Test
    fun `pdf needs the public proxy descriptor API while images remain compatible`() {
        val pdf = createAttachment(mimeType = "application/pdf")
        val image = createAttachment(mimeType = "image/png")

        assertThat(testSubject.canReadOnPlatform(pdf, sdkInt = 25)).isFalse()
        assertThat(testSubject.canReadOnPlatform(pdf, sdkInt = 26)).isTrue()
        assertThat(testSubject.canReadOnPlatform(image, sdkInt = 23)).isTrue()
    }

    @Test
    fun `long image dimensions produce a bounded power of two sample size`() {
        assertThat(testSubject.imageSampleSize(width = 1_080, height = 30_000)).isEqualTo(2)
        assertThat(testSubject.imageSampleSize(width = 1_920, height = 4_000)).isEqualTo(1)
        assertThat(testSubject.imageSampleSize(width = 0, height = 2_000)).isNull()
        assertThat(testSubject.imageSampleSize(width = 1_000, height = 100_001)).isNull()
        assertThat(testSubject.imageSampleSize(width = 10_000, height = 10_000)).isNull()
    }

    @Test
    fun `pdf page count and render dimensions stay inside resource budgets`() {
        assertThat(testSubject.acceptsPdfPageCount(1)).isTrue()
        assertThat(testSubject.acceptsPdfPageCount(testSubject.MAX_PDF_PAGE_COUNT)).isTrue()
        assertThat(testSubject.acceptsPdfPageCount(0)).isFalse()
        assertThat(testSubject.acceptsPdfPageCount(testSubject.MAX_PDF_PAGE_COUNT + 1)).isFalse()

        assertThat(testSubject.pdfRenderSize(pageWidth = 595, pageHeight = 842, targetWidth = 1_440))
            .isEqualTo(RenderSize(width = 1_440, height = 2_038))
        assertThat(testSubject.pdfRenderSize(pageWidth = 100, pageHeight = 10_000, targetWidth = 1_440)).isNull()
        assertThat(testSubject.pdfRenderSize(pageWidth = 0, pageHeight = 842, targetWidth = 1_440)).isNull()
    }

    private fun createAttachment(
        mimeType: String = "image/png",
        name: String = "attachment.png",
        size: Long = 1024,
        uri: Uri = Uri.parse("content://com.example.attachments/1"),
        contentAvailable: Boolean = true,
    ): AttachmentViewInfo {
        return AttachmentViewInfo(
            mimeType,
            name,
            size,
            uri,
            false,
            null,
            contentAvailable,
        )
    }
}
