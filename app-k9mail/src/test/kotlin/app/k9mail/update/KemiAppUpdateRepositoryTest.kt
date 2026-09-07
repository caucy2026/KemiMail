package app.k9mail.update

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNull
import assertk.assertions.isTrue
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class KemiAppUpdateRepositoryTest {
    private lateinit var server: MockWebServer
    private lateinit var testSubject: DefaultKemiAppUpdateRepository

    @BeforeTest
    fun setUp() {
        server = MockWebServer()
        testSubject = DefaultKemiAppUpdateRepository(
            context = ApplicationProvider.getApplicationContext<Context>(),
            httpClient = OkHttpClient(),
            apiBaseUrl = server.url("/kd-api").toString(),
            apkVerifier = KemiApkVerifier { _, _ -> },
            requireHttps = false,
        )
    }

    @AfterTest
    fun tearDown() {
        server.shutdown()
        File(ApplicationProvider.getApplicationContext<Context>().cacheDir, "self-update").deleteRecursively()
    }

    @Test
    fun `check should send package and version and return no update`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"status":200,"msg":"success","data":{"has_update":false}}""",
            ),
        )

        val result = testSubject.checkForUpdate(PACKAGE_NAME, LOCAL_VERSION_CODE)

        assertThat(result).isNull()
        assertThat(server.takeRequest().path).isEqualTo(
            "/kd-api/api/store/update/check?package_name=com.fsck.k9&version_code=39040",
        )
    }

    @Test
    fun `check should parse a valid update`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "status": 200,
                  "msg": "success",
                  "data": {
                    "has_update": true,
                    "package_name": "com.fsck.k9",
                    "version_name": "20.2",
                    "version_code": 39041,
                    "apk_url": "https://download.example/kemi-mail.apk",
                    "apk_sha256": "${"a".repeat(64)}",
                    "file_size": "123456",
                    "force_update": true,
                    "list_in_store": true,
                    "deeplink": "kemiappstore://app/12",
                    "release_notes": "Security fixes"
                  }
                }
                """.trimIndent(),
            ),
        )

        val result = requireNotNull(testSubject.checkForUpdate(PACKAGE_NAME, LOCAL_VERSION_CODE))

        assertThat(result.packageName).isEqualTo(PACKAGE_NAME)
        assertThat(result.versionName).isEqualTo("20.2")
        assertThat(result.versionCode).isEqualTo(39041L)
        assertThat(result.apkUrl).isEqualTo("https://download.example/kemi-mail.apk")
        assertThat(result.apkSha256).isEqualTo("a".repeat(64))
        assertThat(result.fileSizeBytes).isEqualTo(123456L)
        assertThat(result.forceUpdate).isTrue()
        assertThat(result.deepLink).isEqualTo("kemiappstore://app/12")
        assertThat(result.releaseNotes).isEqualTo("Security fixes")
    }

    @Test
    fun `check should reject an insecure apk url`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "status": 200,
                  "data": {
                    "has_update": true,
                    "package_name": "com.fsck.k9",
                    "version_name": "20.2",
                    "version_code": 39041,
                    "apk_url": "http://download.example/kemi-mail.apk",
                    "force_update": false
                  }
                }
                """.trimIndent(),
            ),
        )

        assertFailure {
            testSubject.checkForUpdate(PACKAGE_NAME, LOCAL_VERSION_CODE)
        }.isInstanceOf<KemiAppUpdateException>()
    }

    @Test
    fun `check should reject an update without a sha256 digest`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "status": 200,
                  "data": {
                    "has_update": true,
                    "package_name": "com.fsck.k9",
                    "version_name": "20.2",
                    "version_code": 39041,
                    "apk_url": "https://download.example/kemi-mail.apk",
                    "force_update": false
                  }
                }
                """.trimIndent(),
            ),
        )

        assertFailure {
            testSubject.checkForUpdate(PACKAGE_NAME, LOCAL_VERSION_CODE)
        }.isInstanceOf<KemiAppUpdateException>()
    }

    @Test
    fun `check should ignore an invalid store deep link and retain direct download`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """
                {
                  "status": 200,
                  "data": {
                    "has_update": true,
                    "package_name": "com.fsck.k9",
                    "version_name": "20.2",
                    "version_code": 39041,
                    "apk_url": "https://download.example/kemi-mail.apk",
                    "apk_sha256": "${"a".repeat(64)}",
                    "force_update": false,
                    "deeplink": "https://malicious.example/app/12"
                  }
                }
                """.trimIndent(),
            ),
        )

        val result = requireNotNull(testSubject.checkForUpdate(PACKAGE_NAME, LOCAL_VERSION_CODE))

        assertThat(result.forceUpdate).isFalse()
        assertThat(result.deepLink).isNull()
    }

    @Test
    fun `download should store the complete file after verification`() = runTest {
        server.enqueue(MockResponse().setBody("apk"))
        val updateInfo = KemiAppUpdateInfo(
            packageName = PACKAGE_NAME,
            versionName = "20.2",
            versionCode = 39041,
            apkUrl = server.url("/kemi-mail.apk").toString(),
            apkSha256 = "dd37c2d7274f7ea982cb83390c36918fee9ce8889073c44b68cdc00bdb8c3e04",
            fileSizeBytes = 3,
            forceUpdate = false,
            deepLink = null,
            releaseNotes = "",
        )

        val result = testSubject.download(updateInfo) { _, _ -> }

        assertThat(result.readText()).isEqualTo("apk")
        assertThat(result.name).isEqualTo("kemi-mail-39041.apk")
    }

    private companion object {
        const val PACKAGE_NAME = "com.fsck.k9"
        const val LOCAL_VERSION_CODE = 39040L
    }
}
