package com.fsck.k9.activity.attachment

import com.fsck.k9.mailstore.AttachmentViewInfo

internal object DualScreenAttachmentPreviewPolicy {
    const val MAX_PREVIEW_BYTES: Long = 20L * 1024 * 1024

    private val supportedMimeTypes = setOf(
        "image/jpeg",
        "image/png",
        "image/webp",
    )

    fun canPreview(attachment: AttachmentViewInfo): Boolean {
        return attachment.isContentAvailable &&
            attachment.internalUri.scheme == CONTENT_SCHEME &&
            attachment.size in 1..MAX_PREVIEW_BYTES &&
            attachment.mimeType?.lowercase() in supportedMimeTypes
    }

    private const val CONTENT_SCHEME = "content"
}
