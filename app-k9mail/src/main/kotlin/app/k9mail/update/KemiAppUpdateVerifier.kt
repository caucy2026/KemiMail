package app.k9mail.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest

internal fun interface KemiApkVerifier {
    fun verify(file: File, updateInfo: KemiAppUpdateInfo)
}

internal data class KemiPackageMetadata(
    val packageName: String,
    val versionCode: Long,
    val signerDigests: Set<String>,
)

internal interface KemiPackageMetadataReader {
    fun readInstalled(): KemiPackageMetadata
    fun readArchive(file: File): KemiPackageMetadata?
}

internal class DefaultKemiApkVerifier(
    private val metadataReader: KemiPackageMetadataReader,
) : KemiApkVerifier {
    override fun verify(file: File, updateInfo: KemiAppUpdateInfo) {
        val actualSha256 = file.sha256Hex()
        if (!MessageDigest.isEqual(actualSha256.toByteArray(), updateInfo.apkSha256.toByteArray())) {
            throw KemiAppUpdateException(KemiAppUpdateFailure.INTEGRITY)
        }

        val installed = metadataReader.readInstalled()
        val archive = metadataReader.readArchive(file)
            ?: throw KemiAppUpdateException(KemiAppUpdateFailure.PACKAGE)

        if (archive.packageName != installed.packageName || archive.packageName != updateInfo.packageName) {
            throw KemiAppUpdateException(KemiAppUpdateFailure.PACKAGE)
        }
        if (archive.versionCode != updateInfo.versionCode || archive.versionCode <= installed.versionCode) {
            throw KemiAppUpdateException(KemiAppUpdateFailure.PACKAGE)
        }
        if (installed.signerDigests.isEmpty() || archive.signerDigests.intersect(installed.signerDigests).isEmpty()) {
            throw KemiAppUpdateException(KemiAppUpdateFailure.SIGNATURE)
        }
    }
}

internal class AndroidKemiPackageMetadataReader(
    private val context: Context,
) : KemiPackageMetadataReader {
    override fun readInstalled(): KemiPackageMetadata {
        val packageInfo = context.packageManager.getPackageInfoCompat(context.packageName)
        return packageInfo.toMetadata()
    }

    override fun readArchive(file: File): KemiPackageMetadata? {
        return context.packageManager.getPackageArchiveInfoCompat(file.absolutePath)?.toMetadata()
    }

    @Suppress("DEPRECATION")
    private fun PackageManager.getPackageInfoCompat(packageName: String): PackageInfo {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        return getPackageInfo(packageName, flags)
    }

    @Suppress("DEPRECATION")
    private fun PackageManager.getPackageArchiveInfoCompat(path: String): PackageInfo? {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        return getPackageArchiveInfo(path, flags)
    }

    @Suppress("DEPRECATION")
    private fun PackageInfo.toMetadata(): KemiPackageMetadata {
        val version = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode else versionCode.toLong()
        val signers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = signingInfo
            when {
                signingInfo == null -> emptyArray()
                signingInfo.hasMultipleSigners() -> signingInfo.apkContentsSigners
                else -> signingInfo.signingCertificateHistory
            }
        } else {
            signatures.orEmpty()
        }

        return KemiPackageMetadata(
            packageName = packageName,
            versionCode = version,
            signerDigests = signers.mapTo(mutableSetOf()) { signature ->
                MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).toHex()
            },
        )
    }
}

internal fun File.sha256Hex(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    inputStream().buffered().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    return digest.digest().toHex()
}

private fun ByteArray.toHex(): String = joinToString(separator = "") { byte ->
    "%02x".format(byte.toInt() and UNSIGNED_BYTE_MASK)
}

private const val UNSIGNED_BYTE_MASK = 0xff
