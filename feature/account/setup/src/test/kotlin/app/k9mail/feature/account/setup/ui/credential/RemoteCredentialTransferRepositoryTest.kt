package app.k9mail.feature.account.setup.ui.credential

import assertk.assertThat
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.matches
import java.net.URI
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import net.thunderbird.core.common.credentials.RemoteCredentialTransferConfiguration
import net.thunderbird.core.common.credentials.RemoteCredentialTransferConfigurationProvider
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RemoteCredentialTransferRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var testSubject: RemoteCredentialTransferRepository

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        val serverUrl = server.url("/")
        val httpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val rewrittenUrl = chain.request().url.newBuilder()
                    .scheme(serverUrl.scheme)
                    .host(serverUrl.host)
                    .port(serverUrl.port)
                    .build()
                chain.proceed(chain.request().newBuilder().url(rewrittenUrl).build())
            }
            .build()
        testSubject = RemoteCredentialTransferRepository(
            httpClient = httpClient,
            configurationProvider = RemoteCredentialTransferConfigurationProvider {
                RemoteCredentialTransferConfiguration("https://relay.example")
            },
        )
    }

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `start should create capability session without sending plaintext capability tokens`() = runTest {
        server.enqueue(createdSessionResponse())

        val result = testSubject.start("someone@example.com")

        assertThat(result).isInstanceOf<RemoteCredentialTransferContract.StartResult.Success>()
        val session = (result as RemoteCredentialTransferContract.StartResult.Success).session
        assertThat(session.expiresAt).isEqualTo(EXPIRES_AT)
        assertThat(session.verificationCode).matches(Regex("\\d{6}"))
        assertThat(session.qrCodeUrl.startsWith("https://relay.example/kemi-assist/assist#")).isEqualTo(true)
        assertThat(session.qrCodeUrl).doesNotContain("someone@example.com")

        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/kemi-assist/v1/sessions")
        val body = JSONObject(request.body.readUtf8())
        assertThat(body.getString("sessionId")).isEqualTo(session.id)
        assertThat(body.getString("writeTokenHash")).matches(Regex("[A-Za-z0-9_-]{43}"))
        assertThat(body.getString("readTokenHash")).matches(Regex("[A-Za-z0-9_-]{43}"))

        val fragment = URI.create(session.qrCodeUrl).rawFragment
        assertThat(fragment.contains("s=${session.id}")).isEqualTo(true)
        assertThat(fragment).doesNotContain(body.getString("readTokenHash"))
    }

    @Test
    fun `poll should return pending and cancel should delete session`() = runTest {
        server.enqueue(createdSessionResponse())
        val session = (
            testSubject.start("someone@example.com") as
                RemoteCredentialTransferContract.StartResult.Success
            ).session
        server.takeRequest()
        server.enqueue(MockResponse().setResponseCode(204))

        assertThat(testSubject.poll(session.id)).isEqualTo(RemoteCredentialTransferContract.PollResult.Pending)
        assertThat(server.takeRequest().path).isEqualTo(
            "/kemi-assist/v1/sessions/${session.id}/payload",
        )

        server.enqueue(MockResponse().setResponseCode(204))
        testSubject.cancel(session.id)

        assertThat(server.takeRequest().path).isEqualTo("/kemi-assist/v1/sessions/${session.id}")
    }

    @Test
    fun `poll should reject invalid encrypted payload and delete session`() = runTest {
        server.enqueue(createdSessionResponse())
        val session = (
            testSubject.start("someone@example.com") as
                RemoteCredentialTransferContract.StartResult.Success
            ).session
        server.takeRequest()
        server.enqueue(
            MockResponse().setBody(
                """{"version":1,"senderPublicKey":"invalid","nonce":"invalid","ciphertext":"invalid"}""",
            ),
        )
        server.enqueue(MockResponse().setResponseCode(204))

        val result = testSubject.poll(session.id)

        assertThat(result).isEqualTo(RemoteCredentialTransferContract.PollResult.InvalidPayload)
        server.takeRequest()
        assertThat(server.takeRequest().path).isEqualTo("/kemi-assist/v1/sessions/${session.id}")
    }

    private fun createdSessionResponse(): MockResponse {
        return MockResponse()
            .setResponseCode(201)
            .setBody("""{"version":1,"ok":true,"expiresAt":$EXPIRES_AT}""")
    }

    private companion object {
        const val EXPIRES_AT = 1_300_000L
    }
}
