package app.k9mail.feature.account.oauth.domain.usecase

import app.k9mail.feature.account.oauth.domain.AccountOAuthDomainContract.UseCase.CheckIsDeviceAuthorizationSupported
import net.thunderbird.core.common.oauth.OAuthConfigurationProvider

internal class CheckIsDeviceAuthorizationSupported(
    private val configurationProvider: OAuthConfigurationProvider,
) : CheckIsDeviceAuthorizationSupported {
    override fun execute(hostname: String): Boolean {
        return configurationProvider.getConfiguration(hostname)?.deviceAuthorizationEndpoint != null
    }
}
