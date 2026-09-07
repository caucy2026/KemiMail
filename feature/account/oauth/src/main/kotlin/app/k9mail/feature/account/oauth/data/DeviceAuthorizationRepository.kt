package app.k9mail.feature.account.oauth.data

import androidx.core.net.toUri
import app.k9mail.feature.account.oauth.domain.AccountOAuthDomainContract
import app.k9mail.feature.account.oauth.domain.entity.DeviceAuthorizationSession
import app.k9mail.feature.account.oauth.domain.entity.PollDeviceAuthorizationResult
import app.k9mail.feature.account.oauth.domain.entity.StartDeviceAuthorizationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.TokenRequest
import net.openid.appauth.TokenResponse
import net.thunderbird.core.common.oauth.OAuthConfiguration
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

internal class DeviceAuthorizationRepository(
    private val httpClient: OkHttpClient,
    private val clock: () -> Long = System::currentTimeMillis,
) : AccountOAuthDomainContract.DeviceAuthorizationRepository {
    override suspend fun start(configuration: OAuthConfiguration): StartDeviceAuthorizationResult =
        withContext(Dispatchers.IO) {
            val endpoint = configuration.deviceAuthorizationEndpoint
                ?: return@withContext StartDeviceAuthorizationResult.NotSupported
            runCatching {
                val request = Request.Builder()
                    .url(endpoint)
                    .post(
                        FormBody.Builder()
                            .add("client_id", configuration.clientId)
                            .add("scope", configuration.scopes.joinToString(" "))
                            .build(),
                    )
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@use StartDeviceAuthorizationResult.Failure
                    }

                    val json = JSONObject(response.body.string())
                    val expiresInSeconds = json.getLong("expires_in")
                    StartDeviceAuthorizationResult.Success(
                        DeviceAuthorizationSession(
                            configuration = configuration,
                            deviceCode = json.getString("device_code"),
                            userCode = json.getString("user_code"),
                            verificationUri = json.getString("verification_uri"),
                            expiresAt = clock() + expiresInSeconds * MILLIS_PER_SECOND,
                            pollingIntervalSeconds = json.optLong("interval", DEFAULT_POLLING_INTERVAL_SECONDS)
                                .coerceAtLeast(MINIMUM_POLLING_INTERVAL_SECONDS),
                        ),
                    )
                }
            }.getOrElse { StartDeviceAuthorizationResult.Failure }
        }

    override suspend fun poll(session: DeviceAuthorizationSession): PollDeviceAuthorizationResult =
        withContext(Dispatchers.IO) {
            if (clock() >= session.expiresAt) {
                return@withContext PollDeviceAuthorizationResult.Expired
            }

            runCatching {
                val request = Request.Builder()
                    .url(session.configuration.tokenEndpoint)
                    .post(
                        FormBody.Builder()
                            .add("grant_type", DEVICE_CODE_GRANT_TYPE)
                            .add("client_id", session.configuration.clientId)
                            .add("device_code", session.deviceCode)
                            .build(),
                    )
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val responseJson = response.body.string()
                    if (response.isSuccessful) {
                        parseTokenResponse(session, responseJson)
                    } else {
                        parseErrorResponse(responseJson)
                    }
                }
            }.getOrElse { PollDeviceAuthorizationResult.Failure }
        }

    private fun parseTokenResponse(
        session: DeviceAuthorizationSession,
        responseJson: String,
    ): PollDeviceAuthorizationResult {
        val serviceConfiguration = AuthorizationServiceConfiguration(
            session.configuration.authorizationEndpoint.toUri(),
            session.configuration.tokenEndpoint.toUri(),
        )
        val tokenRequest = TokenRequest.Builder(serviceConfiguration, session.configuration.clientId)
            .setGrantType(DEVICE_CODE_GRANT_TYPE)
            .setScopes(session.configuration.scopes)
            .setAdditionalParameters(mapOf("device_code" to session.deviceCode))
            .build()
        val tokenResponse = TokenResponse.Builder(tokenRequest)
            .fromResponseJsonString(responseJson)
            .build()
        val authState = AuthState(serviceConfiguration).apply {
            update(tokenResponse, null)
        }
        return PollDeviceAuthorizationResult.Success(authState.toAuthorizationState())
    }

    private fun parseErrorResponse(responseJson: String): PollDeviceAuthorizationResult {
        return when (JSONObject(responseJson).optString("error")) {
            "authorization_pending" -> PollDeviceAuthorizationResult.Pending
            "slow_down" -> PollDeviceAuthorizationResult.SlowDown
            "authorization_declined", "access_denied" -> PollDeviceAuthorizationResult.Declined
            "expired_token" -> PollDeviceAuthorizationResult.Expired
            else -> PollDeviceAuthorizationResult.Failure
        }
    }

    private companion object {
        const val DEVICE_CODE_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:device_code"
        const val DEFAULT_POLLING_INTERVAL_SECONDS = 5L
        const val MINIMUM_POLLING_INTERVAL_SECONDS = 1L
        const val MILLIS_PER_SECOND = 1_000L
    }
}
