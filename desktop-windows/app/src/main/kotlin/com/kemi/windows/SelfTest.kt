package com.kemi.windows

import java.nio.file.Files
import java.nio.file.Path

/** Explicit diagnostic mode uses a caller-selected temporary directory and no real accounts or network. */
internal fun runSelfTest(directory: Path) {
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
    renderPreview(directory)
    Files.writeString(directory.resolve("result.txt"),"PASS: Windows DPAPI account/draft round trip, encrypted disk, MIME, Compose rendering, bundled runtime")
}
