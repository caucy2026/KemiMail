package com.kemi.windows

import jakarta.mail.internet.InternetAddress
import java.nio.file.Path
import java.time.Instant
import java.util.UUID

enum class Security { TLS, STARTTLS }

data class Account(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val email: String = "",
    val username: String = "",
    val password: String = "",
    val imapHost: String = "",
    val imapPort: Int = 993,
    val imapSecurity: Security = Security.TLS,
    val smtpHost: String = "",
    val smtpPort: Int = 465,
    val smtpSecurity: Security = Security.TLS,
) {
    override fun toString() = "Account(id=$id)"
    fun validate() {
        require(name.length <= 120) { "显示名称过长" }
        require(!name.contains('\r') && !name.contains('\n')) { "显示名称不能换行" }
        require(addresses(email).size == 1) { "请输入一个有效的邮箱地址" }
        require(username.isNotBlank() && password.isNotBlank()) { "请填写登录用户名和邮箱授权码" }
        for (host in listOf(imapHost, smtpHost)) {
            require(host.length in 1..253 && host.all { it.isLetterOrDigit() || it == '.' || it == '-' } &&
                !host.startsWith('.') && !host.endsWith('.')) { "服务器只填写域名或 IPv4 地址，不含协议和端口" }
        }
        require(imapPort in 1..65535 && smtpPort in 1..65535) { "端口必须在 1–65535 之间" }
    }
}

fun addresses(value: String): List<InternetAddress> {
    require(!value.contains('\r') && !value.contains('\n')) { "邮箱地址不能换行" }
    return try {
        InternetAddress.parse(value.replace('；', ',').replace(';', ','), true).toList().also { list ->
            require(list.isNotEmpty()) { "请填写邮箱地址" }
            list.forEach { it.validate(); require(it.address.contains('@') && !it.isGroup) }
        }
    } catch (_: Exception) { throw IllegalArgumentException("邮箱地址格式不正确") }
}

data class MailFolder(val path: String, val label: String, val trash: Boolean = false, val sent: Boolean = false)
data class MailSummary(val uid: Long, val validity: Long, val subject: String, val sender: String,
                       val date: Instant?, val seen: Boolean, val starred: Boolean)
data class Attachment(val name: String, val bytes: ByteArray) {
    override fun toString() = "Attachment(size=${bytes.size})"
}
data class MailDetail(val summary: MailSummary, val to: String, val replyTo: String, val messageId: String?,
                      val body: String, val attachments: List<Attachment>)
data class ComposeDraft(val to: String = "", val cc: String = "", val subject: String = "",
                        val body: String = "", val files: List<Path> = emptyList(), val replyId: String? = null) {
    fun validate() {
        addresses(to)
        if (cc.isNotBlank()) addresses(cc)
        require(subject.length <= 998 && !subject.contains('\r') && !subject.contains('\n')) { "主题过长或包含换行" }
        require(body.length <= 1_000_000) { "正文过长" }
        require(files.size <= 20) { "最多添加 20 个附件" }
        require(files.all { java.nio.file.Files.isRegularFile(it) }) { "附件文件已移动或不可读取，请重新添加" }
        require(files.sumOf { java.nio.file.Files.size(it) } <= MAX_MESSAGE_BYTES) { "附件总大小不能超过 20 MB" }
    }
}
const val MAX_MESSAGE_BYTES = 20L * 1024 * 1024

interface MailGateway {
    suspend fun check(account: Account)
    suspend fun folders(account: Account): List<MailFolder>
    suspend fun list(account: Account, folder: String, limit: Int): List<MailSummary>
    suspend fun read(account: Account, folder: String, mail: MailSummary): MailDetail
    suspend fun flag(account: Account, folder: String, mail: MailSummary, seen: Boolean? = null, starred: Boolean? = null)
    suspend fun trash(account: Account, folder: String, mail: MailSummary, destination: String)
    suspend fun send(account: Account, draft: ComposeDraft): SendResult
}
data class SendResult(val savedToSent: Boolean)
class MailFailure(val userMessage: String) : Exception(userMessage)

fun safeAttachmentName(name: String): String {
    val base = name.substringAfterLast('/').substringAfterLast('\\')
        .replace(Regex("[<>:\"/\\\\|?*\\x00-\\x1f]"), "_").trim().trimEnd('.')
    val result = base.take(160).ifBlank { "attachment" }
    return if (Regex("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\\..*)?").matches(result)) "_$result" else result
}
