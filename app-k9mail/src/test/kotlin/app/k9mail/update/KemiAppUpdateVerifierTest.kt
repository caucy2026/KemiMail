package app.k9mail.update

import assertk.assertFailure
import assertk.assertions.isInstanceOf
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test

class KemiAppUpdateVerifierTest {
    private val temporaryDirectory = createTempDirectory("kemi-app-update-test").toFile()

    @AfterTest
    fun tearDown() {
        temporaryDirectory.deleteRecursively()
    }

    @Test
    fun `verify should accept matching digest package version and signer`() {
        val apkFile = apkFile()
        val testSubject = DefaultKemiApkVerifier(
            FakePackageMetadataReader(
                installed = packageMetadata(versionCode = 39040, signer = "signer-a"),
                archive = packageMetadata(versionCode = 39041, signer = "signer-a"),
            ),
        )

        testSubject.verify(apkFile, updateInfo())
    }

    @Test
    fun `verify should reject a digest mismatch`() {
        val testSubject = DefaultKemiApkVerifier(
            FakePackageMetadataReader(
                installed = packageMetadata(versionCode = 39040, signer = "signer-a"),
                archive = packageMetadata(versionCode = 39041, signer = "signer-a"),
            ),
        )

        assertFailure {
            testSubject.verify(apkFile(), updateInfo(apkSha256 = "a".repeat(64)))
        }.isInstanceOf<KemiAppUpdateException>()
    }

    @Test
    fun `verify should reject a different package`() {
        val testSubject = DefaultKemiApkVerifier(
            FakePackageMetadataReader(
                installed = packageMetadata(versionCode = 39040, signer = "signer-a"),
                archive = packageMetadata(
                    packageName = "com.example.other",
                    versionCode = 39041,
                    signer = "signer-a",
                ),
            ),
        )

        assertFailure {
            testSubject.verify(apkFile(), updateInfo())
        }.isInstanceOf<KemiAppUpdateException>()
    }

    @Test
    fun `verify should reject a different signer`() {
        val testSubject = DefaultKemiApkVerifier(
            FakePackageMetadataReader(
                installed = packageMetadata(versionCode = 39040, signer = "signer-a"),
                archive = packageMetadata(versionCode = 39041, signer = "signer-b"),
            ),
        )

        assertFailure {
            testSubject.verify(apkFile(), updateInfo())
        }.isInstanceOf<KemiAppUpdateException>()
    }

    private fun apkFile(): File {
        return File(temporaryDirectory, "update.apk").apply { writeText("apk") }
    }

    private fun updateInfo(
        apkSha256: String = "dd37c2d7274f7ea982cb83390c36918fee9ce8889073c44b68cdc00bdb8c3e04",
    ): KemiAppUpdateInfo {
        return KemiAppUpdateInfo(
            packageName = PACKAGE_NAME,
            versionName = "20.2",
            versionCode = 39041,
            apkUrl = "https://download.example/update.apk",
            apkSha256 = apkSha256,
            fileSizeBytes = 3,
            forceUpdate = false,
            deepLink = null,
            releaseNotes = "",
        )
    }

    private fun packageMetadata(
        packageName: String = PACKAGE_NAME,
        versionCode: Long,
        signer: String,
    ): KemiPackageMetadata {
        return KemiPackageMetadata(
            packageName = packageName,
            versionCode = versionCode,
            signerDigests = setOf(signer),
        )
    }

    private class FakePackageMetadataReader(
        private val installed: KemiPackageMetadata,
        private val archive: KemiPackageMetadata?,
    ) : KemiPackageMetadataReader {
        override fun readInstalled(): KemiPackageMetadata = installed
        override fun readArchive(file: File): KemiPackageMetadata? = archive
    }

    private companion object {
        const val PACKAGE_NAME = "com.fsck.k9"
    }
}
