package com.fsck.k9.activity

import assertk.assertThat
import assertk.assertions.hasLength
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlin.test.Test

class DualScreenComposeReferenceContentTest {
    @Test
    fun `reference keeps ordinary subject and body without creating editable state`() {
        val content = DualScreenComposeReferenceContent.fromText(
            subject = "Quarterly review",
            body = "Please review the attached figures.",
        )

        assertThat(content.subject).isEqualTo("Quarterly review")
        assertThat(content.body).isEqualTo("Please review the attached figures.")
        assertThat(content.truncated).isFalse()
    }

    @Test
    fun `reference bounds an unusually large source message`() {
        val content = DualScreenComposeReferenceContent.fromText(
            subject = null,
            body = "x".repeat(100_001),
        )

        assertThat(content.subject).isEqualTo("")
        assertThat(content.body).hasLength(100_000)
        assertThat(content.truncated).isTrue()
    }
}
