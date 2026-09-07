package app.k9mail.feature.account.oauth.ui

import android.app.Activity
import android.content.Intent
import androidx.lifecycle.viewModelScope
import app.k9mail.feature.account.common.domain.entity.AuthorizationState
import app.k9mail.feature.account.oauth.domain.AccountOAuthDomainContract.UseCase
import app.k9mail.feature.account.oauth.domain.entity.AuthorizationIntentResult
import app.k9mail.feature.account.oauth.domain.entity.AuthorizationResult
import app.k9mail.feature.account.oauth.domain.entity.DeviceAuthorizationSession
import app.k9mail.feature.account.oauth.domain.entity.PollDeviceAuthorizationResult
import app.k9mail.feature.account.oauth.domain.entity.StartDeviceAuthorizationResult
import app.k9mail.feature.account.oauth.ui.AccountOAuthContract.Effect
import app.k9mail.feature.account.oauth.ui.AccountOAuthContract.Error
import app.k9mail.feature.account.oauth.ui.AccountOAuthContract.Event
import app.k9mail.feature.account.oauth.ui.AccountOAuthContract.State
import app.k9mail.feature.account.oauth.ui.AccountOAuthContract.ViewModel
import kotlin.math.max
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.thunderbird.core.ui.contract.mvi.BaseViewModel

@Suppress("TooManyFunctions")
class AccountOAuthViewModel(
    initialState: State = State(),
    private val getOAuthRequestIntent: UseCase.GetOAuthRequestIntent,
    private val finishOAuthSignIn: UseCase.FinishOAuthSignIn,
    private val checkIsGoogleSignIn: UseCase.CheckIsGoogleSignIn,
    private val checkIsDeviceAuthorizationSupported: UseCase.CheckIsDeviceAuthorizationSupported,
    private val startDeviceAuthorization: UseCase.StartDeviceAuthorization,
    private val pollDeviceAuthorization: UseCase.PollDeviceAuthorization,
    private val clock: () -> Long = System::currentTimeMillis,
) : BaseViewModel<State, Event, Effect>(initialState), ViewModel {
    private var deviceAuthorizationJob: Job? = null

    override fun initState(state: State) {
        val isGoogleSignIn = checkIsGoogleSignIn.execute(state.hostname)
        val isDeviceAuthorizationSupported = checkIsDeviceAuthorizationSupported.execute(state.hostname)
        deviceAuthorizationJob?.cancel()

        updateState {
            state.copy(
                isGoogleSignIn = isGoogleSignIn,
                isDeviceAuthorizationSupported = isDeviceAuthorizationSupported,
            )
        }
    }

    override fun event(event: Event) {
        when (event) {
            is Event.OnOAuthResult -> onOAuthResult(event.resultCode, event.data)

            Event.SignInClicked -> onSignIn()
            Event.DeviceSignInClicked -> onDeviceSignIn()
            Event.CancelDeviceSignInClicked -> cancelDeviceSignIn()

            Event.OnBackClicked -> navigateBack()

            Event.OnRetryClicked -> onRetry()
        }
    }

    private fun onSignIn() {
        deviceAuthorizationJob?.cancel()
        deviceAuthorizationJob = null
        if (state.value.deviceAuthorization != null || state.value.error?.isDeviceAuthorizationError() == true) {
            updateState {
                it.copy(
                    deviceAuthorization = null,
                    error = null,
                    isLoading = false,
                )
            }
        }
        val result = getOAuthRequestIntent.execute(
            hostname = state.value.hostname,
            emailAddress = state.value.emailAddress,
        )

        when (result) {
            AuthorizationIntentResult.NotSupported -> {
                updateState { state ->
                    state.copy(
                        error = Error.NotSupported,
                    )
                }
            }

            is AuthorizationIntentResult.Success -> {
                emitEffect(Effect.LaunchOAuth(result.intent))
            }
        }
    }

    private fun onRetry() {
        val retryDeviceAuthorization = when (state.value.error) {
            Error.DeviceAuthorizationDeclined,
            Error.DeviceAuthorizationExpired,
            Error.DeviceAuthorizationFailed,
            -> true

            else -> false
        }
        updateState { state ->
            state.copy(
                error = null,
            )
        }
        if (retryDeviceAuthorization) {
            onDeviceSignIn()
        } else {
            onSignIn()
        }
    }

    private fun onDeviceSignIn() {
        deviceAuthorizationJob?.cancel()
        updateState {
            it.copy(
                isLoading = true,
                deviceAuthorization = null,
                error = null,
            )
        }
        deviceAuthorizationJob = viewModelScope.launch {
            when (val result = startDeviceAuthorization.execute(state.value.hostname)) {
                StartDeviceAuthorizationResult.NotSupported -> updateErrorState(Error.NotSupported)
                StartDeviceAuthorizationResult.Failure -> updateErrorState(Error.DeviceAuthorizationFailed)
                is StartDeviceAuthorizationResult.Success -> pollDeviceAuthorization(result.session)
            }
        }
    }

    @Suppress("ReturnCount")
    private suspend fun pollDeviceAuthorization(session: DeviceAuthorizationSession) {
        var pollingIntervalSeconds = session.pollingIntervalSeconds
        updateState {
            it.copy(
                isLoading = false,
                deviceAuthorization = session.toUiState(),
            )
        }

        while (clock() < session.expiresAt) {
            delay(pollingIntervalSeconds * MILLIS_PER_SECOND)
            updateState { currentState ->
                currentState.copy(deviceAuthorization = session.toUiState())
            }
            when (val result = pollDeviceAuthorization.execute(session)) {
                PollDeviceAuthorizationResult.Pending -> Unit
                PollDeviceAuthorizationResult.SlowDown -> pollingIntervalSeconds += SLOW_DOWN_SECONDS
                PollDeviceAuthorizationResult.Declined -> {
                    updateErrorState(Error.DeviceAuthorizationDeclined)
                    return
                }

                PollDeviceAuthorizationResult.Expired -> {
                    updateErrorState(Error.DeviceAuthorizationExpired)
                    return
                }

                PollDeviceAuthorizationResult.Failure -> {
                    updateErrorState(Error.DeviceAuthorizationFailed)
                    return
                }

                is PollDeviceAuthorizationResult.Success -> {
                    updateState { it.copy(deviceAuthorization = null) }
                    navigateNext(result.authorizationState)
                    return
                }
            }
        }
        updateErrorState(Error.DeviceAuthorizationExpired)
    }

    private fun DeviceAuthorizationSession.toUiState(): AccountOAuthContract.DeviceAuthorization {
        return AccountOAuthContract.DeviceAuthorization(
            verificationUri = verificationUri,
            userCode = userCode,
            remainingSeconds = max(0, ((expiresAt - clock()) / MILLIS_PER_SECOND).toInt()),
        )
    }

    private fun cancelDeviceSignIn() {
        deviceAuthorizationJob?.cancel()
        deviceAuthorizationJob = null
        updateState {
            it.copy(
                isLoading = false,
                deviceAuthorization = null,
                error = null,
            )
        }
    }

    private fun onOAuthResult(resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK && data != null) {
            finishSignIn(data)
        } else {
            updateState { state ->
                state.copy(error = Error.Canceled)
            }
        }
    }

    private fun finishSignIn(data: Intent) {
        updateState { state ->
            state.copy(
                isLoading = true,
            )
        }
        viewModelScope.launch {
            when (val result = finishOAuthSignIn.execute(data)) {
                AuthorizationResult.BrowserNotAvailable -> updateErrorState(Error.BrowserNotAvailable)
                AuthorizationResult.Canceled -> updateErrorState(Error.Canceled)
                is AuthorizationResult.Failure -> updateErrorState(Error.Unknown(result.error))
                is AuthorizationResult.Success -> {
                    updateState { state ->
                        state.copy(isLoading = false)
                    }
                    navigateNext(authorizationState = result.state)
                }
            }
        }
    }

    private fun updateErrorState(error: Error) = updateState { state ->
        state.copy(
            error = error,
            isLoading = false,
            deviceAuthorization = null,
        )
    }

    private fun Error.isDeviceAuthorizationError(): Boolean {
        return this == Error.DeviceAuthorizationDeclined ||
            this == Error.DeviceAuthorizationExpired ||
            this == Error.DeviceAuthorizationFailed
    }

    private fun navigateBack() {
        if (state.value.deviceAuthorization != null || deviceAuthorizationJob?.isActive == true) {
            cancelDeviceSignIn()
        } else {
            emitEffect(Effect.NavigateBack)
        }
    }

    private fun navigateNext(authorizationState: AuthorizationState) {
        emitEffect(Effect.NavigateNext(authorizationState))
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000L
        const val SLOW_DOWN_SECONDS = 5L
    }
}
