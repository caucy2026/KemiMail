package com.kemi.windows

import com.sun.jna.platform.win32.Crypt32Util
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Properties

interface SecretProtector {
    fun encrypt(bytes: ByteArray): ByteArray
    fun decrypt(bytes: ByteArray): ByteArray
}
class WindowsProtector : SecretProtector {
    init { check(System.getProperty("os.name").startsWith("Windows")) { "凭证存储仅支持 Windows" } }
    override fun encrypt(bytes: ByteArray): ByteArray = Crypt32Util.cryptProtectData(bytes)
    override fun decrypt(bytes: ByteArray): ByteArray = Crypt32Util.cryptUnprotectData(bytes)
}

/** DPAPI encrypts the entire file for the current Windows user, including account identifiers. */
class AccountVault(private val directory: Path, private val protector: SecretProtector) {
    private val accountsFile get() = directory.resolve("accounts.bin")
    @Synchronized fun load(): List<Account> {
        val p = read(accountsFile)
        val count = p.getProperty("count", "0").toInt().also { require(it in 0..100) }
        return (0 until count).map { i ->
            fun field(key: String) = p.getProperty("$i.$key") ?: error("账号配置不完整")
            Account(field("id"), field("name"), field("email"), field("username"), field("password"),
                field("imapHost"), field("imapPort").toInt(), Security.valueOf(field("imapSecurity")),
                field("smtpHost"), field("smtpPort").toInt(), Security.valueOf(field("smtpSecurity")))
        }
    }
    @Synchronized fun save(accounts: List<Account>) {
        val p = Properties().apply { setProperty("count", accounts.size.toString()) }
        accounts.forEachIndexed { i, a ->
            mapOf("id" to a.id, "name" to a.name, "email" to a.email, "username" to a.username,
                "password" to a.password, "imapHost" to a.imapHost, "imapPort" to a.imapPort.toString(),
                "imapSecurity" to a.imapSecurity.name, "smtpHost" to a.smtpHost,
                "smtpPort" to a.smtpPort.toString(), "smtpSecurity" to a.smtpSecurity.name).forEach { (k,v) ->
                p.setProperty("$i.$k", v)
            }
        }
        write(accountsFile, p)
    }
    private fun draftFile(id: String): Path {
        java.util.UUID.fromString(id)
        return directory.resolve("draft-$id.bin")
    }
    @Synchronized fun loadDraft(id: String): ComposeDraft {
        val p = read(draftFile(id))
        val count = p.getProperty("files", "0").toInt().also { require(it in 0..20) { "草稿附件记录异常" } }
        return ComposeDraft(p.getProperty("to", ""), p.getProperty("cc", ""), p.getProperty("subject", ""),
            p.getProperty("body", ""), (0 until count)
                .map { Path.of(p.getProperty("file.$it")) }, p.getProperty("replyId"))
    }
    @Synchronized fun saveDraft(id: String, draft: ComposeDraft) {
        require(draft.files.size <= 20 && draft.body.length <= 1_000_000) { "草稿过大：最多 20 个附件，正文最多 100 万字符" }
        val p = Properties()
        mapOf("to" to draft.to, "cc" to draft.cc, "subject" to draft.subject, "body" to draft.body,
            "files" to draft.files.size.toString()).forEach { (k,v) -> p.setProperty(k,v) }
        draft.replyId?.let { p.setProperty("replyId", it) }
        draft.files.forEachIndexed { i, f -> p.setProperty("file.$i", f.toString()) }
        write(draftFile(id), p)
    }
    @Synchronized fun deleteDraft(id: String) { Files.deleteIfExists(draftFile(id)) }
    private fun read(path: Path): Properties {
        if (!Files.exists(path)) return Properties()
        require(Files.size(path) <= 4_000_000) { "本地配置文件异常" }
        val bytes = protector.decrypt(Files.readAllBytes(path))
        return try { Properties().also { it.loadFromXML(ByteArrayInputStream(bytes)) } } finally { bytes.fill(0) }
    }
    private fun write(path: Path, properties: Properties) {
        Files.createDirectories(directory)
        val bytes = ByteArrayOutputStream().use { properties.storeToXML(it, null, "UTF-8"); it.toByteArray() }
        val encrypted = try {
            require(bytes.size <= 4_000_000) { "本地配置或草稿过大，请删减内容后重试" }
            protector.encrypt(bytes)
        } finally { bytes.fill(0) }
        val temp = Files.createTempFile(directory, "vault-", ".tmp")
        try {
            Files.write(temp, encrypted)
            Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temp) }
    }
}
