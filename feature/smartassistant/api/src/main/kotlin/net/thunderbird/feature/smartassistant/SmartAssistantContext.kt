package net.thunderbird.feature.smartassistant

enum class SmartAssistantScene {
    MESSAGE_LIST,
    MESSAGE_READING,
    DRAFT_COMPOSING,
    MESSAGE_SELECTION,
}

data class SmartAssistantAccountReference(
    val accountUuid: String,
) {
    override fun toString(): String = "SmartAssistantAccountReference([REDACTED])"
}

data class SmartAssistantFolderReference(
    val accountUuid: String,
    val folderId: Long,
) {
    override fun toString(): String = "SmartAssistantFolderReference([REDACTED])"
}

data class SmartAssistantMessageReference(
    val accountUuid: String,
    val folderId: Long,
    val messageUid: String,
) {
    override fun toString(): String = "SmartAssistantMessageReference([REDACTED])"
}

data class SmartAssistantDraftReference(
    val accountUuid: String,
    val folderId: Long,
    val messageUid: String,
) {
    override fun toString(): String = "SmartAssistantDraftReference([REDACTED])"
}

class SmartAssistantSelectionReference(
    messages: List<SmartAssistantMessageReference>,
) {
    val messages: List<SmartAssistantMessageReference> = messages.toList()

    override fun equals(other: Any?): Boolean {
        return other is SmartAssistantSelectionReference && messages == other.messages
    }

    override fun hashCode(): Int = messages.hashCode()

    override fun toString(): String = "SmartAssistantSelectionReference(messages=$messages)"
}

data class SmartAssistantContext(
    val scene: SmartAssistantScene,
    val account: SmartAssistantAccountReference? = null,
    val folder: SmartAssistantFolderReference? = null,
    val message: SmartAssistantMessageReference? = null,
    val draft: SmartAssistantDraftReference? = null,
    val selection: SmartAssistantSelectionReference? = null,
)
