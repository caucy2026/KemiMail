package com.kemi.windows

import com.icegreen.greenmail.util.GreenMail
import com.icegreen.greenmail.util.ServerSetup
import kotlinx.coroutines.runBlocking
import jakarta.mail.Session
import jakarta.mail.Folder
import jakarta.mail.AuthenticationFailedException
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyStore
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import kotlin.test.*
import assertk.assertThat
import assertk.assertions.*

/** Real sockets on loopback, throwaway TLS identity and synthetic mail; never contacts a real provider. */
class MailIntegrationTest {
    @Test fun `TLS completes mail workflow and insecure STARTTLS downgrade is rejected`() = runBlocking {
        val dir = Files.createTempDirectory("mail-tls-test-")
        val originalContext = SSLContext.getDefault()
        val storePath = dir.resolve("fixture.p12")
        val keytool = Path.of(System.getProperty("java.home"),"bin",if (System.getProperty("os.name").startsWith("Windows")) "keytool.exe" else "keytool")
        val process = ProcessBuilder(keytool.toString(),"-genkeypair","-alias","localhost","-keyalg","RSA","-keysize","2048",
            "-dname","CN=localhost","-ext","SAN=dns:localhost","-validity","2","-storetype","PKCS12",
            "-keystore",storePath.toString(),"-storepass","fixture-only","-keypass","fixture-only").redirectErrorStream(true).start()
        process.inputStream.readAllBytes()
        check(process.waitFor() == 0)
        val keystore = KeyStore.getInstance("PKCS12").apply {
            Files.newInputStream(storePath).use { load(it,"fixture-only".toCharArray()) }
        }
        val tm = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(keystore) }
        SSLContext.setDefault(SSLContext.getInstance("TLS").apply { init(null,tm.trustManagers,null) })
        System.setProperty("greenmail.tls.keystore.file",storePath.toString())
        System.setProperty("greenmail.tls.keystore.password","fixture-only")
        try {
            for (security in Security.entries) {
                val server = GreenMail(arrayOf(
                    ServerSetup(0,"127.0.0.1",if (security == Security.TLS) "imaps" else "imap"),
                    ServerSetup(0,"127.0.0.1",if (security == Security.TLS) "smtps" else "smtp"),
                ))
                server.setUser("fixture@example.invalid","fixture","fixture-only")
                server.start()
                try {
                    val account = Account(name = "Synthetic",email = "fixture@example.invalid",username = "fixture",password = "fixture-only",
                        imapHost = "localhost",imapPort = (if (security == Security.TLS) server.imaps else server.imap).port,
                        imapSecurity = security,smtpHost = "localhost",smtpPort = (if (security == Security.TLS) server.smtps else server.smtp).port,
                        smtpSecurity = security)
                    val testSubject = AngusMailGateway()
                    if (security == Security.STARTTLS) {
                        // GreenMail IMAP does not advertise STARTTLS. This is a downgrade refusal test.
                        val failure = assertFailsWith<jakarta.mail.MessagingException> { testSubject.check(account) }
                        assertThat(failure.message ?: "").contains("STARTTLS required")
                        continue
                    }
                    testSubject.check(account)
                    assertFailsWith<AuthenticationFailedException> { testSubject.check(account.copy(password = "wrong-fixture")) }
                    // Same trusted certificate, wrong hostname: production validation must reject it.
                    assertFails { testSubject.check(account.copy(imapHost = "127.0.0.1")) }
                    val store = Session.getInstance(testSubject.properties(account)).getStore("imap")
                    store.connect(account.imapHost,account.imapPort,account.username,account.password)
                    store.getFolder("Sent").create(Folder.HOLDS_MESSAGES); store.getFolder("Trash").create(Folder.HOLDS_MESSAGES); store.close()
                    val result = testSubject.send(account,ComposeDraft(to = account.email,subject = "协议测试",body = "Synthetic TLS content"))
                    assertThat(result.savedToSent).isTrue()
                    assertThat(testSubject.folders(account).map { it.label }).contains("收件箱")
                    val established = testSubject.connectionCount
                    val messages = testSubject.list(account,"INBOX",100)
                    assertThat(messages).hasSize(1)
                    val message = messages.single()
                    assertThat(message.subject).isEqualTo("协议测试")
                    assertThat(testSubject.read(account,"INBOX",message).body).contains("Synthetic TLS content")
                    assertEquals(established,testSubject.connectionCount,"list and read must reuse the authenticated connection")
                    assertThat(testSubject.list(account,"INBOX",100).single().seen).isFalse()
                    val attachmentFile = Files.createTempFile(dir,"synthetic-", ".txt")
                    Files.writeString(attachmentFile,"附件内容".repeat(10000))
                    testSubject.send(account,ComposeDraft(to = account.email,subject = "Attachment",body = "正文",files = listOf(attachmentFile)))
                    val withAttachment = testSubject.list(account,"INBOX",100).first()
                    val detail = testSubject.read(account,"INBOX",withAttachment)
                    assertTrue(detail.body.contains("正文"))
                    assertEquals(0,detail.attachments.single().bytes.size)
                    assertContentEquals(Files.readAllBytes(attachmentFile),testSubject.attachment(account,"INBOX",withAttachment,detail.attachments.single()))
                    testSubject.flag(account,"INBOX",message,seen = true,starred = true)
                    val updated = testSubject.list(account,"INBOX",100).first { it.uid == message.uid }
                    assertThat(updated.seen).isTrue(); assertThat(updated.starred).isTrue()
                    assertFailsWith<MailFailure> { testSubject.read(account,"INBOX",message.copy(validity = message.validity + 1)) }
                    assertThat(testSubject.list(account,"Sent",100)).hasSize(2)
                    testSubject.disconnect()
                } finally { server.stop() }
            }
        } finally {
            SSLContext.setDefault(originalContext)
            System.clearProperty("greenmail.tls.keystore.file"); System.clearProperty("greenmail.tls.keystore.password")
            Files.walk(dir).use { stream -> stream.sorted(Comparator.reverseOrder()).forEach { Files.delete(it) } }
        }
    }
}
