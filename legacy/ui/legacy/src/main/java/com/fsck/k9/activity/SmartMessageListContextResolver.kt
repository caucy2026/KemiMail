package com.fsck.k9.activity

import androidx.annotation.StringRes
import app.k9mail.legacy.message.controller.MessageReference
import com.fsck.k9.ui.R
import net.thunderbird.feature.smartassistant.SmartAssistantFolderReference

/**
 * Resolves display-only context from the authoritative list and reader fragments.
 *
 * This intentionally doesn't retain queries, pages, selections, or message content. Those continue to be owned by
 * the existing fragments and their ViewModels.
 */
internal object SmartMessageListContextResolver {
    fun explicitAccountUuid(accountUuids: Set<String>): String? = accountUuids.singleOrNull()

    @StringRes
    fun emptyMessage(isManualSearch: Boolean): Int {
        return if (isManualSearch) {
            R.string.dual_screen_smart_empty_search_result
        } else {
            R.string.dual_screen_smart_empty_message
        }
    }

    fun activeMessage(
        hasMessageView: Boolean,
        fragmentActiveMessage: MessageReference?,
        launchMessage: MessageReference?,
    ): MessageReference? {
        if (!hasMessageView) return null

        return fragmentActiveMessage ?: launchMessage
    }

    fun folderReference(
        activeMessage: MessageReference?,
        currentAccountUuid: String?,
        currentFolderIds: List<Long>,
    ): SmartAssistantFolderReference? = when {
        activeMessage != null -> {
            SmartAssistantFolderReference(activeMessage.accountUuid, activeMessage.folderId)
        }

        currentAccountUuid != null && currentFolderIds.size == 1 -> {
            SmartAssistantFolderReference(currentAccountUuid, currentFolderIds.single())
        }

        else -> null
    }
}
