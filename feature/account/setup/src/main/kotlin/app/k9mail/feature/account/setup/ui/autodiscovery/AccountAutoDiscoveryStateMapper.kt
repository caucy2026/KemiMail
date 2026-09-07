package app.k9mail.feature.account.setup.ui.autodiscovery

import app.k9mail.autodiscovery.api.AuthenticationType
import app.k9mail.autodiscovery.api.ImapServerSettings
import app.k9mail.autodiscovery.api.IncomingServerSettings
import app.k9mail.autodiscovery.api.OutgoingServerSettings
import app.k9mail.autodiscovery.api.SmtpServerSettings
import app.k9mail.feature.account.common.domain.entity.AccountState
import app.k9mail.feature.account.server.settings.ui.incoming.IncomingServerSettingsContract
import app.k9mail.feature.account.server.settings.ui.outgoing.OutgoingServerSettingsContract
import app.k9mail.feature.account.setup.domain.entity.toAuthenticationType
import app.k9mail.feature.account.setup.domain.entity.toConnectionSecurity
import app.k9mail.feature.account.setup.domain.entity.toIncomingProtocolType
import app.k9mail.feature.account.setup.domain.toServerSettings
import app.k9mail.feature.account.setup.ui.options.display.DisplayOptionsContract
import net.thunderbird.core.validation.input.NumberInputField
import net.thunderbird.core.validation.input.StringInputField

internal fun AccountAutoDiscoveryContract.State.toAccountState(): AccountState {
    val usePasswordAuthentication = configStep == AccountAutoDiscoveryContract.ConfigStep.PASSWORD ||
        configStep == AccountAutoDiscoveryContract.ConfigStep.REMOTE_PASSWORD
    return AccountState(
        emailAddress = emailAddress.value,
        incomingServerSettings = autoDiscoverySettings?.incomingServerSettings
            ?.selectAuthenticationType(usePasswordAuthentication)
            ?.toServerSettings(password.value),
        outgoingServerSettings = autoDiscoverySettings?.outgoingServerSettings
            ?.selectAuthenticationType(usePasswordAuthentication)
            ?.toServerSettings(password.value),
        authorizationState = authorizationState,
        displayOptions = null,
        syncOptions = null,
    )
}

internal fun AccountAutoDiscoveryContract.State.toIncomingConfigState(): IncomingServerSettingsContract.State {
    val usePasswordAuthentication = configStep == AccountAutoDiscoveryContract.ConfigStep.PASSWORD ||
        configStep == AccountAutoDiscoveryContract.ConfigStep.REMOTE_PASSWORD
    val incomingSettings = (autoDiscoverySettings?.incomingServerSettings as? ImapServerSettings?)
        ?.selectAuthenticationType(usePasswordAuthentication)
    return if (incomingSettings == null) {
        IncomingServerSettingsContract.State(
            username = StringInputField(value = emailAddress.value),
            password = StringInputField(value = password.value),
        )
    } else {
        IncomingServerSettingsContract.State(
            protocolType = incomingSettings.toIncomingProtocolType(),
            server = StringInputField(value = incomingSettings.hostname.value),
            security = incomingSettings.connectionSecurity.toConnectionSecurity(),
            port = NumberInputField(value = incomingSettings.port.value.toLong()),
            authenticationType = incomingSettings.authenticationTypes.first().toAuthenticationType(),
            username = StringInputField(value = incomingSettings.username),
            password = StringInputField(value = password.value),
            imapAutodetectNamespaceEnabled = true,
            imapPrefix = StringInputField(value = ""),
            imapUseCompression = true,
            imapSendClientInfo = true,
        )
    }
}

internal fun AccountAutoDiscoveryContract.State.toOutgoingConfigState(): OutgoingServerSettingsContract.State {
    val usePasswordAuthentication = configStep == AccountAutoDiscoveryContract.ConfigStep.PASSWORD ||
        configStep == AccountAutoDiscoveryContract.ConfigStep.REMOTE_PASSWORD
    val outgoingSettings = (autoDiscoverySettings?.outgoingServerSettings as? SmtpServerSettings?)
        ?.selectAuthenticationType(usePasswordAuthentication)
    return if (outgoingSettings == null) {
        OutgoingServerSettingsContract.State(
            username = StringInputField(value = emailAddress.value),
            password = StringInputField(value = password.value),
        )
    } else {
        OutgoingServerSettingsContract.State(
            server = StringInputField(value = outgoingSettings.hostname.value),
            security = outgoingSettings.connectionSecurity.toConnectionSecurity(),
            port = NumberInputField(value = outgoingSettings.port.value.toLong()),
            authenticationType = outgoingSettings.authenticationTypes.first().toAuthenticationType(),
            username = StringInputField(value = outgoingSettings.username),
            password = StringInputField(value = password.value),
        )
    }
}

internal fun AccountAutoDiscoveryContract.State.toOptionsState(): DisplayOptionsContract.State {
    return DisplayOptionsContract.State(
        accountName = StringInputField(value = emailAddress.value),
    )
}

private fun IncomingServerSettings.selectAuthenticationType(
    usePasswordAuthentication: Boolean,
): IncomingServerSettings {
    return if (this is ImapServerSettings) {
        selectAuthenticationType(usePasswordAuthentication)
    } else {
        this
    }
}

private fun OutgoingServerSettings.selectAuthenticationType(
    usePasswordAuthentication: Boolean,
): OutgoingServerSettings {
    return if (this is SmtpServerSettings) {
        selectAuthenticationType(usePasswordAuthentication)
    } else {
        this
    }
}

private fun ImapServerSettings.selectAuthenticationType(usePasswordAuthentication: Boolean): ImapServerSettings {
    return copy(authenticationTypes = listOf(authenticationTypes.selectAuthenticationType(usePasswordAuthentication)))
}

private fun SmtpServerSettings.selectAuthenticationType(usePasswordAuthentication: Boolean): SmtpServerSettings {
    return copy(authenticationTypes = listOf(authenticationTypes.selectAuthenticationType(usePasswordAuthentication)))
}

private fun List<AuthenticationType>.selectAuthenticationType(usePasswordAuthentication: Boolean): AuthenticationType {
    if (!usePasswordAuthentication) return first()
    return firstOrNull { it != AuthenticationType.OAuth2 } ?: first()
}
