package app.k9mail.update

import java.io.File

internal data class KemiAppUpdateInfo(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val apkUrl: String,
    val apkSha256: String,
    val fileSizeBytes: Long?,
    val forceUpdate: Boolean,
    val deepLink: String?,
    val releaseNotes: String,
)

internal enum class KemiAppUpdateFailure {
    NETWORK,
    INVALID_RESPONSE,
    DOWNLOAD,
    INTEGRITY,
    PACKAGE,
    SIGNATURE,
    STORAGE,
    PERMISSION,
    INSTALLATION,
}

internal class KemiAppUpdateException(
    val failure: KemiAppUpdateFailure,
    cause: Throwable? = null,
) : Exception(failure.name, cause)

internal enum class KemiAppUpdatePhase {
    PROMPT,
    DOWNLOADING,
    READY,
    ERROR,
}

internal data class KemiAppUpdateState(
    val phase: KemiAppUpdatePhase = KemiAppUpdatePhase.PROMPT,
    val progressPercent: Int? = null,
    val downloadedFile: File? = null,
    val failure: KemiAppUpdateFailure? = null,
)

internal sealed interface KemiAppUpdateEvent {
    data object UpdateClicked : KemiAppUpdateEvent
    data object RetryClicked : KemiAppUpdateEvent
    data object DismissClicked : KemiAppUpdateEvent
    data object StoreUnavailable : KemiAppUpdateEvent
    data object StoreOpened : KemiAppUpdateEvent
    data class InstallPermissionResult(val granted: Boolean) : KemiAppUpdateEvent
    data object InstallationFailed : KemiAppUpdateEvent
}

internal sealed interface KemiAppUpdateEffect {
    data class OpenStore(val deepLink: String) : KemiAppUpdateEffect
    data class PrepareInstall(val file: File) : KemiAppUpdateEffect
    data class Install(val file: File) : KemiAppUpdateEffect
    data object Finish : KemiAppUpdateEffect
}
