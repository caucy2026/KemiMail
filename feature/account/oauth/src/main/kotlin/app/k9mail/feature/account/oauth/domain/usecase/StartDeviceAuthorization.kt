package app.k9mail.feature.account.oauth.domain.usecase

import app.k9mail.feature.account.oauth.domain.AccountOAuthDomainContract
import app.k9mail.feature.account.oauth.domain.AccountOAuthDomainContract.UseCase.StartDeviceAuthorization
import app.k9mail.feature.account.oauth.domain.entity.StartDeviceAuthorizationResult
import net.thunderbird.core.common.oauth.OAuthConfigurationProvider

internal class StartDeviceAuthorization(
    private val repository: AccountOAuthDomainContract.DeviceAuthorizationRepository,
    private val configurationProvider: OAuthConfigurationProvider,
) : StartDeviceAuthorization {
    override suspend fun execute(hostname: String): StartDeviceAuthorizationResult {
        val configuration = configurationProvider.getConfiguration(hostname)
        return if (configuration?.deviceAuthorizationEndpoint == null) {
            StartDeviceAuthorizationResult.NotSupported
        } else {
            repository.start(configuration)
        }
    }
}
