package com.fsck.k9.activity.smartassistant

import app.k9mail.legacy.message.controller.MessageReference
import net.thunderbird.feature.smartassistant.SmartAssistantDraftReference
import net.thunderbird.feature.smartassistant.SmartAssistantMessageReference

internal fun MessageReference.toSmartAssistantMessageReference(): SmartAssistantMessageReference {
    return SmartAssistantMessageReference(
        accountUuid = accountUuid,
        folderId = folderId,
        messageUid = uid,
    )
}

internal fun MessageReference.toSmartAssistantDraftReference(): SmartAssistantDraftReference {
    return SmartAssistantDraftReference(
        accountUuid = accountUuid,
        folderId = folderId,
        messageUid = uid,
    )
}
