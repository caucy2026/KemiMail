package net.thunderbird.feature.smartassistant

enum class SmartAssistantCapability {
    SUMMARIZE_MESSAGE,
    EXTRACT_KEY_INFORMATION,
    SUGGEST_REPLY,
    GENERATE_TEXT,
    EXPAND_TEXT,
    SHORTEN_TEXT,
    POLISH_TEXT,
    ADJUST_TONE,
    TRANSLATE_TEXT,
}

enum class SmartAssistantInputField {
    SUBJECT,
    MESSAGE_BODY,
    DRAFT_BODY,
    USER_INSTRUCTION,
}

class SmartAssistantText private constructor(
    private val value: String,
) {
    fun reveal(): String = value

    override fun equals(other: Any?): Boolean = other is SmartAssistantText && value == other.value

    override fun hashCode(): Int = value.hashCode()

    override fun toString(): String = "SmartAssistantText([REDACTED])"

    companion object {
        fun from(value: String): SmartAssistantText = SmartAssistantText(value)
    }
}

class SmartAssistantInput private constructor(
    values: Map<SmartAssistantInputField, SmartAssistantText>,
) {
    private val values = values.toMap()

    val fields: Set<SmartAssistantInputField>
        get() = values.keys

    operator fun get(field: SmartAssistantInputField): SmartAssistantText? = values[field]

    fun asMap(): Map<SmartAssistantInputField, SmartAssistantText> = values.toMap()

    override fun equals(other: Any?): Boolean = other is SmartAssistantInput && values == other.values

    override fun hashCode(): Int = values.hashCode()

    override fun toString(): String = "SmartAssistantInput(fields=${values.keys})"

    companion object {
        fun from(values: Map<SmartAssistantInputField, SmartAssistantText>): SmartAssistantInput {
            return SmartAssistantInput(values)
        }
    }
}

data class SmartAssistantRequest(
    val capability: SmartAssistantCapability,
    val context: SmartAssistantContext,
    val input: SmartAssistantInput,
)

enum class SmartAssistantAvailability {
    UNAVAILABLE,
    AVAILABLE,
}

enum class SmartAssistantResultOrigin {
    PROVIDER,
    CACHE,
}

enum class SmartAssistantError {
    UNAVAILABLE,
    CONSENT_REQUIRED,
    INVALID_CONTEXT,
    INPUT_REJECTED,
    OUTPUT_REJECTED,
    TIMEOUT,
    PROVIDER_FAILURE,
}

sealed interface SmartAssistantExecutionResult {
    data class Success(
        val output: SmartAssistantText,
        val origin: SmartAssistantResultOrigin,
    ) : SmartAssistantExecutionResult

    data class Failure(
        val error: SmartAssistantError,
    ) : SmartAssistantExecutionResult
}

data class SmartAssistantConsentScope(
    val account: SmartAssistantAccountReference,
    val capability: SmartAssistantCapability,
    val disclosureId: String,
)

interface SmartAssistantConsentRepository {
    suspend fun isGranted(scope: SmartAssistantConsentScope): Boolean
}

interface SmartAssistantEngine {
    val availability: SmartAssistantAvailability

    suspend fun execute(request: SmartAssistantRequest): SmartAssistantExecutionResult

    fun invalidate(context: SmartAssistantContext)

    fun invalidateAccount(account: SmartAssistantAccountReference)
}
