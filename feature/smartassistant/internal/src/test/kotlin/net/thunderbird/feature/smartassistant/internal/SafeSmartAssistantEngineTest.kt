package net.thunderbird.feature.smartassistant.internal

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlin.coroutines.cancellation.CancellationException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import net.thunderbird.feature.smartassistant.SmartAssistantAccountReference
import net.thunderbird.feature.smartassistant.SmartAssistantAvailability
import net.thunderbird.feature.smartassistant.SmartAssistantCapability
import net.thunderbird.feature.smartassistant.SmartAssistantConsentRepository
import net.thunderbird.feature.smartassistant.SmartAssistantConsentScope
import net.thunderbird.feature.smartassistant.SmartAssistantContext
import net.thunderbird.feature.smartassistant.SmartAssistantError
import net.thunderbird.feature.smartassistant.SmartAssistantExecutionResult
import net.thunderbird.feature.smartassistant.SmartAssistantFolderReference
import net.thunderbird.feature.smartassistant.SmartAssistantInput
import net.thunderbird.feature.smartassistant.SmartAssistantInputField
import net.thunderbird.feature.smartassistant.SmartAssistantMessageReference
import net.thunderbird.feature.smartassistant.SmartAssistantRequest
import net.thunderbird.feature.smartassistant.SmartAssistantResultOrigin
import net.thunderbird.feature.smartassistant.SmartAssistantScene
import net.thunderbird.feature.smartassistant.SmartAssistantText

class SafeSmartAssistantEngineTest {
    @Test
    fun `unavailable provider fails closed before consent or content processing`() = runTest {
        val consentRepository = FakeConsentRepository()
        val provider = FakeSmartAssistantProvider(isAvailable = false)
        val testSubject = createEngine(provider, consentRepository)

        val result = testSubject.execute(messageRequest(accountUuid = "account", body = "private-body"))

        assertFailure(result, SmartAssistantError.UNAVAILABLE)
        assertThat(consentRepository.requestedScopes).isEmpty()
        assertThat(provider.requests).isEmpty()
    }

    @Test
    fun `missing account consent rejects request without invoking provider`() = runTest {
        val consentRepository = FakeConsentRepository()
        val provider = FakeSmartAssistantProvider()
        val testSubject = createEngine(provider, consentRepository)

        val result = testSubject.execute(messageRequest(accountUuid = "account", body = "private-body"))

        assertFailure(result, SmartAssistantError.CONSENT_REQUIRED)
        assertThat(consentRepository.requestedScopes).containsExactly(
            SmartAssistantConsentScope(
                account = SmartAssistantAccountReference("account"),
                capability = SmartAssistantCapability.SUMMARIZE_MESSAGE,
                disclosureId = DISCLOSURE_ID,
            ),
        )
        assertThat(provider.requests).isEmpty()
    }

    @Test
    fun `valid request sends only minimized fields to provider`() = runTest {
        val consentRepository = FakeConsentRepository(grantedAccounts = setOf("account"))
        val provider = FakeSmartAssistantProvider(
            result = SmartAssistantProviderResult.Success(SmartAssistantText.from("summary")),
        )
        val testSubject = createEngine(provider, consentRepository)

        val result = testSubject.execute(
            messageRequest(accountUuid = "account", body = "private-body", subject = "private-subject"),
        )

        assertThat(result).isEqualTo(
            SmartAssistantExecutionResult.Success(
                output = SmartAssistantText.from("summary"),
                origin = SmartAssistantResultOrigin.PROVIDER,
            ),
        )
        assertThat(provider.requests.single().capability).isEqualTo(SmartAssistantCapability.SUMMARIZE_MESSAGE)
        assertThat(provider.requests.single().input.fields.toList()).containsExactly(
            SmartAssistantInputField.SUBJECT,
            SmartAssistantInputField.MESSAGE_BODY,
        )
        assertThat(provider.requests.single().input[SmartAssistantInputField.MESSAGE_BODY]?.reveal())
            .isEqualTo("private-body")
    }

    @Test
    fun `mismatched account context fails before consent and provider`() = runTest {
        val consentRepository = FakeConsentRepository(grantedAccounts = setOf("account"))
        val provider = FakeSmartAssistantProvider()
        val testSubject = createEngine(provider, consentRepository)
        val request = messageRequest(accountUuid = "account", body = "body").copy(
            context = messageContext(accountUuid = "account", messageAccountUuid = "other-account"),
        )

        val result = testSubject.execute(request)

        assertFailure(result, SmartAssistantError.INVALID_CONTEXT)
        assertThat(consentRepository.requestedScopes).isEmpty()
        assertThat(provider.requests).isEmpty()
    }

    @Test
    fun `capability field whitelist and character budgets reject excess data`() = runTest {
        val consentRepository = FakeConsentRepository(grantedAccounts = setOf("account"))
        val provider = FakeSmartAssistantProvider()
        val testSubject = createEngine(
            provider = provider,
            consentRepository = consentRepository,
            policy = policy(maxInputCharacters = 12),
        )
        val disallowedFieldRequest = messageRequest(accountUuid = "account", body = "body").copy(
            input = SmartAssistantInput.from(
                mapOf(
                    SmartAssistantInputField.MESSAGE_BODY to SmartAssistantText.from("body"),
                    SmartAssistantInputField.DRAFT_BODY to SmartAssistantText.from("draft"),
                ),
            ),
        )

        val disallowedResult = testSubject.execute(disallowedFieldRequest)
        val oversizedResult = testSubject.execute(messageRequest(accountUuid = "account", body = "1234567890123"))

        assertFailure(disallowedResult, SmartAssistantError.INPUT_REJECTED)
        assertFailure(oversizedResult, SmartAssistantError.INPUT_REJECTED)
        assertThat(provider.requests).isEmpty()
    }

    @Test
    fun `cache is account isolated and invalidated by context`() = runTest {
        val consentRepository = FakeConsentRepository(grantedAccounts = setOf("account-a", "account-b"))
        val provider = FakeSmartAssistantProvider(
            result = SmartAssistantProviderResult.Success(SmartAssistantText.from("summary")),
        )
        val testSubject = createEngine(provider, consentRepository)
        val accountARequest = messageRequest(accountUuid = "account-a", body = "same-body")
        val accountBRequest = messageRequest(accountUuid = "account-b", body = "same-body")

        val first = testSubject.execute(accountARequest)
        val cached = testSubject.execute(accountARequest)
        val isolated = testSubject.execute(accountBRequest)
        testSubject.invalidate(accountARequest.context)
        val afterInvalidation = testSubject.execute(accountARequest)

        assertThat((first as SmartAssistantExecutionResult.Success).origin)
            .isEqualTo(SmartAssistantResultOrigin.PROVIDER)
        assertThat((cached as SmartAssistantExecutionResult.Success).origin)
            .isEqualTo(SmartAssistantResultOrigin.CACHE)
        assertThat((isolated as SmartAssistantExecutionResult.Success).origin)
            .isEqualTo(SmartAssistantResultOrigin.PROVIDER)
        assertThat((afterInvalidation as SmartAssistantExecutionResult.Success).origin)
            .isEqualTo(SmartAssistantResultOrigin.PROVIDER)
        assertThat(provider.requests.size).isEqualTo(3)
    }

    @Test
    fun `expired cache is never reused`() = runTest {
        var nowMillis = 1_000L
        val consentRepository = FakeConsentRepository(grantedAccounts = setOf("account"))
        val provider = FakeSmartAssistantProvider(
            result = SmartAssistantProviderResult.Success(SmartAssistantText.from("summary")),
        )
        val testSubject = createEngine(
            provider = provider,
            consentRepository = consentRepository,
            nowMillis = { nowMillis },
            policy = policy(cacheTtlMillis = 100L),
        )
        val request = messageRequest(accountUuid = "account", body = "body")

        testSubject.execute(request)
        nowMillis += 101L
        val result = testSubject.execute(request)

        assertThat((result as SmartAssistantExecutionResult.Success).origin)
            .isEqualTo(SmartAssistantResultOrigin.PROVIDER)
        assertThat(provider.requests.size).isEqualTo(2)
    }

    @Test
    fun `provider timeout becomes safe error`() = runTest {
        val consentRepository = FakeConsentRepository(grantedAccounts = setOf("account"))
        val provider = FakeSmartAssistantProvider(delayMillis = 200L)
        val testSubject = createEngine(
            provider = provider,
            consentRepository = consentRepository,
            policy = policy(timeoutMillis = 50L),
        )

        val result = testSubject.execute(messageRequest(accountUuid = "account", body = "body"))

        assertFailure(result, SmartAssistantError.TIMEOUT)
    }

    @Test
    fun `provider failure exposes only classified error`() = runTest {
        val consentRepository = FakeConsentRepository(grantedAccounts = setOf("account"))
        val provider = FakeSmartAssistantProvider(result = SmartAssistantProviderResult.Failure)
        val testSubject = createEngine(provider, consentRepository)

        val result = testSubject.execute(messageRequest(accountUuid = "account", body = "private-body"))

        assertFailure(result, SmartAssistantError.PROVIDER_FAILURE)
        assertThat(result.toString().contains("private-body")).isFalse()
    }

    @Test
    fun `caller cancellation propagates to provider`() = runTest {
        val consentRepository = FakeConsentRepository(grantedAccounts = setOf("account"))
        val provider = FakeSmartAssistantProvider(shouldAwaitCancellation = true)
        val testSubject = createEngine(provider, consentRepository)

        assertFailsWith<CancellationException> {
            kotlinx.coroutines.withTimeout(50L) {
                testSubject.execute(messageRequest(accountUuid = "account", body = "body"))
            }
        }

        assertThat(provider.wasCancelled).isTrue()
    }

    @Test
    fun `production factories stay unavailable and side effect free`() = runTest {
        val engine = createNoOpSmartAssistantEngine()
        val panelHost = createNoOpSmartAssistantPanelHost()

        val result = engine.execute(messageRequest(accountUuid = "account", body = "private-body"))

        assertThat(engine.availability).isEqualTo(SmartAssistantAvailability.UNAVAILABLE)
        assertFailure(result, SmartAssistantError.UNAVAILABLE)
        assertThat(panelHost.isAvailable).isFalse()
    }

    private fun createEngine(
        provider: FakeSmartAssistantProvider,
        consentRepository: FakeConsentRepository,
        policy: SmartAssistantProcessingPolicy = policy(),
        nowMillis: () -> Long = { 1_000L },
    ): SafeSmartAssistantEngine {
        return SafeSmartAssistantEngine(
            provider = provider,
            consentRepository = consentRepository,
            policy = policy,
            nowMillis = nowMillis,
        )
    }
}

private class FakeConsentRepository(
    private val grantedAccounts: Set<String> = emptySet(),
) : SmartAssistantConsentRepository {
    val requestedScopes = mutableListOf<SmartAssistantConsentScope>()

    override suspend fun isGranted(scope: SmartAssistantConsentScope): Boolean {
        requestedScopes += scope
        return scope.account.accountUuid in grantedAccounts && scope.disclosureId == DISCLOSURE_ID
    }
}

private class FakeSmartAssistantProvider(
    override val isAvailable: Boolean = true,
    var result: SmartAssistantProviderResult = SmartAssistantProviderResult.Success(SmartAssistantText.from("output")),
    private val delayMillis: Long = 0L,
    private val shouldAwaitCancellation: Boolean = false,
) : SmartAssistantProvider {
    val requests = mutableListOf<MinimizedSmartAssistantRequest>()
    var wasCancelled = false

    override suspend fun execute(request: MinimizedSmartAssistantRequest): SmartAssistantProviderResult {
        requests += request
        return try {
            if (shouldAwaitCancellation) awaitCancellation()
            if (delayMillis > 0) delay(delayMillis)
            result
        } finally {
            wasCancelled = shouldAwaitCancellation
        }
    }
}

private fun messageRequest(
    accountUuid: String,
    body: String,
    subject: String? = null,
): SmartAssistantRequest {
    val fields = linkedMapOf<SmartAssistantInputField, SmartAssistantText>()
    if (subject != null) fields[SmartAssistantInputField.SUBJECT] = SmartAssistantText.from(subject)
    fields[SmartAssistantInputField.MESSAGE_BODY] = SmartAssistantText.from(body)
    return SmartAssistantRequest(
        capability = SmartAssistantCapability.SUMMARIZE_MESSAGE,
        context = messageContext(accountUuid = accountUuid, messageAccountUuid = accountUuid),
        input = SmartAssistantInput.from(fields),
    )
}

private fun messageContext(
    accountUuid: String,
    messageAccountUuid: String,
): SmartAssistantContext {
    return SmartAssistantContext(
        scene = SmartAssistantScene.MESSAGE_READING,
        account = SmartAssistantAccountReference(accountUuid),
        folder = SmartAssistantFolderReference(accountUuid, folderId = 42L),
        message = SmartAssistantMessageReference(messageAccountUuid, folderId = 42L, messageUid = "message"),
    )
}

private fun policy(
    timeoutMillis: Long = 1_000L,
    cacheTtlMillis: Long = 60_000L,
    maxInputCharacters: Int = 100_000,
): SmartAssistantProcessingPolicy {
    return SmartAssistantProcessingPolicy(
        disclosureId = DISCLOSURE_ID,
        timeoutMillis = timeoutMillis,
        cacheTtlMillis = cacheTtlMillis,
        maxCacheEntries = 8,
        maxInputCharacters = maxInputCharacters,
        maxInstructionCharacters = 4_000,
        maxOutputCharacters = 100_000,
    )
}

private fun assertFailure(result: SmartAssistantExecutionResult, error: SmartAssistantError) {
    assertThat(result).isEqualTo(SmartAssistantExecutionResult.Failure(error))
}

private const val DISCLOSURE_ID = "unittest-policy-v1"
