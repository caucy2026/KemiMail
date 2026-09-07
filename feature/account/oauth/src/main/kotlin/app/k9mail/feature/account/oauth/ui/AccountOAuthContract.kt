package app.k9mail.feature.account.oauth.ui

import android.content.Intent
import app.k9mail.feature.account.common.domain.entity.AuthorizationState
import app.k9mail.feature.account.common.ui.WizardNavigationBarState
import net.thunderbird.core.ui.contract.mvi.UnidirectionalViewModel

interface AccountOAuthContract {

    interface ViewModel : UnidirectionalViewModel<State, Event, Effect> {
        fun initState(state: State)
    }

    data class State(
        val hostname: String = "",
        val emailAddress: String = "",
        val wizardNavigationBarState: WizardNavigationBarState = WizardNavigationBarState(
            isNextEnabled = false,
        ),
        val isGoogleSignIn: Boolean = false,
        val isDeviceAuthorizationSupported: Boolean = false,
        val deviceAuthorization: DeviceAuthorization? = null,
        val error: Error? = null,
        val isLoading: Boolean = false,
    )

    data class DeviceAuthorization(
        val verificationUri: String,
        val userCode: String,
        val remainingSeconds: Int,
    )

    sealed interface Event {
        data class OnOAuthResult(
            val resultCode: Int,
            val data: Intent?,
        ) : Event

        data object SignInClicked : Event
        data object DeviceSignInClicked : Event
        data object CancelDeviceSignInClicked : Event
        data object OnBackClicked : Event
        data object OnRetryClicked : Event
    }

    sealed interface Effect {
        data class LaunchOAuth(
            val intent: Intent,
        ) : Effect

        data class NavigateNext(
            val state: AuthorizationState,
        ) : Effect
        object NavigateBack : Effect
    }

    sealed interface Error {
        object NotSupported : Error
        object Canceled : Error

        object BrowserNotAvailable : Error
        data object DeviceAuthorizationDeclined : Error
        data object DeviceAuthorizationExpired : Error
        data object DeviceAuthorizationFailed : Error
        data class Unknown(val error: Exception) : Error
    }
}
