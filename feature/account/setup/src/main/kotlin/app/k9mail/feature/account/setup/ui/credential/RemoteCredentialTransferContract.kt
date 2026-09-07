package app.k9mail.feature.account.setup.ui.credential

import net.thunderbird.core.ui.contract.mvi.UnidirectionalViewModel

interface RemoteCredentialTransferContract {
    interface ViewModel : UnidirectionalViewModel<State, Event, Effect> {
        val isAvailable: Boolean
    }

    data class State(
        val emailAddress: String = "",
        val qrCodeUrl: String? = null,
        val verificationCode: String = "",
        val remainingSeconds: Int = 0,
        val isLoading: Boolean = false,
        val error: Error? = null,
    )

    sealed interface Event {
        data class Start(val emailAddress: String) : Event
        data object Retry : Event
        data object Cancel : Event
    }

    sealed interface Effect {
        data class CredentialReceived(val credential: String) : Effect
        data object Canceled : Effect
    }

    sealed interface Error {
        data object Unavailable : Error
        data object Expired : Error
        data object InvalidPayload : Error
        data object Network : Error
    }

    interface Repository {
        val isAvailable: Boolean

        suspend fun start(emailAddress: String): StartResult
        suspend fun poll(sessionId: String): PollResult
        suspend fun cancel(sessionId: String)
    }

    data class Session(
        val id: String,
        val qrCodeUrl: String,
        val verificationCode: String,
        val expiresAt: Long,
    )

    sealed interface StartResult {
        data class Success(val session: Session) : StartResult
        data object Unavailable : StartResult
        data object Failure : StartResult
    }

    sealed interface PollResult {
        data object Pending : PollResult
        data object Expired : PollResult
        data object InvalidPayload : PollResult
        data class Received(val credential: String) : PollResult
    }
}
