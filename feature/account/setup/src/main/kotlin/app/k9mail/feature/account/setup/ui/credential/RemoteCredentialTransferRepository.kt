package app.k9mail.feature.account.setup.ui.credential

import java.nio.charset.StandardCharsets
import java.security.KeyPair
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.thunderbird.core.common.credentials.RemoteCredentialTransferConfigurationProvider
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

internal class RemoteCredentialTransferRepository(
    private val httpClient: OkHttpClient,
    configurationProvider: RemoteCredentialTransferConfigurationProvider,
) : RemoteCredentialTransferContract.Repository {
    private val baseUrl = configurationProvider.getConfiguration().baseUrl?.trimEnd('/')
    private val localSessions = ConcurrentHashMap<String, LocalSession>()

    override val isAvailable: Boolean = baseUrl?.startsWith("https://") == true

    override suspend fun start(emailAddress: String): RemoteCredentialTransferContract.StartResult {
        return if (!isAvailable || baseUrl == null) {
            RemoteCredentialTransferContract.StartResult.Unavailable
        } else {
            startAvailable(baseUrl, emailAddress)
        }
    }

    private suspend fun startAvailable(
        relayBaseUrl: String,
        emailAddress: String,
    ): RemoteCredentialTransferContract.StartResult = withContext(Dispatchers.IO) {
        runCatching {
            val sessionId = CredentialTransferCrypto.randomBase64Url(SESSION_ID_BYTES)
            val writeToken = CredentialTransferCrypto.randomBase64Url(TOKEN_BYTES)
            val readToken = CredentialTransferCrypto.randomBase64Url(TOKEN_BYTES)
            val receiverKeys = CredentialTransferCrypto.generateKeyPair()
            val requestBody = JSONObject()
                .put("version", PROTOCOL_VERSION)
                .put("sessionId", sessionId)
                .put("writeTokenHash", CredentialTransferCrypto.sha256Base64Url(writeToken))
                .put("readTokenHash", CredentialTransferCrypto.sha256Base64Url(readToken))
                .toString()
                .toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder()
                .url("$relayBaseUrl$ROUTE_PREFIX/v1/sessions")
                .post(requestBody)
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.code != HTTP_CREATED) error("session_create_failed")
                val responseBody = JSONObject(response.body.string())
                val expiresAt = responseBody.getLong("expiresAt")
                val verificationCode = CredentialTransferCrypto.verificationCode(
                    sessionId,
                    receiverKeys.public.encoded,
                    writeToken,
                )
                localSessions[sessionId] = LocalSession(
                    receiverKeys = receiverKeys,
                    readToken = readToken,
                    baseUrl = relayBaseUrl,
                )
                val qrCodeUrl = buildQrCodeUrl(
                    relayBaseUrl = relayBaseUrl,
                    sessionId = sessionId,
                    writeToken = writeToken,
                    publicKey = CredentialTransferCrypto.encodeBase64Url(receiverKeys.public.encoded),
                    verificationCode = verificationCode,
                    emailAddress = emailAddress,
                )
                RemoteCredentialTransferContract.StartResult.Success(
                    RemoteCredentialTransferContract.Session(
                        id = sessionId,
                        qrCodeUrl = qrCodeUrl,
                        verificationCode = verificationCode,
                        expiresAt = expiresAt,
                    ),
                )
            }
        }.getOrElse {
            RemoteCredentialTransferContract.StartResult.Failure
        }
    }

    override suspend fun poll(sessionId: String): RemoteCredentialTransferContract.PollResult {
        val localSession = localSessions[sessionId] ?: return RemoteCredentialTransferContract.PollResult.Expired
        return withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url("${localSession.baseUrl}$ROUTE_PREFIX/v1/sessions/$sessionId/payload")
                    .header(READ_TOKEN_HEADER, localSession.readToken)
                    .get()
                    .build()
                httpClient.newCall(request).execute().use { response ->
                    when (response.code) {
                        HTTP_NO_CONTENT -> RemoteCredentialTransferContract.PollResult.Pending
                        HTTP_NOT_FOUND -> {
                            localSessions.remove(sessionId)
                            RemoteCredentialTransferContract.PollResult.Expired
                        }

                        HTTP_OK -> decryptPayload(sessionId, localSession, response.body.string())
                        else -> RemoteCredentialTransferContract.PollResult.Pending
                    }
                }
            }.getOrDefault(RemoteCredentialTransferContract.PollResult.Pending)
        }
    }

    override suspend fun cancel(sessionId: String) {
        val localSession = localSessions.remove(sessionId) ?: return
        withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url("${localSession.baseUrl}$ROUTE_PREFIX/v1/sessions/$sessionId")
                    .header(READ_TOKEN_HEADER, localSession.readToken)
                    .delete()
                    .build()
                httpClient.newCall(request).execute().close()
            }
        }
    }

    private suspend fun decryptPayload(
        sessionId: String,
        localSession: LocalSession,
        responseBody: String,
    ): RemoteCredentialTransferContract.PollResult {
        return runCatching {
            val envelope = JSONObject(responseBody)
            val plaintext = CredentialTransferCrypto.decrypt(
                sessionId = sessionId,
                receiverKeys = localSession.receiverKeys,
                senderPublicKey = envelope.getString("senderPublicKey"),
                nonce = envelope.getString("nonce"),
                ciphertext = envelope.getString("ciphertext"),
            )
            val credential = try {
                JSONObject(String(plaintext, StandardCharsets.UTF_8)).getString("authorizationCode")
            } finally {
                plaintext.fill(0)
            }
            require(credential.isNotBlank() && credential.length <= MAX_CREDENTIAL_LENGTH)
            cancel(sessionId)
            RemoteCredentialTransferContract.PollResult.Received(credential)
        }.getOrElse {
            cancel(sessionId)
            RemoteCredentialTransferContract.PollResult.InvalidPayload
        }
    }

    private fun buildQrCodeUrl(
        relayBaseUrl: String,
        sessionId: String,
        writeToken: String,
        publicKey: String,
        verificationCode: String,
        emailAddress: String,
    ): String {
        val maskedAddress = maskEmailAddress(emailAddress)
        val accountLabel = CredentialTransferCrypto.encodeBase64Url(maskedAddress.toByteArray(StandardCharsets.UTF_8))
        return "$relayBaseUrl$ROUTE_PREFIX/assist" +
            "#s=$sessionId&w=$writeToken&k=$publicKey&c=$verificationCode&a=$accountLabel"
    }

    private fun maskEmailAddress(emailAddress: String): String {
        val separator = emailAddress.indexOf('@')
        if (separator <= 0) return "***"
        val localPart = emailAddress.substring(0, separator)
        val visiblePrefix = localPart.take(2)
        return "$visiblePrefix***${emailAddress.substring(separator)}"
    }

    private data class LocalSession(
        val receiverKeys: KeyPair,
        val readToken: String,
        val baseUrl: String,
    )

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        const val ROUTE_PREFIX = "/kemi-assist"
        const val READ_TOKEN_HEADER = "X-KEMI-Read-Token"
        const val PROTOCOL_VERSION = 1
        const val SESSION_ID_BYTES = 24
        const val TOKEN_BYTES = 32
        const val MAX_CREDENTIAL_LENGTH = 4_096
        const val HTTP_OK = 200
        const val HTTP_CREATED = 201
        const val HTTP_NO_CONTENT = 204
        const val HTTP_NOT_FOUND = 404
    }
}
