package com.fsck.k9.activity.attachment

import android.net.Uri
import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.fsck.k9.mailstore.AttachmentViewInfo
import kotlin.test.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DualScreenAttachmentPreviewPolicyTest {
    private val testSubject = DualScreenAttachmentPreviewPolicy

    @Test
    fun `downloaded content images on the strict whitelist can be previewed`() {
        assertThat(createAttachment(mimeType = "image/jpeg").let(testSubject::canPreview))
            .isTrue()
        assertThat(createAttachment(mimeType = "IMAGE/PNG").let(testSubject::canPreview))
            .isTrue()
        assertThat(createAttachment(mimeType = "image/webp").let(testSubject::canPreview))
            .isTrue()
    }

    @Test
    fun `active content must already be downloaded`() {
        val attachment = createAttachment(contentAvailable = false)

        assertThat(testSubject.canPreview(attachment)).isFalse()
    }

    @Test
    fun `only app content provider uris can be previewed`() {
        val attachment = createAttachment(uri = Uri.parse("file:///private/attachment.png"))

        assertThat(testSubject.canPreview(attachment)).isFalse()
    }

    @Test
    fun `unknown empty and oversized attachments stay outside the preview boundary`() {
        assertThat(createAttachment(size = AttachmentViewInfo.UNKNOWN_SIZE).let(::canPreview)).isFalse()
        assertThat(createAttachment(size = 0).let(::canPreview)).isFalse()
        assertThat(
            createAttachment(size = testSubject.MAX_PREVIEW_BYTES + 1).let(::canPreview),
        ).isFalse()
    }

    @Test
    fun `active content and image-like file names cannot bypass the mime whitelist`() {
        assertThat(createAttachment(mimeType = "image/svg+xml", name = "vector.svg").let(::canPreview)).isFalse()
        assertThat(createAttachment(mimeType = "image/gif", name = "animation.gif").let(::canPreview)).isFalse()
        assertThat(createAttachment(mimeType = "application/pdf", name = "report.pdf").let(::canPreview)).isFalse()
        assertThat(createAttachment(mimeType = "application/octet-stream", name = "photo.png").let(::canPreview))
            .isFalse()
    }

    private fun canPreview(attachment: AttachmentViewInfo): Boolean {
        return testSubject.canPreview(attachment)
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
