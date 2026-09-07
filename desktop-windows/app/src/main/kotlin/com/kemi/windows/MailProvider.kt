package com.kemi.windows

/** Alibaba settings match Android BuiltInProviderSettings and the provider's TLS configuration. */
internal enum class MailProvider(val label: String,val subtitle: String,val imapHost: String,val smtpHost: String) {
    ALIBABA("阿里企业邮箱","企业域名 · 安全连接","imap.qiye.aliyun.com","smtp.qiye.aliyun.com"),
    QQ("QQ 邮箱","qq.com","imap.qq.com","smtp.qq.com"),
    NETEASE163("网易 163","163.com","imap.163.com","smtp.163.com"),
    NETEASE126("网易 126","126.com","imap.126.com","smtp.126.com");

    fun applyTo(account: Account): Account = account.copy(
        imapHost = imapHost,smtpHost = smtpHost,imapPort = 993,smtpPort = 465,
        imapSecurity = Security.TLS,smtpSecurity = Security.TLS,
        username = if (this == ALIBABA || account.username.isBlank()) account.email.trim() else account.username,
    )

    fun matches(account: Account,imapPort: String,smtpPort: String): Boolean =
        account.imapHost.equals(imapHost,true) && account.smtpHost.equals(smtpHost,true) &&
            imapPort == "993" && smtpPort == "465" &&
            account.imapSecurity == Security.TLS && account.smtpSecurity == Security.TLS
}

internal enum class MailFilter(val label: String) { ALL("全部"), UNREAD("未读"), STARRED("星标") }

internal fun filterMessages(messages: List<MailSummary>,query: String,filter: MailFilter): List<MailSummary> =
    messages.filter { message ->
        (when (filter) { MailFilter.ALL -> true; MailFilter.UNREAD -> !message.seen; MailFilter.STARRED -> message.starred }) &&
            (query.isBlank() || message.subject.contains(query.trim(),true) || message.sender.contains(query.trim(),true))
    }
