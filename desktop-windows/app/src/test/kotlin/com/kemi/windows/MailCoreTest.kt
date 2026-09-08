package com.kemi.windows

import assertk.assertThat
import assertk.assertions.*
import jakarta.mail.Session
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import jakarta.mail.internet.MimeBodyPart
import java.util.Properties
import java.nio.file.Files
import kotlin.test.*

class MailCoreTest {
    private fun account() = Account(email = "user@example.invalid",username = "user",password = "test-only",
        imapHost = "imap.example.invalid",smtpHost = "smtp.example.invalid")
    @Test fun `IMAP client identity is printable ASCII independent of localized application name`() {
        assertTrue(imapClientIdentity.all { (key,value) -> (key + value).all { it.code in 32..126 } })
        assertEquals("KemiMail",imapClientIdentity["name"])
    }
    @Test fun `address parser rejects header injection and missing domain`() {
        assertFailsWith<IllegalArgumentException> { addresses("a@example.invalid\r\nBcc: other@example.invalid") }
        assertFailsWith<IllegalArgumentException> { addresses("invalid") }
        assertThat(addresses("A <a@example.invalid>; b@example.invalid")).hasSize(2)
    }
    @Test fun `TLS is mandatory and hostname verification cannot be disabled by settings`() {
        val testSubject = AngusMailGateway()
        for (security in Security.entries) {
            val p = testSubject.properties(account().copy(imapSecurity = security,smtpSecurity = security))
            for (protocol in listOf("imap","smtp")) {
                assertThat(p.getProperty("mail.$protocol.ssl.checkserveridentity")).isEqualTo("true")
                assertThat(p.getProperty("mail.$protocol.starttls.required")).isEqualTo((security == Security.STARTTLS).toString())
                assertThat(p.getProperty("mail.$protocol.ssl.enable")).isEqualTo((security == Security.TLS).toString())
            }
        }
    }
    @Test fun `account validates ports host and sender`() {
        account().validate()
        assertFailsWith<IllegalArgumentException> { account().copy(imapHost = "https://mail.example.invalid").validate() }
        assertFailsWith<IllegalArgumentException> { account().copy(smtpPort = 0).validate() }
        assertFailsWith<IllegalArgumentException> { account().copy(email = "a@example.invalid,b@example.invalid").validate() }
        assertThat(account().toString()).doesNotContain("test-only")
    }
    @Test fun `safe filename removes path traversal reserved characters and devices`() {
        assertThat(safeAttachmentName("../../a.txt")).isEqualTo("a.txt")
        assertThat(safeAttachmentName("C:\\secret\\a.txt")).isEqualTo("a.txt")
        assertThat(safeAttachmentName("CON.txt")).isEqualTo("_CON.txt")
        assertThat(safeAttachmentName(".. ")).isEqualTo("attachment")
        assertThat(safeAttachmentName("a:b?.txt")).isEqualTo("a_b_.txt")
    }
    @Test fun `HTML mail is text without script or remote image content`() {
        val message = MimeMessage(Session.getInstance(Properties())).apply {
            setContent("<p>Hello</p><script>SECRET_SCRIPT</script><img src='https://invalid.example/tracker'><p>World</p>","text/html; charset=utf-8")
        }
        message.saveChanges()
        val text = parseContent(message).first
        assertThat(text).contains("Hello"); assertThat(text).contains("World")
        assertThat(text).doesNotContain("SECRET_SCRIPT"); assertThat(text).doesNotContain("https://")
    }
    @Test fun `MIME alternative prefers plain text and retains attachment bytes`() {
        val message = MimeMessage(Session.getInstance(Properties()))
        val alt = MimeMultipart("alternative").apply {
            addBodyPart(MimeBodyPart().apply { setText("Plain 中文", "UTF-8") })
            addBodyPart(MimeBodyPart().apply { setContent("<b>HTML</b>","text/html") })
        }
        message.setContent(MimeMultipart().apply {
            addBodyPart(MimeBodyPart().apply { setContent(alt) })
            addBodyPart(MimeBodyPart().apply { setText("fixture"); fileName = "../../test.txt" })
        })
        message.saveChanges()
        val result = parseContent(message)
        assertThat(result.first).contains("Plain 中文"); assertThat(result.first).doesNotContain("HTML")
        assertThat(result.second.single().name).isEqualTo("test.txt")
        assertThat(result.second.single().bytes.toString(Charsets.UTF_8)).isEqualTo("fixture")
    }
    @Test fun `outbound message round trips Unicode recipient reply headers and attachment`() {
        val file = Files.createTempFile("mail-test-", ".txt")
        try {
            Files.writeString(file,"附件内容")
            val draft = ComposeDraft("a@example.invalid", "b@example.invalid","中文主题","正文",listOf(file),"<reply@example.invalid>")
            val message = buildMessage(Session.getInstance(Properties()),account(),draft)
            val bytes = java.io.ByteArrayOutputStream().also { message.writeTo(it) }.toByteArray()
            val parsed = MimeMessage(Session.getInstance(Properties()),bytes.inputStream())
            assertThat(parsed.subject).isEqualTo("中文主题")
            assertThat(parsed.allRecipients.toList()).hasSize(2)
            assertThat(parsed.getHeader("In-Reply-To",null)).isEqualTo("<reply@example.invalid>")
            val result = parseContent(parsed)
            assertThat(result.first).contains("正文")
            assertThat(result.second.single().bytes.toString(Charsets.UTF_8)).isEqualTo("附件内容")
        } finally { Files.delete(file) }
    }
    @Test fun `oversized decoded content is rejected`() {
        val message = MimeMessage(Session.getInstance(Properties())).apply { setText("x".repeat(MAX_MESSAGE_BYTES.toInt()+1)) }
        message.saveChanges()
        assertFailsWith<MailFailure> { parseContent(message) }
    }
    @Test fun `too many MIME parts are rejected`() {
        val message = MimeMessage(Session.getInstance(Properties())).apply { setContent(MimeMultipart().apply {
            repeat(201) { addBodyPart(MimeBodyPart().apply { setText("x") }) }
        }) }
        message.saveChanges()
        assertFailsWith<MailFailure> { parseContent(message) }
    }
}

class FakeProtector : SecretProtector {
    // Deliberately reversible test fake, never wired into production.
    override fun encrypt(bytes: ByteArray) = bytes.map { (it.toInt() xor 0x5a).toByte() }.toByteArray()
    override fun decrypt(bytes: ByteArray) = encrypt(bytes)
}
class AccountVaultTest {
    @Test fun `accounts and drafts survive round trip and corruption is not treated as empty`() {
        val dir = Files.createTempDirectory("vault-test-")
        try {
            val testSubject = AccountVault(dir,FakeProtector())
            val account = Account(name = "测试账号",email = "test@example.invalid",password = "test-fixture")
            testSubject.save(listOf(account)); assertThat(testSubject.load()).isEqualTo(listOf(account))
            val draft = ComposeDraft(to = "to@example.invalid",subject = "草稿",body = "正文")
            testSubject.saveDraft(account.id,draft); assertThat(testSubject.loadDraft(account.id)).isEqualTo(draft)
            testSubject.deleteDraft(account.id); assertThat(testSubject.loadDraft(account.id)).isEqualTo(ComposeDraft())
            Files.write(dir.resolve("accounts.bin"),byteArrayOf(1,2,3))
            assertFails { testSubject.load() }
        } finally { Files.walk(dir).sorted(Comparator.reverseOrder()).forEach { Files.delete(it) } }
    }
    @Test fun `oversized draft cannot replace the previous recoverable draft`() {
        val dir = Files.createTempDirectory("vault-test-")
        try {
            val testSubject = AccountVault(dir,FakeProtector())
            val id = java.util.UUID.randomUUID().toString()
            val previous = ComposeDraft(body = "Recoverable draft")
            testSubject.saveDraft(id,previous)
            assertFailsWith<IllegalArgumentException> {
                testSubject.saveDraft(id,ComposeDraft(body = "x".repeat(1_000_001)))
            }
            assertThat(testSubject.loadDraft(id)).isEqualTo(previous)
        } finally { Files.walk(dir).use { it.sorted(Comparator.reverseOrder()).forEach(Files::delete) } }
    }
    @Test fun `failed encryption preserves existing account file`() {
        val dir = Files.createTempDirectory("vault-test-")
        try {
            val baseline = byteArrayOf(5,6,7); Files.write(dir.resolve("accounts.bin"),baseline)
            val testSubject = AccountVault(dir,object : SecretProtector {
                override fun encrypt(bytes: ByteArray): ByteArray = error("synthetic failure")
                override fun decrypt(bytes: ByteArray) = bytes
            })
            assertFails { testSubject.save(emptyList()) }
            assertContentEquals(baseline,Files.readAllBytes(dir.resolve("accounts.bin")))
        } finally { Files.walk(dir).sorted(Comparator.reverseOrder()).forEach { Files.delete(it) } }
    }
}
