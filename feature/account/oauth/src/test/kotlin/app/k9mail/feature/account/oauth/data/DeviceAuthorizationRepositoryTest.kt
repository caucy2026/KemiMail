package app.k9mail.feature.account.oauth.data

import app.k9mail.feature.account.oauth.domain.entity.DeviceAuthorizationSession
import app.k9mail.feature.account.oauth.domain.entity.PollDeviceAuthorizationResult
import app.k9mail.feature.account.oauth.domain.entity.StartDeviceAuthorizationResult
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import net.thunderbird.core.common.oauth.OAuthConfiguration
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DeviceAuthorizationRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var configuration: OAuthConfiguration
    private lateinit var testSubject: DeviceAuthorizationRepository

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        configuration = OAuthConfiguration(
            clientId = "client-id",
            scopes = listOf("openid", "offline_access", "mail.read"),
            authorizationEndpoint = server.url("/authorize").toString(),
            tokenEndpoint = server.url("/token").toString(),
            redirectUri = "example:/oauth",
            deviceAuthorizationEndpoint = server.url("/devicecode").toString(),
        )
        testSubject = DeviceAuthorizationRepository(
            httpClient = OkHttpClient(),
            clock = { NOW },
        )
    }

    @AfterTest
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `start should request device code and return session`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{
                    "device_code":"device-code",
                    "user_code":"ABCD-EFGH",
                    "verification_uri":"https://microsoft.com/devicelogin",
                    "expires_in":900,
                    "interval":5
                }
                """.trimIndent(),
            ),
        )

        val result = testSubject.start(configuration)

        assertThat(result).isEqualTo(
            StartDeviceAuthorizationResult.Success(
                DeviceAuthorizationSession(
                    configuration = configuration,
                    deviceCode = "device-code",
                    userCode = "ABCD-EFGH",
                    verificationUri = "https://microsoft.com/devicelogin",
                    expiresAt = NOW + 900_000,
                    pollingIntervalSeconds = 5,
                ),
            ),
        )
        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/devicecode")
        assertThat(request.body.readUtf8()).contains(
            "client_id=client-id&scope=openid+offline_access+mail.read",
        )
    }

    @Test
    fun `poll should turn token response into authorization state`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{
                    "token_type":"Bearer",
                    "scope":"openid offline_access mail.read",
                    "expires_in":3600,
                    "access_token":"access-token",
                    "refresh_token":"refresh-token"
                }
                """.trimIndent(),
            ),
        )

        val result = testSubject.poll(session())

        assertThat(result).isInstanceOf<PollDeviceAuthorizationResult.Success>()
        val authState = (result as PollDeviceAuthorizationResult.Success).authorizationState.toAuthState()
        assertThat(authState.accessToken).isEqualTo("access-token")
        assertThat(authState.refreshToken).isEqualTo("refresh-token")
        val request = server.takeRequest()
        assertThat(request.path).isEqualTo("/token")
        val expectedRequestBody = "grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Adevice_code" +
            "&client_id=client-id&device_code=device-code"
        assertThat(request.body.readUtf8()).contains(
            expectedRequestBody,
        )
    }

    @Test
    fun `poll should map expected device authorization errors`() = runTest {
        val answers = listOf(
            "authorization_pending" to PollDeviceAuthorizationResult.Pending,
            "slow_down" to PollDeviceAuthorizationResult.SlowDown,
            "authorization_declined" to PollDeviceAuthorizationResult.Declined,
            "expired_token" to PollDeviceAuthorizationResult.Expired,
            "bad_verification_code" to PollDeviceAuthorizationResult.Failure,
        )

        for ((error, expected) in answers) {
            server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"$error"}"""))
            assertThat(testSubject.poll(session())).isEqualTo(expected)
        }
    }

    private fun session(): DeviceAuthorizationSession {
        return DeviceAuthorizationSession(
            configuration = configuration,
            deviceCode = "device-code",
            userCode = "ABCD-EFGH",
            verificationUri = "https://microsoft.com/devicelogin",
            expiresAt = NOW + 900_000,
            pollingIntervalSeconds = 5,
        )
    }

    private companion object {
        const val NOW = 1_000_000L
    }
}
