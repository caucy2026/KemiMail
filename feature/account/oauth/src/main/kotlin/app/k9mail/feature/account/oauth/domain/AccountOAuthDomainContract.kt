package app.k9mail.feature.account.oauth.domain

import android.content.Intent
import app.k9mail.feature.account.common.domain.entity.AuthorizationState
import app.k9mail.feature.account.oauth.domain.entity.AuthorizationIntentResult
import app.k9mail.feature.account.oauth.domain.entity.AuthorizationResult
import app.k9mail.feature.account.oauth.domain.entity.DeviceAuthorizationSession
import app.k9mail.feature.account.oauth.domain.entity.PollDeviceAuthorizationResult
import app.k9mail.feature.account.oauth.domain.entity.StartDeviceAuthorizationResult
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationResponse
import net.thunderbird.core.common.oauth.OAuthConfiguration

interface AccountOAuthDomainContract {

    interface UseCase {
        fun interface GetOAuthRequestIntent {
            fun execute(hostname: String, emailAddress: String): AuthorizationIntentResult
        }

        fun interface FinishOAuthSignIn {
            suspend fun execute(intent: Intent): AuthorizationResult
        }

        fun interface CheckIsGoogleSignIn {
            fun execute(hostname: String): Boolean
        }

        fun interface CheckIsDeviceAuthorizationSupported {
            fun execute(hostname: String): Boolean
        }

        fun interface StartDeviceAuthorization {
            suspend fun execute(hostname: String): StartDeviceAuthorizationResult
        }

        fun interface PollDeviceAuthorization {
            suspend fun execute(session: DeviceAuthorizationSession): PollDeviceAuthorizationResult
        }
    }

    interface AuthorizationRepository {
        fun getAuthorizationRequestIntent(
            configuration: OAuthConfiguration,
            emailAddress: String,
        ): AuthorizationIntentResult

        suspend fun getAuthorizationResponse(intent: Intent): AuthorizationResponse?
        suspend fun getAuthorizationException(intent: Intent): AuthorizationException?

        suspend fun getExchangeToken(response: AuthorizationResponse): AuthorizationResult
    }

    interface DeviceAuthorizationRepository {
        suspend fun start(configuration: OAuthConfiguration): StartDeviceAuthorizationResult

        suspend fun poll(session: DeviceAuthorizationSession): PollDeviceAuthorizationResult
    }

    fun interface AuthorizationStateRepository {
        fun isAuthorized(authorizationState: AuthorizationState): Boolean
    }
}
