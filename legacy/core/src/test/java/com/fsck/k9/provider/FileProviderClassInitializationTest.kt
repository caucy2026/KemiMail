package com.fsck.k9.provider

import assertk.assertThat
import assertk.assertions.isNotNull
import net.thunderbird.core.android.testing.RobolectricTest
import org.junit.Test

class FileProviderClassInitializationTest : RobolectricTest() {

    @Test
    fun `file providers can be initialized before dependency injection is available`() {
        val decryptedFileProvider = DecryptedFileProvider()
        val attachmentTempFileProvider = AttachmentTempFileProvider()

        assertThat(decryptedFileProvider).isNotNull()
        assertThat(attachmentTempFileProvider).isNotNull()
    }
}
