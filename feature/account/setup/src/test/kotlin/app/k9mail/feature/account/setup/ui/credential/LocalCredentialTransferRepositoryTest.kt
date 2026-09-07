package app.k9mail.feature.account.setup.ui.credential

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isTrue
import java.net.Inet4Address
import java.net.InetAddress
import java.net.URI
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LocalCredentialTransferRepositoryTest {
    private val address = InetAddress.getByName("127.0.0.1") as Inet4Address
    private val httpClient = OkHttpClient()
    private var currentTime = START_TIME
    private var sessionId: String? = null
    private lateinit var testSubject: LocalCredentialTransferRepository

    @BeforeTest
    fun setUp() {
        testSubject = LocalCredentialTransferRepository(
            addressProvider = { address },
            clock = { currentTime },
        )
    }

    @AfterTest
    fun tearDown() {
        runBlocking {
            sessionId?.let { testSubject.cancel(it) }
        }
    }

    @Test
    fun `start should create short-lived local HTTP session`() = runTest {
        val result = testSubject.start("someone@example.com")

        assertThat(result).isInstanceOf<RemoteCredentialTransferContract.StartResult.Success>()
        val session = (result as RemoteCredentialTransferContract.StartResult.Success).session
        sessionId = session.id
        assertThat(session.qrCodeUrl.startsWith("http://127.0.0.1:")).isTrue()
        assertThat(session.qrCodeUrl).contains("/kemi-assist/assist#")
        assertThat(session.qrCodeUrl).doesNotContain("someone@example.com")
        assertThat(session.verificationCode).isEqualTo("")
        assertThat(session.expiresAt).isEqualTo(START_TIME + SESSION_TTL_MS)

        val fragment = URI.create(session.qrCodeUrl).rawFragment
        assertThat(fragment).contains("s=${session.id}")
        assertThat(fragment).contains("w=")
    }

    @Test
    fun `phone page should disclose plaintext local HTTP transfer`() = runTest {
        val session = startSession()
        val request = Request.Builder().url(session.qrCodeUrl.substringBefore('#')).build()

        val exchange = executeWhilePolling(session.id, request)

        exchange.response.use { response ->
            assertThat(response.code).isEqualTo(200)
            val page = response.body.string()
            assertThat(page).contains("未加密的局域网 HTTP")
            assertThat(page).contains("不要输入邮箱主密码")
        }
        assertThat(exchange.pollResult).isEqualTo(RemoteCredentialTransferContract.PollResult.Pending)
    }

    @Test
    fun `valid one-time token should deliver credential and close session`() = runTest {
        val session = startSession()
        val writeToken = fragmentValue(session.qrCodeUrl, "w")
        val request = credentialRequest(session, writeToken, "app-password")

        val exchange = executeWhilePolling(session.id, request)

        exchange.response.use { response -> assertThat(response.code).isEqualTo(204) }
        assertThat(exchange.pollResult).isEqualTo(
            RemoteCredentialTransferContract.PollResult.Received("app-password"),
        )
        assertThat(testSubject.poll(session.id)).isEqualTo(RemoteCredentialTransferContract.PollResult.Expired)
    }

    @Test
    fun `invalid token should be rejected without consuming session`() = runTest {
        val session = startSession()
        val invalidRequest = credentialRequest(session, "invalid-token", "app-password")

        val rejectedExchange = executeWhilePolling(session.id, invalidRequest)

        rejectedExchange.response.use { response -> assertThat(response.code).isEqualTo(401) }
        assertThat(rejectedExchange.pollResult).isEqualTo(RemoteCredentialTransferContract.PollResult.Pending)

        val writeToken = fragmentValue(session.qrCodeUrl, "w")
        val acceptedExchange = executeWhilePolling(
            session.id,
            credentialRequest(session, writeToken, "app-password"),
        )
        acceptedExchange.response.use { response -> assertThat(response.code).isEqualTo(204) }
        assertThat(acceptedExchange.pollResult).isEqualTo(
            RemoteCredentialTransferContract.PollResult.Received("app-password"),
        )
    }

    @Test
    fun `expired session should close without accepting a connection`() = runTest {
        val session = startSession()
        currentTime = session.expiresAt

        val result = testSubject.poll(session.id)

        assertThat(result).isEqualTo(RemoteCredentialTransferContract.PollResult.Expired)
        assertThat(canConnect(session.qrCodeUrl)).isFalse()
    }

    private suspend fun startSession(): RemoteCredentialTransferContract.Session {
        val result = testSubject.start("someone@example.com")
        val session = (result as RemoteCredentialTransferContract.StartResult.Success).session
        sessionId = session.id
        return session
    }

    private suspend fun executeWhilePolling(sessionId: String, request: Request): Exchange {
        return withContext(Dispatchers.Default) {
            withTimeout(5_000) {
                val response = async(Dispatchers.IO) { httpClient.newCall(request).execute() }
                var pollResult: RemoteCredentialTransferContract.PollResult
                do {
                    pollResult = testSubject.poll(sessionId)
                } while (pollResult == RemoteCredentialTransferContract.PollResult.Pending && !response.isCompleted)
                Exchange(response.await(), pollResult)
            }
        }
    }

    private fun credentialRequest(
        session: RemoteCredentialTransferContract.Session,
        writeToken: String,
        credential: String,
    ): Request {
        val uri = URI.create(session.qrCodeUrl)
        val requestBody = """{"version":1,"authorizationCode":"$credential"}"""
            .toRequestBody("application/json".toMediaType())
        return Request.Builder()
            .url("http://${uri.host}:${uri.port}/kemi-assist/v1/sessions/${session.id}/credential")
            .header("X-KEMI-Write-Token", writeToken)
            .post(requestBody)
            .build()
    }

    private fun fragmentValue(url: String, name: String): String {
        return URI.create(url).rawFragment.split('&')
            .first { it.startsWith("$name=") }
            .substringAfter('=')
    }

    private fun canConnect(url: String): Boolean {
        return runCatching {
            httpClient.newCall(Request.Builder().url(url.substringBefore('#')).build()).execute().use { }
        }.isSuccess
    }

    private data class Exchange(
        val response: Response,
        val pollResult: RemoteCredentialTransferContract.PollResult,
    )

    private companion object {
        const val START_TIME = 1_000_000L
        const val SESSION_TTL_MS = 5 * 60 * 1_000L
    }
}
