package app.k9mail.feature.account.oauth.domain.entity

import net.thunderbird.core.common.oauth.OAuthConfiguration

data class DeviceAuthorizationSession(
    val configuration: OAuthConfiguration,
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val expiresAt: Long,
    val pollingIntervalSeconds: Long,
)
