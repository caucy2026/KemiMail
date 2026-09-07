package app.k9mail.autodiscovery.autoconfig

import app.k9mail.autodiscovery.api.AuthenticationType.PasswordCleartext
import app.k9mail.autodiscovery.api.AutoDiscoveryResult
import app.k9mail.autodiscovery.api.ConnectionSecurity.TLS
import app.k9mail.autodiscovery.api.ImapServerSettings
import app.k9mail.autodiscovery.api.SmtpServerSettings
import net.thunderbird.core.common.mail.EmailAddress
import net.thunderbird.core.common.net.Domain
import net.thunderbird.core.common.net.toDomain
import net.thunderbird.core.common.net.toHostname
import net.thunderbird.core.common.net.toPort

internal object BuiltInProviderSettings {
    private val alibabaMailMxDomains = setOf(
        "qiye.aliyun.com".toDomain(),
        "mxhichina.com".toDomain(),
    )

    fun find(
        mxHostNames: List<Domain>,
        email: EmailAddress,
        isMxLookupTrusted: Boolean,
    ): AutoDiscoveryResult.Settings? {
        if (mxHostNames.none(::isAlibabaMailMxHost)) return null

        return AutoDiscoveryResult.Settings(
            incomingServerSettings = ImapServerSettings(
                hostname = "imap.qiye.aliyun.com".toHostname(),
                port = 993.toPort(),
                connectionSecurity = TLS,
                authenticationTypes = listOf(PasswordCleartext),
                username = email.address,
            ),
            outgoingServerSettings = SmtpServerSettings(
                hostname = "smtp.qiye.aliyun.com".toHostname(),
                port = 465.toPort(),
                connectionSecurity = TLS,
                authenticationTypes = listOf(PasswordCleartext),
                username = email.address,
            ),
            isTrusted = isMxLookupTrusted,
            source = ALIBABA_MAIL_SETTINGS_SOURCE,
        )
    }

    private fun isAlibabaMailMxHost(mxHostName: Domain): Boolean {
        return alibabaMailMxDomains.any { providerDomain ->
            mxHostName == providerDomain || mxHostName.value.endsWith(".${providerDomain.value}")
        }
    }

    private const val ALIBABA_MAIL_SETTINGS_SOURCE = "https://help.aliyun.com/zh/document_detail/36576.html"
}
