package app.k9mail.feature.account.setup.ui.credential

import androidx.lifecycle.viewModelScope
import app.k9mail.feature.account.setup.ui.credential.RemoteCredentialTransferContract.Effect
import app.k9mail.feature.account.setup.ui.credential.RemoteCredentialTransferContract.Error
import app.k9mail.feature.account.setup.ui.credential.RemoteCredentialTransferContract.Event
import app.k9mail.feature.account.setup.ui.credential.RemoteCredentialTransferContract.PollResult
import app.k9mail.feature.account.setup.ui.credential.RemoteCredentialTransferContract.StartResult
import app.k9mail.feature.account.setup.ui.credential.RemoteCredentialTransferContract.State
import kotlin.math.max
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.thunderbird.core.ui.contract.mvi.BaseViewModel

internal class RemoteCredentialTransferViewModel(
    private val repository: RemoteCredentialTransferContract.Repository,
    private val clock: () -> Long = System::currentTimeMillis,
) : BaseViewModel<State, Event, Effect>(State()), RemoteCredentialTransferContract.ViewModel {
    private var sessionId: String? = null
    private var expiresAt: Long = 0
    private var pollingJob: Job? = null

    override val isAvailable: Boolean = repository.isAvailable

    override fun event(event: Event) {
        when (event) {
            is Event.Start -> start(event.emailAddress)
            Event.Retry -> start(state.value.emailAddress)
            Event.Cancel -> cancel()
        }
    }

    private fun start(emailAddress: String) {
        pollingJob?.cancel()
        viewModelScope.launch {
            sessionId?.let { repository.cancel(it) }
            updateState {
                State(
                    emailAddress = emailAddress,
                    isLoading = true,
                )
            }
            when (val result = repository.start(emailAddress)) {
                StartResult.Unavailable -> updateError(Error.Unavailable)
                StartResult.Failure -> updateError(Error.Network)
                is StartResult.Success -> beginPolling(result.session)
            }
        }
    }

    private fun beginPolling(session: RemoteCredentialTransferContract.Session) {
        sessionId = session.id
        expiresAt = session.expiresAt
        updateState {
            it.copy(
                qrCodeUrl = session.qrCodeUrl,
                verificationCode = session.verificationCode,
                remainingSeconds = remainingSeconds(),
                isLoading = false,
                error = null,
            )
        }
        pollingJob = viewModelScope.launch {
            while (remainingSeconds() > 0) {
                when (val result = repository.poll(session.id)) {
                    PollResult.Pending -> {
                        updateState { it.copy(remainingSeconds = remainingSeconds()) }
                        delay(POLL_INTERVAL_MS)
                    }

                    PollResult.Expired -> {
                        sessionId = null
                        updateError(Error.Expired)
                        return@launch
                    }

                    PollResult.InvalidPayload -> {
                        sessionId = null
                        updateError(Error.InvalidPayload)
                        return@launch
                    }

                    is PollResult.Received -> {
                        sessionId = null
                        emitEffect(Effect.CredentialReceived(result.credential))
                        return@launch
                    }
                }
            }
            repository.cancel(session.id)
            sessionId = null
            updateError(Error.Expired)
        }
    }

    private fun cancel() {
        pollingJob?.cancel()
        pollingJob = null
        viewModelScope.launch {
            sessionId?.let { repository.cancel(it) }
            sessionId = null
            updateState { State(emailAddress = it.emailAddress) }
            emitEffect(Effect.Canceled)
        }
    }

    private fun updateError(error: Error) {
        updateState {
            it.copy(
                qrCodeUrl = null,
                verificationCode = "",
                remainingSeconds = 0,
                isLoading = false,
                error = error,
            )
        }
    }

    private fun remainingSeconds(): Int {
        return max(0, ((expiresAt - clock()) / MILLIS_PER_SECOND).toInt())
    }

    private companion object {
        const val POLL_INTERVAL_MS = 2_000L
        const val MILLIS_PER_SECOND = 1_000L
    }
}
