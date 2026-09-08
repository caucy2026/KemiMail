package com.kemi.windows

import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.*
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Properties
import kotlin.coroutines.coroutineContext

internal object MailRelease {
    private val values = Properties().apply {
        MailRelease::class.java.getResourceAsStream("/version.properties")!!.use { load(it) }
    }
    val name: String = values.getProperty("versionName")
    val code: Int = values.getProperty("versionCode").toInt()
    val packageName: String = values.getProperty("packageName")
}
internal data class MailUpdate(val name: String,val code: Int,val url: URI,val sha256: String,val size: Long,
                               val notes: String,val forced: Boolean)
internal class UpdateFailure(message: String) : Exception(message)
internal fun requireUpdate(value: Boolean,message: String) { if (!value) throw UpdateFailure(message) }

internal class KemiUpdateClient {
    companion object {
        const val BASE = "https://kemi.newlinksz.com/kd-api/api/store/update/check"
        const val MAX_PACKAGE = 2L * 1024 * 1024 * 1024
        fun secureUri(text: String): URI {
            val uri = try { URI(text) } catch (_: Exception) { throw UpdateFailure("更新地址格式不正确") }
            requireUpdate(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null,
                "更新地址必须使用 HTTPS")
            return uri
        }
    }
    fun parse(text: String,localCode: Int = MailRelease.code): MailUpdate? {
        val root = try { Json.parseToJsonElement(text).jsonObject } catch (_: Exception) { throw UpdateFailure("更新服务返回格式不正确") }
        fun JsonObject.text(key: String) = (get(key) as? JsonPrimitive)?.contentOrNull
        requireUpdate(root.text("status") == "200","更新服务暂不可用")
        val data = root["data"] as? JsonObject ?: throw UpdateFailure("更新服务缺少版本信息")
        requireUpdate(data.text("package_name") == MailRelease.packageName && data.text("os_type") == "windows" &&
            data.text("local_version_code")?.toIntOrNull() == localCode,"更新信息与当前应用不匹配")
        val hasUpdate = (data["has_update"] as? JsonPrimitive)?.booleanOrNull
        requireUpdate(hasUpdate != null,"更新服务缺少更新状态")
        if (hasUpdate == false) return null
        val code = data.text("version_code")?.toIntOrNull() ?: 0
        val name = data.text("version_name").orEmpty()
        requireUpdate(code > localCode && Regex("[0-9]+\\.[0-9]+\\.[0-9]+").matches(name),"更新版本无效或没有递增")
        val uri = secureUri(data.text("download_url")?.takeIf { it.isNotBlank() } ?: data.text("apk_url").orEmpty())
        requireUpdate(uri.path.endsWith(".exe",true),"当前版本仅支持 EXE 安装包更新")
        val hash = (data.text("sha256")?.takeIf { it.isNotBlank() } ?: data.text("apk_sha256").orEmpty()).lowercase()
        requireUpdate(Regex("[0-9a-f]{64}").matches(hash),"更新包缺少有效校验值")
        val size = data.text("file_size_bytes")?.toLongOrNull()?.takeIf { it > 0 } ?: data.text("file_size")?.toLongOrNull() ?: 0
        requireUpdate(size in 1..MAX_PACKAGE,"更新包大小无效")
        val forced = when (data.text("force_update")) { "true", "1" -> true; "false", "0" -> false; else -> throw UpdateFailure("更新策略无效") }
        return MailUpdate(name,code,uri,hash,size,(data.text("release_notes") ?: data.text("short_desc").orEmpty()).take(12000),forced)
    }
    private fun open(uri: URI): HttpURLConnection {
        var current = uri
        repeat(6) {
            secureUri(current.toString())
            val connection = current.toURL().openConnection() as HttpURLConnection
            connection.connectTimeout = 15000; connection.readTimeout = 30000
            connection.instanceFollowRedirects = false
            try {
                when (connection.responseCode) {
                    in 300..399 -> {
                        val location = connection.getHeaderField("Location") ?: throw UpdateFailure("更新下载跳转无效")
                        current = current.resolve(location); connection.disconnect()
                    }
                    200 -> return connection
                    else -> throw UpdateFailure("更新服务请求失败，请稍后重试")
                }
            } catch (error: Exception) { connection.disconnect(); throw error }
        }
        throw UpdateFailure("更新下载跳转次数过多")
    }
    fun check(localCode: Int = MailRelease.code): MailUpdate? {
        val connection = open(URI("$BASE?package_name=${MailRelease.packageName}&version_code=$localCode&os=windows"))
        try {
            val bytes = connection.inputStream.use { it.readNBytes(262145) }
            requireUpdate(bytes.size <= 262144,"更新响应过大")
            return parse(bytes.toString(Charsets.UTF_8),localCode)
        } finally { connection.disconnect() }
    }
    suspend fun copyVerified(input: InputStream,target: Path,update: MailUpdate,progress: (Int) -> Unit) {
        var complete = false
        var created = false
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var count = 0L
            Files.newOutputStream(target,java.nio.file.StandardOpenOption.CREATE_NEW).use { output ->
                created = true
                val buffer = ByteArray(65536)
                while (true) {
                    coroutineContext.ensureActive()
                    val read = input.read(buffer); if (read < 0) break
                    count += read; requireUpdate(count <= update.size,"更新包大小超出预期")
                    digest.update(buffer,0,read); output.write(buffer,0,read)
                    progress((count * 100 / update.size).toInt())
                }
            }
            requireUpdate(count == update.size,"更新包下载不完整")
            requireUpdate(digest.digest().joinToString("") { "%02x".format(it) } == update.sha256,"更新包校验失败，请重新下载")
            complete = true
        } finally { if (created && !complete) Files.deleteIfExists(target) }
    }
    suspend fun download(update: MailUpdate,directory: Path,progress: (Int) -> Unit): Path {
        Files.createDirectories(directory)
        val target = directory.resolve("setup.exe")
        val connection = open(update.url)
        try {
            requireUpdate(connection.contentLengthLong < 0 || connection.contentLengthLong == update.size,"更新包长度不匹配")
            connection.inputStream.use { copyVerified(it,target,update,progress) }
            return target
        } finally { connection.disconnect() }
    }
}
