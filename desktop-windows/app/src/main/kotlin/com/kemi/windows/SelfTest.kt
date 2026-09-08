package com.kemi.windows

import java.nio.file.Files
import java.nio.file.Path

/** Explicit diagnostic mode uses a caller-selected temporary directory and no real accounts or network. */
internal fun runSelfTest(directory: Path) {
    require(!Files.exists(directory) || Files.list(directory).use { !it.findAny().isPresent }) { "Self-test needs a new or empty directory" }
    Files.createDirectories(directory)
    val vault = AccountVault(directory,WindowsProtector())
    val account = Account(name = "Synthetic",email = "fixture@example.invalid",username = "fixture@example.invalid",
        password = "synthetic-not-a-real-secret",imapHost = "imap.example.invalid",smtpHost = "smtp.example.invalid")
    vault.save(listOf(account))
    check(vault.load() == listOf(account))
    val disk = Files.readAllBytes(directory.resolve("accounts.bin")).toString(Charsets.ISO_8859_1)
    check(!disk.contains(account.password) && !disk.contains(account.email))
    val draft = ComposeDraft(to = "recipient@example.invalid",subject = "Windows self test",body = "Synthetic draft")
    vault.saveDraft(account.id,draft); check(vault.loadDraft(account.id) == draft)
    vault.deleteDraft(account.id); check(vault.loadDraft(account.id) == ComposeDraft())
    val mail = buildMessage(jakarta.mail.Session.getInstance(java.util.Properties()),account,draft)
    check(parseContent(mail).first.trim() == "Synthetic draft")
    for (encoding in listOf("GB2312","GBK","GB18030","Big5","UTF-8")) {
        val text = if (encoding == "Big5") "中文郵件測試" else "中文邮件测试"
        val bytes = text.toByteArray(charset(encoding))
        check(decodeMailText(bytes,encoding,false) == text)
    }
    val encodedName = jakarta.mail.internet.MimeUtility.encodeText("月度评优推荐表.xlsx","GB2312","B")
    val attachment = jakarta.mail.internet.MimeBodyPart().apply { setText("synthetic"); fileName = encodedName }
    val encodedMail = jakarta.mail.internet.MimeMessage(jakarta.mail.Session.getInstance(java.util.Properties())).apply {
        setContent(jakarta.mail.internet.MimeMultipart().apply { addBodyPart(attachment) }); saveChanges()
    }
    check(parseContent(encodedMail).second.single().name == "月度评优推荐表.xlsx")
    val icon = checkNotNull(Thread.currentThread().contextClassLoader.getResourceAsStream("kemi-mail.png"))
        .use { javax.imageio.ImageIO.read(it) }
    check(icon.width == 512 && icon.height == 512)
    renderPreview(directory)
    Files.writeString(directory.resolve("result.txt"),"PASS: Windows DPAPI account/draft round trip, encrypted disk, MIME, GB2312/GBK/GB18030/Big5, decoded attachment name, Android icon, Compose rendering, bundled runtime")
}
