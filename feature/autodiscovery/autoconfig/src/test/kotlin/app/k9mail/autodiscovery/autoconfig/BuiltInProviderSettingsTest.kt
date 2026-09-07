package app.k9mail.autodiscovery.autoconfig

import app.k9mail.autodiscovery.api.AuthenticationType.PasswordCleartext
import app.k9mail.autodiscovery.api.ConnectionSecurity.TLS
import app.k9mail.autodiscovery.api.ImapServerSettings
import app.k9mail.autodiscovery.api.SmtpServerSettings
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlin.test.Test
import net.thunderbird.core.common.mail.toUserEmailAddress
import net.thunderbird.core.common.net.toDomain
import net.thunderbird.core.common.net.toHostname
import net.thunderbird.core.common.net.toPort

class BuiltInProviderSettingsTest {
    private val email = "user@company.example".toUserEmailAddress()

    @Test
    fun `find returns Alibaba Mail settings for current MX domain`() {
        val settings = BuiltInProviderSettings.find(
            mxHostNames = listOf("mx1.qiye.aliyun.com".toDomain()),
            email = email,
            isMxLookupTrusted = true,
        )

        assertThat(settings?.incomingServerSettings).isEqualTo(
            ImapServerSettings(
                hostname = "imap.qiye.aliyun.com".toHostname(),
                port = 993.toPort(),
                connectionSecurity = TLS,
                authenticationTypes = listOf(PasswordCleartext),
                username = email.address,
            ),
        )
        assertThat(settings?.outgoingServerSettings).isEqualTo(
            SmtpServerSettings(
                hostname = "smtp.qiye.aliyun.com".toHostname(),
                port = 465.toPort(),
                connectionSecurity = TLS,
                authenticationTypes = listOf(PasswordCleartext),
                username = email.address,
            ),
        )
        assertThat(settings?.isTrusted).isEqualTo(true)
    }

    @Test
    fun `find returns Alibaba Mail settings for legacy MX domain`() {
        val settings = BuiltInProviderSettings.find(
            mxHostNames = listOf("mxn.mxhichina.com".toDomain()),
            email = email,
            isMxLookupTrusted = true,
        )

        assertThat(settings?.incomingServerSettings).isEqualTo(
            ImapServerSettings(
                hostname = "imap.qiye.aliyun.com".toHostname(),
                port = 993.toPort(),
                connectionSecurity = TLS,
                authenticationTypes = listOf(PasswordCleartext),
                username = email.address,
            ),
        )
    }

    @Test
    fun `find checks all MX hosts`() {
        val settings = BuiltInProviderSettings.find(
            mxHostNames = listOf(
                "mx.unrelated.example".toDomain(),
                "mx2.qiye.aliyun.com".toDomain(),
            ),
            email = email,
            isMxLookupTrusted = true,
        )

        assertThat(settings?.outgoingServerSettings).isEqualTo(
            SmtpServerSettings(
                hostname = "smtp.qiye.aliyun.com".toHostname(),
                port = 465.toPort(),
                connectionSecurity = TLS,
                authenticationTypes = listOf(PasswordCleartext),
                username = email.address,
            ),
        )
    }

    @Test
    fun `find preserves MX lookup trust`() {
        val settings = BuiltInProviderSettings.find(
            mxHostNames = listOf("mxw.mxhichina.com".toDomain()),
            email = email,
            isMxLookupTrusted = false,
        )

        assertThat(settings?.isTrusted).isEqualTo(false)
    }

    @Test
    fun `find returns null for unrelated MX domain`() {
        val settings = BuiltInProviderSettings.find(
            mxHostNames = listOf("mx.mail.example".toDomain()),
            email = email,
            isMxLookupTrusted = true,
        )

        assertThat(settings).isNull()
    }
}
