package com.kemi.windows

import jakarta.mail.Session
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import jakarta.mail.internet.MimeUtility
import java.util.Properties
import kotlin.test.*

class MailEncodingTest {
    @Test fun `Chinese MIME encoded filename is decoded before sanitizing`() {
        val part = MimeBodyPart().apply {
            setText("synthetic attachment")
            fileName = MimeUtility.encodeText("月度评优推荐表.xlsx","GB2312","B")
        }
        val message = MimeMessage(Session.getInstance(Properties())).apply {
            setContent(MimeMultipart().apply { addBodyPart(part) }); saveChanges()
        }
        assertEquals("月度评优推荐表.xlsx",parseContent(message).second.single().name)
    }
    @Test fun `declared Chinese charsets and MIME transfer encoding round trip`() {
        for (encoding in listOf("GB2312","GBK","GB18030","Big5","UTF-8")) {
            val text = if (encoding == "Big5") "中文郵件測試" else "中文邮件测试"
            val raw = "MIME-Version: 1.0\r\nContent-Type: text/plain; charset=$encoding\r\nContent-Transfer-Encoding: base64\r\n\r\n" +
                java.util.Base64.getEncoder().encodeToString(text.toByteArray(charset(encoding)))
            val expected = text
            val message = MimeMessage(Session.getInstance(Properties()),raw.byteInputStream())
            assertEquals(expected,parseContent(message).first)
        }
    }
    @Test fun `HTML meta charset is honored when MIME charset is missing`() {
        val source = "<html><head><meta charset=GB2312></head><body>中文邮件</body></html>"
        assertTrue(decodeMailText(source.toByteArray(charset("GB2312")),null,true).contains("中文邮件"))
        assertFailsWith<MailFailure> { decodeMailText(byteArrayOf(1),"nonexistent-charset",false) }
    }
    @Test fun `opening a message reads attachment metadata without touching attachment stream`() {
        val attachment = object : MimeBodyPart() {
            override fun getInputStream(): java.io.InputStream = error("Attachment must not download during reading")
            override fun getSize() = 1024
        }.apply { fileName = "report.txt"; disposition = "attachment" }
        val message = MimeMessage(Session.getInstance(Properties())).apply {
            val multipart = MimeMultipart().apply {
                addBodyPart(MimeBodyPart().apply { setText("正文","UTF-8") }); addBodyPart(attachment)
            }
            setContent(multipart)
            setHeader("Content-Type",multipart.contentType)
        }
        val result = parseContent(message,loadAttachments = false)
        assertTrue(result.first.contains("正文"))
        assertEquals(listOf(1),result.second.single().partPath)
        assertEquals(0,result.second.single().bytes.size)
        assertEquals(1024,result.second.single().encodedSize)
    }
}
