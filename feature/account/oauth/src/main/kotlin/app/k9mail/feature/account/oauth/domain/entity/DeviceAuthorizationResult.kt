package app.k9mail.feature.account.oauth.domain.entity

import app.k9mail.feature.account.common.domain.entity.AuthorizationState

sealed interface StartDeviceAuthorizationResult {
    data class Success(val session: DeviceAuthorizationSession) : StartDeviceAuthorizationResult
    data object NotSupported : StartDeviceAuthorizationResult
    data object Failure : StartDeviceAuthorizationResult
}

sealed interface PollDeviceAuthorizationResult {
    data object Pending : PollDeviceAuthorizationResult
    data object SlowDown : PollDeviceAuthorizationResult
    data object Declined : PollDeviceAuthorizationResult
    data object Expired : PollDeviceAuthorizationResult
    data object Failure : PollDeviceAuthorizationResult
    data class Success(val authorizationState: AuthorizationState) : PollDeviceAuthorizationResult
}
