package com.fsck.k9.activity

import app.k9mail.legacy.message.controller.MessageReference
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import com.fsck.k9.ui.R
import kotlin.test.Test
import net.thunderbird.feature.smartassistant.SmartAssistantFolderReference

class SmartMessageListContextResolverTest {
    @Test
    fun `explicit single-account search resolves the query account`() {
        assertThat(SmartMessageListContextResolver.explicitAccountUuid(setOf("new-account")))
            .isEqualTo("new-account")
    }

    @Test
    fun `multi-account search does not invent one active account`() {
        assertThat(SmartMessageListContextResolver.explicitAccountUuid(setOf("first", "second"))).isNull()
    }

    @Test
    fun `restored list selection is authoritative when reader exists`() {
        val launchMessage = MessageReference("account", 1L, "launch")
        val restoredMessage = MessageReference("account", 2L, "restored")

        val result = SmartMessageListContextResolver.activeMessage(
            hasMessageView = true,
            fragmentActiveMessage = restoredMessage,
            launchMessage = launchMessage,
        )

        assertThat(result).isEqualTo(restoredMessage)
    }

    @Test
    fun `launch message is fallback before list selection is restored`() {
        val launchMessage = MessageReference("account", 1L, "launch")

        val result = SmartMessageListContextResolver.activeMessage(
            hasMessageView = true,
            fragmentActiveMessage = null,
            launchMessage = launchMessage,
        )

        assertThat(result).isEqualTo(launchMessage)
    }

    @Test
    fun `stale list selection is ignored without a reader`() {
        val result = SmartMessageListContextResolver.activeMessage(
            hasMessageView = false,
            fragmentActiveMessage = MessageReference("account", 1L, "stale"),
            launchMessage = null,
        )

        assertThat(result).isNull()
    }

    @Test
    fun `selected search result supplies its own folder context`() {
        val result = SmartMessageListContextResolver.folderReference(
            activeMessage = MessageReference("result-account", 22L, "result"),
            currentAccountUuid = "list-account",
            currentFolderIds = listOf(11L),
        )

        assertThat(result).isEqualTo(SmartAssistantFolderReference("result-account", 22L))
    }

    @Test
    fun `single folder list supplies folder context without a selected message`() {
        val result = SmartMessageListContextResolver.folderReference(
            activeMessage = null,
            currentAccountUuid = "account",
            currentFolderIds = listOf(42L),
        )

        assertThat(result).isEqualTo(SmartAssistantFolderReference("account", 42L))
    }

    @Test
    fun `multi-folder search does not invent one folder context`() {
        val result = SmartMessageListContextResolver.folderReference(
            activeMessage = null,
            currentAccountUuid = "account",
            currentFolderIds = listOf(1L, 2L),
        )

        assertThat(result).isNull()
    }

    @Test
    fun `manual search uses dedicated upper-screen empty state`() {
        assertThat(SmartMessageListContextResolver.emptyMessage(isManualSearch = true))
            .isEqualTo(R.string.dual_screen_smart_empty_search_result)
        assertThat(SmartMessageListContextResolver.emptyMessage(isManualSearch = false))
            .isEqualTo(R.string.dual_screen_smart_empty_message)
    }
}
