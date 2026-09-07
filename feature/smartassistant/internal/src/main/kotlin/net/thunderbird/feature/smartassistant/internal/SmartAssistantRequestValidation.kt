package net.thunderbird.feature.smartassistant.internal

import net.thunderbird.feature.smartassistant.SmartAssistantAccountReference
import net.thunderbird.feature.smartassistant.SmartAssistantCapability
import net.thunderbird.feature.smartassistant.SmartAssistantContext
import net.thunderbird.feature.smartassistant.SmartAssistantDraftReference
import net.thunderbird.feature.smartassistant.SmartAssistantFolderReference
import net.thunderbird.feature.smartassistant.SmartAssistantInput
import net.thunderbird.feature.smartassistant.SmartAssistantInputField
import net.thunderbird.feature.smartassistant.SmartAssistantMessageReference
import net.thunderbird.feature.smartassistant.SmartAssistantRequest
import net.thunderbird.feature.smartassistant.SmartAssistantScene
import net.thunderbird.feature.smartassistant.SmartAssistantSelectionReference
import net.thunderbird.feature.smartassistant.SmartAssistantText

internal class SmartAssistantContextValidator {
    fun validate(context: SmartAssistantContext): SmartAssistantAccountReference? {
        val account = context.account?.takeIf { it.accountUuid.isNotBlank() }
        return account?.takeIf {
            referencesMatch(context, it) && sceneMatches(context)
        }
    }

    private fun referencesMatch(
        context: SmartAssistantContext,
        account: SmartAssistantAccountReference,
    ): Boolean {
        return context.folder.matches(account) &&
            context.message.matches(account, context.folder) &&
            context.draft.matches(account, context.folder) &&
            context.selection.matches(account)
    }

    private fun sceneMatches(context: SmartAssistantContext): Boolean = when (context.scene) {
        SmartAssistantScene.MESSAGE_LIST -> {
            context.message == null && context.draft == null && context.selection == null
        }
        SmartAssistantScene.MESSAGE_READING -> {
            context.message != null && context.draft == null && context.selection == null
        }
        SmartAssistantScene.DRAFT_COMPOSING -> {
            context.message == null && context.draft != null && context.selection == null
        }
        SmartAssistantScene.MESSAGE_SELECTION -> {
            context.message == null && context.draft == null && context.selection?.messages?.isNotEmpty() == true
        }
    }
}

internal sealed interface InputMinimization {
    data class Accepted(val input: SmartAssistantInput) : InputMinimization
    data object WrongScene : InputMinimization
    data object Rejected : InputMinimization
}

internal class SmartAssistantInputMinimizer(
    private val policy: SmartAssistantProcessingPolicy,
) {
    fun minimize(request: SmartAssistantRequest): InputMinimization {
        val spec = capabilitySpecs.getValue(request.capability)
        return if (request.context.scene != spec.scene) {
            InputMinimization.WrongScene
        } else {
            minimizeInput(request.input, spec)?.let(InputMinimization::Accepted) ?: InputMinimization.Rejected
        }
    }

    private fun minimizeInput(
        input: SmartAssistantInput,
        spec: CapabilitySpec,
    ): SmartAssistantInput? {
        val fields = input.fields
        val hasValidFieldSet = fields.isNotEmpty() &&
            spec.allowedFields.containsAll(fields) &&
            fields.containsAll(spec.requiredFields)
        if (!hasValidFieldSet) return null

        var totalCharacters = 0
        var valuesAreValid = true
        val minimizedValues = linkedMapOf<SmartAssistantInputField, SmartAssistantText>()
        for (field in fields.sortedBy { it.ordinal }) {
            val text = checkNotNull(input[field])
            val revealed = text.reveal()
            totalCharacters += revealed.length
            valuesAreValid = valuesAreValid && revealed.isNotBlank()
            valuesAreValid = valuesAreValid && totalCharacters <= policy.maxInputCharacters
            valuesAreValid = valuesAreValid && instructionIsWithinBudget(field, revealed)
            minimizedValues[field] = text
        }
        return SmartAssistantInput.from(minimizedValues).takeIf { valuesAreValid }
    }

    private fun instructionIsWithinBudget(field: SmartAssistantInputField, value: String): Boolean {
        return field != SmartAssistantInputField.USER_INSTRUCTION || value.length <= policy.maxInstructionCharacters
    }
}

private data class CapabilitySpec(
    val scene: SmartAssistantScene,
    val allowedFields: Set<SmartAssistantInputField>,
    val requiredFields: Set<SmartAssistantInputField>,
)

private val messageReadingFields = setOf(
    SmartAssistantInputField.SUBJECT,
    SmartAssistantInputField.MESSAGE_BODY,
)
private val replyFields = messageReadingFields + SmartAssistantInputField.USER_INSTRUCTION
private val draftGenerationFields = setOf(
    SmartAssistantInputField.SUBJECT,
    SmartAssistantInputField.MESSAGE_BODY,
    SmartAssistantInputField.USER_INSTRUCTION,
)
private val draftTransformationFields = setOf(
    SmartAssistantInputField.DRAFT_BODY,
    SmartAssistantInputField.USER_INSTRUCTION,
)

private val capabilitySpecs = mapOf(
    SmartAssistantCapability.SUMMARIZE_MESSAGE to messageReadingSpec(),
    SmartAssistantCapability.EXTRACT_KEY_INFORMATION to messageReadingSpec(),
    SmartAssistantCapability.SUGGEST_REPLY to CapabilitySpec(
        scene = SmartAssistantScene.MESSAGE_READING,
        allowedFields = replyFields,
        requiredFields = setOf(SmartAssistantInputField.MESSAGE_BODY),
    ),
    SmartAssistantCapability.GENERATE_TEXT to CapabilitySpec(
        scene = SmartAssistantScene.DRAFT_COMPOSING,
        allowedFields = draftGenerationFields,
        requiredFields = setOf(SmartAssistantInputField.USER_INSTRUCTION),
    ),
    SmartAssistantCapability.EXPAND_TEXT to draftTransformationSpec(),
    SmartAssistantCapability.SHORTEN_TEXT to draftTransformationSpec(),
    SmartAssistantCapability.POLISH_TEXT to draftTransformationSpec(),
    SmartAssistantCapability.ADJUST_TONE to draftTransformationSpec(),
    SmartAssistantCapability.TRANSLATE_TEXT to draftTransformationSpec(),
)

private fun messageReadingSpec() = CapabilitySpec(
    scene = SmartAssistantScene.MESSAGE_READING,
    allowedFields = messageReadingFields,
    requiredFields = setOf(SmartAssistantInputField.MESSAGE_BODY),
)

private fun draftTransformationSpec() = CapabilitySpec(
    scene = SmartAssistantScene.DRAFT_COMPOSING,
    allowedFields = draftTransformationFields,
    requiredFields = setOf(SmartAssistantInputField.DRAFT_BODY),
)

private fun SmartAssistantFolderReference?.matches(account: SmartAssistantAccountReference): Boolean {
    return this == null || accountUuid == account.accountUuid
}

private fun SmartAssistantMessageReference?.matches(
    account: SmartAssistantAccountReference,
    folder: SmartAssistantFolderReference?,
): Boolean {
    return this == null ||
        (
            accountUuid == account.accountUuid &&
                messageUid.isNotBlank() &&
                (folder == null || folderId == folder.folderId)
            )
}

private fun SmartAssistantDraftReference?.matches(
    account: SmartAssistantAccountReference,
    folder: SmartAssistantFolderReference?,
): Boolean {
    return this == null ||
        (
            accountUuid == account.accountUuid &&
                messageUid.isNotBlank() &&
                (folder == null || folderId == folder.folderId)
            )
}

private fun SmartAssistantSelectionReference?.matches(account: SmartAssistantAccountReference): Boolean {
    return this == null ||
        messages.all { message ->
            message.accountUuid == account.accountUuid && message.messageUid.isNotBlank()
        }
}
