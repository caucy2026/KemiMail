package com.kemi.windows

import jakarta.mail.*
import jakarta.mail.internet.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.angus.mail.imap.IMAPFolder
import org.eclipse.angus.mail.imap.IMAPStore
import org.jsoup.Jsoup
import java.util.Date
import java.util.Properties
import java.io.ByteArrayOutputStream

class AngusMailGateway : MailGateway {
    internal fun properties(account: Account) = Properties().apply {
        for ((protocol, security) in listOf("imap" to account.imapSecurity, "smtp" to account.smtpSecurity)) {
            setProperty("mail.$protocol.ssl.enable", (security == Security.TLS).toString())
            setProperty("mail.$protocol.starttls.enable", (security == Security.STARTTLS).toString())
            setProperty("mail.$protocol.starttls.required", (security == Security.STARTTLS).toString())
            setProperty("mail.$protocol.ssl.checkserveridentity", "true")
            setProperty("mail.$protocol.ssl.protocols", "TLSv1.2 TLSv1.3")
            setProperty("mail.$protocol.connectiontimeout", "15000")
            setProperty("mail.$protocol.timeout", "30000")
            setProperty("mail.$protocol.writetimeout", "30000")
        }
        setProperty("mail.smtp.auth", "true")
        setProperty("mail.smtp.sendpartial", "false")
        setProperty("mail.imap.peek", "true")
        setProperty("mail.imap.connectionpoolsize", "1")
        setProperty("mail.debug", "false")
    }
    private fun session(account: Account) = Session.getInstance(properties(account))
    private fun <T> connected(account: Account, block: (IMAPStore) -> T): T {
        account.validate()
        val store = session(account).getStore("imap") as IMAPStore
        try {
            store.connect(account.imapHost, account.imapPort, account.username, account.password)
            // QQ IMAP requires an ID command, containing product identity only.
            if (store.hasCapability("ID")) store.id(mapOf("name" to "KemiMail", "version" to "1.0.0"))
            return block(store)
        } finally { runCatching { store.close() } }
    }
    private fun <T> inFolder(store: IMAPStore, path: String, writable: Boolean = false, block: (IMAPFolder) -> T): T {
        val folder = store.getFolder(path) as IMAPFolder
        folder.open(if (writable) Folder.READ_WRITE else Folder.READ_ONLY)
        try { return block(folder) } finally { if (folder.isOpen) runCatching { folder.close(false) } }
    }
    private fun find(folder: IMAPFolder, mail: MailSummary): Message {
        if (folder.uidValidity != mail.validity) throw MailFailure("邮箱文件夹已变化，请刷新列表后重试")
        return folder.getMessageByUID(mail.uid) ?: throw MailFailure("邮件已移动或删除，请刷新列表")
    }
    override suspend fun check(account: Account) = withContext(Dispatchers.IO) {
        connected(account) { Unit }
        val transport = session(account).getTransport("smtp")
        try { transport.connect(account.smtpHost, account.smtpPort, account.username, account.password) }
        finally { runCatching { transport.close() } }
    }
    override suspend fun folders(account: Account): List<MailFolder> = withContext(Dispatchers.IO) {
        connected(account) { store ->
            store.defaultFolder.list("*").take(500).filter { it.type and Folder.HOLDS_MESSAGES != 0 }.map { folder ->
                val attributes = (folder as IMAPFolder).attributes.toList()
                val label = when {
                    folder.fullName.equals("INBOX", true) -> "收件箱"
                    attributes.any { it.equals("\\Sent", true) } -> "已发送"
                    attributes.any { it.equals("\\Trash", true) } -> "已删除"
                    attributes.any { it.equals("\\Drafts", true) } -> "草稿"
                    attributes.any { it.equals("\\Junk", true) } -> "垃圾邮件"
                    else -> folder.name
                }
                MailFolder(folder.fullName, label,
                    attributes.any { it.equals("\\Trash", true) } || folder.name.equals("Trash", true),
                    attributes.any { it.equals("\\Sent", true) } || folder.name.equals("Sent", true))
            }.sortedBy { if (it.path.equals("INBOX", true)) "" else it.label }
        }
    }
    override suspend fun list(account: Account, folder: String, limit: Int): List<MailSummary> = withContext(Dispatchers.IO) {
        connected(account) { store -> inFolder(store, folder) { f ->
            val count = f.messageCount
            if (count == 0) emptyList() else {
                val messages = f.getMessages((count - limit.coerceIn(1,2000) + 1).coerceAtLeast(1), count)
                f.fetch(messages, FetchProfile().apply {
                    add(FetchProfile.Item.ENVELOPE); add(FetchProfile.Item.FLAGS); add(UIDFolder.FetchProfileItem.UID)
                })
                messages.reversed().map { m -> MailSummary(f.getUID(m), f.uidValidity,
                    m.subject ?: "（无主题）", m.from?.joinToString { displayAddress(it) } ?: "（未知发件人）",
                    (m.sentDate ?: m.receivedDate)?.toInstant(), m.isSet(Flags.Flag.SEEN), m.isSet(Flags.Flag.FLAGGED)) }
            }
        } }
    }
    override suspend fun read(account: Account, folder: String, mail: MailSummary): MailDetail = withContext(Dispatchers.IO) {
        connected(account) { store -> inFolder(store, folder) { f ->
            val m = find(f, mail)
            if (m.size > MAX_MESSAGE_BYTES) throw MailFailure("邮件超过 20 MB，请使用网页版查看")
            val parsed = parseContent(m)
            MailDetail(mail, m.getRecipients(Message.RecipientType.TO)?.joinToString { displayAddress(it) } ?: "",
                m.replyTo?.joinToString { (it as? InternetAddress)?.address ?: it.toString() } ?: "",
                m.getHeader("Message-ID")?.firstOrNull(), parsed.first, parsed.second)
        } }
    }
    override suspend fun flag(account: Account, folder: String, mail: MailSummary, seen: Boolean?, starred: Boolean?) =
        withContext(Dispatchers.IO) {
            connected(account) { store -> inFolder(store, folder, true) { f ->
                val m = find(f, mail)
                seen?.let { m.setFlag(Flags.Flag.SEEN, it) }
                starred?.let { m.setFlag(Flags.Flag.FLAGGED, it) }
                Unit
            } }
        }
    override suspend fun trash(account: Account, folder: String, mail: MailSummary, destination: String) =
        withContext(Dispatchers.IO) {
            connected(account) { store ->
                if (!store.hasCapability("MOVE")) throw MailFailure("此服务器不支持安全移动，请在网页版删除邮件")
                require(folder != destination)
                inFolder(store, folder, true) { f -> f.moveMessages(arrayOf(find(f, mail)), store.getFolder(destination)) }
            }
        }
    override suspend fun send(account: Account, draft: ComposeDraft): SendResult = withContext(Dispatchers.IO) {
        draft.validate(); account.validate()
        val mailSession = session(account)
        val message = buildMessage(mailSession, account, draft)
        val transport = mailSession.getTransport("smtp")
        try {
            transport.connect(account.smtpHost, account.smtpPort, account.username, account.password)
            try { transport.sendMessage(message, message.allRecipients) }
            catch (_: MessagingException) {
                throw MailFailure("未能确认发送结果。请先检查已发送邮件或网页版，确认未发送后再重试，避免重复发送。")
            }
        } finally { runCatching { transport.close() } }
        // Delivery has been accepted. A failure here must never turn into a retryable send failure.
        val saved = runCatching {
            connected(account) { store ->
                val sent = store.defaultFolder.list("*").filterIsInstance<IMAPFolder>().firstOrNull {
                    it.attributes.any { attribute -> attribute.equals("\\Sent", true) } || it.name.equals("Sent", true)
                } ?: return@connected false
                message.setFlag(Flags.Flag.SEEN, true)
                sent.appendMessages(arrayOf(message)); true
            }
        }.getOrDefault(false)
        SendResult(saved)
    }
}

internal fun displayAddress(address: Address): String = (address as? InternetAddress)?.toUnicodeString() ?: address.toString()
internal fun buildMessage(session: Session, account: Account, draft: ComposeDraft): MimeMessage {
    draft.validate()
    return MimeMessage(session).apply {
        setFrom(InternetAddress(account.email, account.name.ifBlank { account.email }, "UTF-8"))
        setRecipients(Message.RecipientType.TO, addresses(draft.to).toTypedArray())
        if (draft.cc.isNotBlank()) setRecipients(Message.RecipientType.CC, addresses(draft.cc).toTypedArray())
        setSubject(draft.subject, "UTF-8"); sentDate = Date()
        draft.replyId?.takeIf { it.length < 998 && !it.contains('\r') && !it.contains('\n') }?.let {
            setHeader("In-Reply-To", it); setHeader("References", it)
        }
        if (draft.files.isEmpty()) setText(draft.body, "UTF-8") else {
            setContent(MimeMultipart().apply {
                addBodyPart(MimeBodyPart().apply { setText(draft.body, "UTF-8") })
                draft.files.forEach { path -> addBodyPart(MimeBodyPart().apply {
                    attachFile(path.toFile()); fileName = MimeUtility.encodeText(safeAttachmentName(path.fileName.toString()), "UTF-8", null)
                }) }
            })
        }
        saveChanges()
    }
}

/** No HTML renderer, remote URLs, scripts, or automatic attachment execution. */
internal fun parseContent(root: Part): Pair<String,List<Attachment>> {
    val attachments = mutableListOf<Attachment>()
    var consumed = 0L
    var parts = 0
    fun bytes(part: Part): ByteArray {
        val output = ByteArrayOutputStream()
        part.inputStream.use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer); if (count < 0) break
                consumed += count
                if (consumed > MAX_MESSAGE_BYTES) throw MailFailure("邮件解码后超过 20 MB，请使用网页版查看")
                output.write(buffer,0,count)
            }
        }
        return output.toByteArray()
    }
    fun visit(part: Part, depth: Int): String {
        if (depth > 20 || ++parts > 200) throw MailFailure("邮件结构过于复杂，请使用网页版查看")
        if (part.disposition.equals(Part.ATTACHMENT,true) || part.fileName != null) {
            attachments.add(Attachment(safeAttachmentName(part.fileName ?: "attachment"), bytes(part)))
            return ""
        }
        if (part.isMimeType("multipart/*")) {
            val multi = part.content as Multipart
            if (multi.count > 200) throw MailFailure("邮件包含过多内容片段")
            if (part.isMimeType("multipart/alternative")) {
                val preferred = (0 until multi.count).map { multi.getBodyPart(it) }
                    .firstOrNull { it.isMimeType("text/plain") }
                if (preferred != null) return visit(preferred,depth+1)
            }
            return (0 until multi.count).joinToString("\n") { visit(multi.getBodyPart(it),depth+1) }
        }
        if (part.isMimeType("text/*")) {
            val charset = runCatching { ContentType(part.contentType).getParameter("charset")?.let { charset(it) } }
                .getOrNull() ?: Charsets.UTF_8
            val text = bytes(part).toString(charset)
            return if (part.isMimeType("text/html")) Jsoup.parse(text).apply {
                select("script,style,iframe,object,embed").remove()
                select("br").append("\n"); select("p,div,tr,li").prepend("\n")
            }.wholeText() else text
        }
        return ""
    }
    return visit(root,0).ifBlank { "（此邮件没有可显示的文本正文）" } to attachments
}
