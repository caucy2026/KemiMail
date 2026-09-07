package net.thunderbird.feature.smartassistant.internal

import kotlinx.coroutines.withTimeoutOrNull
import net.thunderbird.feature.smartassistant.SmartAssistantAccountReference
import net.thunderbird.feature.smartassistant.SmartAssistantAvailability
import net.thunderbird.feature.smartassistant.SmartAssistantConsentRepository
import net.thunderbird.feature.smartassistant.SmartAssistantConsentScope
import net.thunderbird.feature.smartassistant.SmartAssistantContext
import net.thunderbird.feature.smartassistant.SmartAssistantEngine
import net.thunderbird.feature.smartassistant.SmartAssistantError
import net.thunderbird.feature.smartassistant.SmartAssistantExecutionResult
import net.thunderbird.feature.smartassistant.SmartAssistantRequest
import net.thunderbird.feature.smartassistant.SmartAssistantResultOrigin
import net.thunderbird.feature.smartassistant.SmartAssistantText

internal data class SmartAssistantProcessingPolicy(
    val disclosureId: String,
    val timeoutMillis: Long,
    val cacheTtlMillis: Long,
    val maxCacheEntries: Int,
    val maxInputCharacters: Int,
    val maxInstructionCharacters: Int,
    val maxOutputCharacters: Int,
) {
    init {
        require(disclosureId.isNotBlank())
        require(timeoutMillis > 0)
        require(cacheTtlMillis > 0)
        require(maxCacheEntries > 0)
        require(maxInputCharacters > 0)
        require(maxInstructionCharacters > 0)
        require(maxOutputCharacters > 0)
    }
}

internal class SafeSmartAssistantEngine(
    private val provider: SmartAssistantProvider,
    private val consentRepository: SmartAssistantConsentRepository,
    private val policy: SmartAssistantProcessingPolicy,
    nowMillis: () -> Long,
) : SmartAssistantEngine {
    private val cache = SmartAssistantMemoryCache(
        maxEntries = policy.maxCacheEntries,
        ttlMillis = policy.cacheTtlMillis,
        nowMillis = nowMillis,
    )
    private val contextValidator = SmartAssistantContextValidator()
    private val inputMinimizer = SmartAssistantInputMinimizer(policy)
    private val requestHasher = SmartAssistantRequestHasher()

    override val availability: SmartAssistantAvailability
        get() = if (provider.isAvailable) {
            SmartAssistantAvailability.AVAILABLE
        } else {
            SmartAssistantAvailability.UNAVAILABLE
        }

    override suspend fun execute(request: SmartAssistantRequest): SmartAssistantExecutionResult {
        if (!provider.isAvailable) return failure(SmartAssistantError.UNAVAILABLE)

        return when (val preparation = prepareRequest(request)) {
            is RequestPreparation.Failure -> failure(preparation.error)
            is RequestPreparation.Success -> executePreparedRequest(preparation)
        }
    }

    private suspend fun executePreparedRequest(
        preparation: RequestPreparation.Success,
    ): SmartAssistantExecutionResult {
        val consentScope = SmartAssistantConsentScope(
            account = preparation.account,
            capability = preparation.request.capability,
            disclosureId = policy.disclosureId,
        )
        val hasConsent = consentRepository.isGranted(consentScope)
        return if (hasConsent) {
            executeWithCache(preparation)
        } else {
            failure(SmartAssistantError.CONSENT_REQUIRED)
        }
    }

    private suspend fun executeWithCache(
        preparation: RequestPreparation.Success,
    ): SmartAssistantExecutionResult {
        val cacheKey = requestHasher.cacheKey(
            account = preparation.account,
            contextHash = preparation.contextHash,
            request = preparation.request,
        )
        val cachedOutput = cache.get(cacheKey)
        return if (cachedOutput != null) {
            SmartAssistantExecutionResult.Success(cachedOutput, SmartAssistantResultOrigin.CACHE)
        } else {
            executeProvider(preparation.request, cacheKey)
        }
    }

    private suspend fun executeProvider(
        request: MinimizedSmartAssistantRequest,
        cacheKey: SmartAssistantCacheKey,
    ): SmartAssistantExecutionResult {
        val providerResult = withTimeoutOrNull(policy.timeoutMillis) { provider.execute(request) }
        return when (providerResult) {
            null -> failure(SmartAssistantError.TIMEOUT)
            SmartAssistantProviderResult.Failure -> failure(SmartAssistantError.PROVIDER_FAILURE)
            is SmartAssistantProviderResult.Success -> acceptProviderOutput(providerResult.output, cacheKey)
        }
    }

    private fun acceptProviderOutput(
        output: SmartAssistantText,
        cacheKey: SmartAssistantCacheKey,
    ): SmartAssistantExecutionResult {
        val revealedOutput = output.reveal()
        return if (revealedOutput.isBlank() || revealedOutput.length > policy.maxOutputCharacters) {
            failure(SmartAssistantError.OUTPUT_REJECTED)
        } else {
            cache.put(cacheKey, output)
            SmartAssistantExecutionResult.Success(output, SmartAssistantResultOrigin.PROVIDER)
        }
    }

    override fun invalidate(context: SmartAssistantContext) {
        cache.invalidateContext(requestHasher.hashContext(context))
    }

    override fun invalidateAccount(account: SmartAssistantAccountReference) {
        cache.invalidateAccount(requestHasher.hashAccount(account))
    }

    private fun prepareRequest(request: SmartAssistantRequest): RequestPreparation {
        val account = contextValidator.validate(request.context)
        val minimizedInput = inputMinimizer.minimize(request)
        return when {
            account == null -> RequestPreparation.Failure(SmartAssistantError.INVALID_CONTEXT)
            minimizedInput is InputMinimization.WrongScene -> {
                RequestPreparation.Failure(SmartAssistantError.INVALID_CONTEXT)
            }
            minimizedInput is InputMinimization.Rejected -> {
                RequestPreparation.Failure(SmartAssistantError.INPUT_REJECTED)
            }
            minimizedInput is InputMinimization.Accepted -> RequestPreparation.Success(
                account = account,
                contextHash = requestHasher.hashContext(request.context),
                request = MinimizedSmartAssistantRequest(request.capability, minimizedInput.input),
            )
            else -> error("Unknown smart assistant input state")
        }
    }
}

private sealed interface RequestPreparation {
    data class Success(
        val account: SmartAssistantAccountReference,
        val contextHash: String,
        val request: MinimizedSmartAssistantRequest,
    ) : RequestPreparation

    data class Failure(val error: SmartAssistantError) : RequestPreparation
}

private fun failure(error: SmartAssistantError): SmartAssistantExecutionResult {
    return SmartAssistantExecutionResult.Failure(error)
}
