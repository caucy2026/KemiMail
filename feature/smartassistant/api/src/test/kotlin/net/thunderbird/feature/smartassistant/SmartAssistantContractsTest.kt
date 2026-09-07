package net.thunderbird.feature.smartassistant

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import kotlin.test.Test

class SmartAssistantContractsTest {
    @Test
    fun `sensitive request and context strings redact all local identifiers and content`() {
        val request = SmartAssistantRequest(
            capability = SmartAssistantCapability.SUMMARIZE_MESSAGE,
            context = messageContext(accountUuid = "private-account", messageUid = "private-message"),
            input = SmartAssistantInput.from(
                mapOf(
                    SmartAssistantInputField.SUBJECT to SmartAssistantText.from("private-subject"),
                    SmartAssistantInputField.MESSAGE_BODY to SmartAssistantText.from("private-body"),
                ),
            ),
        )

        val rendered = request.toString()

        assertThat(rendered).doesNotContain("private-account")
        assertThat(rendered).doesNotContain("private-message")
        assertThat(rendered).doesNotContain("private-subject")
        assertThat(rendered).doesNotContain("private-body")
    }

    @Test
    fun `input copies the source map and exposes field names without values`() {
        val source = linkedMapOf(
            SmartAssistantInputField.MESSAGE_BODY to SmartAssistantText.from("original"),
        )
        val testSubject = SmartAssistantInput.from(source)

        source[SmartAssistantInputField.USER_INSTRUCTION] = SmartAssistantText.from("later")

        assertThat(testSubject.fields.toList()).containsExactly(SmartAssistantInputField.MESSAGE_BODY)
        assertThat(testSubject[SmartAssistantInputField.MESSAGE_BODY]?.reveal()).isEqualTo("original")
        assertThat(testSubject.toString()).doesNotContain("original")
        assertThat(testSubject.toString()).doesNotContain("later")
    }

    @Test
    fun `success output remains redacted when rendered`() {
        val testSubject = SmartAssistantExecutionResult.Success(
            output = SmartAssistantText.from("private-output"),
            origin = SmartAssistantResultOrigin.PROVIDER,
        )

        assertThat(testSubject.toString()).doesNotContain("private-output")
        assertThat(testSubject.output.reveal()).isEqualTo("private-output")
    }
}

private fun messageContext(accountUuid: String, messageUid: String): SmartAssistantContext {
    return SmartAssistantContext(
        scene = SmartAssistantScene.MESSAGE_READING,
        account = SmartAssistantAccountReference(accountUuid),
        folder = SmartAssistantFolderReference(accountUuid, folderId = 42L),
        message = SmartAssistantMessageReference(accountUuid, folderId = 42L, messageUid),
    )
}
