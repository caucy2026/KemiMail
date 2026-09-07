package app.k9mail.feature.account.oauth.domain.usecase

import app.k9mail.feature.account.oauth.domain.AccountOAuthDomainContract
import app.k9mail.feature.account.oauth.domain.AccountOAuthDomainContract.UseCase.PollDeviceAuthorization
import app.k9mail.feature.account.oauth.domain.entity.DeviceAuthorizationSession
import app.k9mail.feature.account.oauth.domain.entity.PollDeviceAuthorizationResult

internal class PollDeviceAuthorization(
    private val repository: AccountOAuthDomainContract.DeviceAuthorizationRepository,
) : PollDeviceAuthorization {
    override suspend fun execute(session: DeviceAuthorizationSession): PollDeviceAuthorizationResult {
        return repository.poll(session)
    }
}
