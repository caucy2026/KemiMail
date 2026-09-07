package app.k9mail.update

import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import org.json.JSONException
import org.json.JSONObject

internal interface KemiAppUpdateRepository {
    suspend fun checkForUpdate(packageName: String, localVersionCode: Long): KemiAppUpdateInfo?

    suspend fun download(
        updateInfo: KemiAppUpdateInfo,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
    ): File
}

internal class DefaultKemiAppUpdateRepository(
    private val context: Context,
    private val httpClient: OkHttpClient,
    apiBaseUrl: String,
    private val apkVerifier: KemiApkVerifier,
    private val requireHttps: Boolean = true,
) : KemiAppUpdateRepository {
    private val apiBaseUrl: HttpUrl = apiBaseUrl.toHttpUrl()

    override suspend fun checkForUpdate(
        packageName: String,
        localVersionCode: Long,
    ): KemiAppUpdateInfo? = withContext(Dispatchers.IO) {
        val url = apiBaseUrl.newBuilder()
            .addPathSegments("api/store/update/check")
            .addQueryParameter("package_name", packageName)
            .addQueryParameter("version_code", localVersionCode.toString())
            .build()
        val request = Request.Builder().url(url).get().build()

        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful || (requireHttps && !response.request.url.isHttps)) {
                    throw KemiAppUpdateException(KemiAppUpdateFailure.NETWORK)
                }
                parseCheckResponse(
                    responseBody = response.body.readLimited(MAX_CHECK_RESPONSE_BYTES),
                    expectedPackageName = packageName,
                    localVersionCode = localVersionCode,
                )
            }
        } catch (exception: KemiAppUpdateException) {
            throw exception
        } catch (exception: IOException) {
            throw KemiAppUpdateException(KemiAppUpdateFailure.NETWORK, exception)
        } catch (exception: JSONException) {
            throw KemiAppUpdateException(KemiAppUpdateFailure.INVALID_RESPONSE, exception)
        }
    }

    override suspend fun download(
        updateInfo: KemiAppUpdateInfo,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val downloadUrl = updateInfo.apkUrl.toHttpUrlOrNull()
            ?: throw KemiAppUpdateException(KemiAppUpdateFailure.INVALID_RESPONSE)
        if (requireHttps && !downloadUrl.isHttps) {
            throw KemiAppUpdateException(KemiAppUpdateFailure.INVALID_RESPONSE)
        }
        val updateDirectory = File(context.cacheDir, UPDATE_DIRECTORY_NAME)
        if ((!updateDirectory.exists() && !updateDirectory.mkdirs()) || !updateDirectory.isDirectory) {
            throw KemiAppUpdateException(KemiAppUpdateFailure.STORAGE)
        }

        val targetFile = File(updateDirectory, "kemi-mail-${updateInfo.versionCode}.apk")
        val partialFile = File(updateDirectory, "${targetFile.name}.part")
        partialFile.delete()

        var promoted = false
        try {
            downloadToFile(downloadUrl, partialFile, updateInfo.fileSizeBytes, onProgress)
            apkVerifier.verify(partialFile, updateInfo)
            targetFile.delete()
            if (!partialFile.renameTo(targetFile)) {
                throw KemiAppUpdateException(KemiAppUpdateFailure.STORAGE)
            }
            promoted = true
            targetFile
        } catch (exception: IOException) {
            throw KemiAppUpdateException(KemiAppUpdateFailure.DOWNLOAD, exception)
        } finally {
            if (!promoted) partialFile.delete()
        }
    }

    private suspend fun downloadToFile(
        downloadUrl: HttpUrl,
        partialFile: File,
        apiFileSize: Long?,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
    ) {
        val request = Request.Builder().url(downloadUrl).get().build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful || (requireHttps && !response.request.url.isHttps)) {
                downloadFailure()
            }

            val responseLength = response.body.contentLength().takeIf { it >= 0 }
            val expectedLength = responseLength ?: apiFileSize
            if (expectedLength != null && expectedLength > MAX_APK_BYTES) {
                downloadFailure()
            }
            val downloadedBytes = response.body.writeTo(partialFile, expectedLength, onProgress)
            if (responseLength != null && downloadedBytes != responseLength) {
                downloadFailure()
            }
            if (apiFileSize != null && downloadedBytes != apiFileSize) {
                downloadFailure()
            }
        }
    }

    private suspend fun ResponseBody.writeTo(
        partialFile: File,
        expectedLength: Long?,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
    ): Long {
        byteStream().use { input ->
            partialFile.outputStream().buffered().use { output ->
                return copyUpdate(input, output, expectedLength, onProgress)
            }
        }
    }

    private suspend fun copyUpdate(
        input: InputStream,
        output: OutputStream,
        expectedLength: Long?,
        onProgress: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
    ): Long {
        val buffer = ByteArray(DOWNLOAD_BUFFER_BYTES)
        var downloadedBytes = 0L
        while (true) {
            coroutineContext.ensureActive()
            val count = input.read(buffer)
            if (count < 0) break
            downloadedBytes += count
            if (downloadedBytes > MAX_APK_BYTES) downloadFailure()
            output.write(buffer, 0, count)
            onProgress(downloadedBytes, expectedLength)
        }
        return downloadedBytes
    }

    private fun parseCheckResponse(
        responseBody: String,
        expectedPackageName: String,
        localVersionCode: Long,
    ): KemiAppUpdateInfo? {
        val root = JSONObject(responseBody)
        if (root.getInt("status") != HTTP_OK) {
            invalidCheckResponse()
        }
        val data = root.getJSONObject("data")
        if (!data.optBoolean("has_update", false)) return null

        val packageName = data.getString("package_name")
        val versionCode = data.getLong("version_code")
        if (packageName != expectedPackageName || versionCode <= localVersionCode) {
            invalidCheckResponse()
        }

        val apkUrl = sequenceOf(data.optString("apk_url"), data.optString("download_url"))
            .firstOrNull { it.isNotBlank() }
            ?.toHttpUrlOrNull()
            ?.takeIf(HttpUrl::isHttps)
            ?.toString()
            ?: invalidCheckResponse()
        val apkSha256 = data.optString("apk_sha256")
            .trim()
            .lowercase()
            .takeIf(String::isNotEmpty)
            ?.also { hash ->
                if (!SHA_256_PATTERN.matches(hash)) {
                    invalidCheckResponse()
                }
            }
            ?: invalidCheckResponse()
        val deepLink = data.optString("deeplink")
            .trim()
            .takeIf { it.startsWith(STORE_DEEP_LINK_PREFIX) }
        val fileSizeBytes = when (val fileSize = data.opt("file_size")) {
            is Number -> fileSize.toLong()
            is String -> fileSize.toLongOrNull()
            else -> null
        }?.takeIf { it > 0 }
        val releaseNotes = data.optString("release_notes")
            .ifBlank { data.optString("short_desc") }

        return KemiAppUpdateInfo(
            packageName = packageName,
            versionName = data.optString("version_name").ifBlank { versionCode.toString() },
            versionCode = versionCode,
            apkUrl = apkUrl,
            apkSha256 = apkSha256,
            fileSizeBytes = fileSizeBytes,
            forceUpdate = data.optBoolean("force_update", false),
            deepLink = deepLink,
            releaseNotes = releaseNotes,
        )
    }

    private fun okhttp3.ResponseBody.readLimited(maxBytes: Int): String {
        val contentLength = contentLength()
        if (contentLength > maxBytes) {
            throw KemiAppUpdateException(KemiAppUpdateFailure.INVALID_RESPONSE)
        }

        val output = ByteArrayOutputStream()
        byteStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (output.size() + count > maxBytes) {
                    throw KemiAppUpdateException(KemiAppUpdateFailure.INVALID_RESPONSE)
                }
                output.write(buffer, 0, count)
            }
        }
        return output.toString(StandardCharsets.UTF_8.name())
    }

    private fun invalidCheckResponse(): Nothing {
        throw KemiAppUpdateException(KemiAppUpdateFailure.INVALID_RESPONSE)
    }

    private fun downloadFailure(): Nothing {
        throw KemiAppUpdateException(KemiAppUpdateFailure.DOWNLOAD)
    }

    private companion object {
        const val HTTP_OK = 200
        const val MAX_CHECK_RESPONSE_BYTES = 256 * 1024
        const val MAX_APK_BYTES = 250L * 1024 * 1024
        const val DOWNLOAD_BUFFER_BYTES = 64 * 1024
        const val UPDATE_DIRECTORY_NAME = "self-update"
        const val STORE_DEEP_LINK_PREFIX = "kemiappstore://app/"
        val SHA_256_PATTERN = Regex("[0-9a-f]{64}")
    }
}
