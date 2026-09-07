package net.thunderbird.core.common.credentials

data class RemoteCredentialTransferConfiguration(
    val baseUrl: String?,
    val transport: CredentialTransferTransport = if (baseUrl == null) {
        CredentialTransferTransport.DISABLED
    } else {
        CredentialTransferTransport.REMOTE_RELAY
    },
)

enum class CredentialTransferTransport {
    DISABLED,
    REMOTE_RELAY,
    LOCAL_HTTP,
}

fun interface RemoteCredentialTransferConfigurationProvider {
    fun getConfiguration(): RemoteCredentialTransferConfiguration
}
