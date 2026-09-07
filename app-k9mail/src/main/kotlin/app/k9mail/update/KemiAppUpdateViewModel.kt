package app.k9mail.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.thunderbird.core.ui.contract.mvi.BaseViewModel

internal class KemiAppUpdateViewModel(
    val updateInfo: KemiAppUpdateInfo,
    private val repository: KemiAppUpdateRepository,
) : BaseViewModel<KemiAppUpdateState, KemiAppUpdateEvent, KemiAppUpdateEffect>(KemiAppUpdateState()) {
    private var downloadJob: Job? = null
    private var storeAttempted = false

    override fun event(event: KemiAppUpdateEvent) {
        when (event) {
            KemiAppUpdateEvent.UpdateClicked -> update()
            KemiAppUpdateEvent.RetryClicked -> retry()
            KemiAppUpdateEvent.DismissClicked -> dismiss()
            KemiAppUpdateEvent.StoreUnavailable -> download()
            KemiAppUpdateEvent.StoreOpened -> {
                if (!updateInfo.forceUpdate) emitEffect(KemiAppUpdateEffect.Finish)
            }
            is KemiAppUpdateEvent.InstallPermissionResult -> handleInstallPermission(event.granted)
            KemiAppUpdateEvent.InstallationFailed -> showFailure(KemiAppUpdateFailure.INSTALLATION)
        }
    }

    private fun update() {
        when (state.value.phase) {
            KemiAppUpdatePhase.DOWNLOADING -> Unit
            KemiAppUpdatePhase.READY -> prepareInstall(state.value.downloadedFile)
            KemiAppUpdatePhase.PROMPT,
            KemiAppUpdatePhase.ERROR,
            -> {
                val deepLink = updateInfo.deepLink
                if (!storeAttempted && deepLink != null) {
                    storeAttempted = true
                    emitEffect(KemiAppUpdateEffect.OpenStore(deepLink))
                } else {
                    download()
                }
            }
        }
    }

    private fun retry() {
        val downloadedFile = state.value.downloadedFile
        if (downloadedFile != null && downloadedFile.isFile) {
            updateState {
                it.copy(phase = KemiAppUpdatePhase.READY, failure = null)
            }
            prepareInstall(downloadedFile)
        } else {
            download()
        }
    }

    private fun dismiss() {
        if (!updateInfo.forceUpdate) {
            downloadJob?.cancel()
            emitEffect(KemiAppUpdateEffect.Finish)
        }
    }

    private fun download() {
        if (downloadJob?.isActive == true) return
        updateState {
            KemiAppUpdateState(phase = KemiAppUpdatePhase.DOWNLOADING)
        }
        downloadJob = viewModelScope.launch {
            runCatching {
                repository.download(updateInfo) { downloadedBytes, totalBytes ->
                    val progress = totalBytes
                        ?.takeIf { it > 0 }
                        ?.let {
                            ((downloadedBytes * PERCENT_SCALE) / it)
                                .coerceIn(MIN_PERCENT, PERCENT_SCALE)
                                .toInt()
                        }
                    updateState { state -> state.copy(progressPercent = progress) }
                }
            }.onSuccess { file ->
                updateState {
                    KemiAppUpdateState(
                        phase = KemiAppUpdatePhase.READY,
                        progressPercent = 100,
                        downloadedFile = file,
                    )
                }
                prepareInstall(file)
            }.onFailure { exception ->
                val failure = (exception as? KemiAppUpdateException)?.failure ?: KemiAppUpdateFailure.DOWNLOAD
                showFailure(failure)
            }
        }
    }

    private fun prepareInstall(file: File?) {
        if (file == null || !file.isFile) {
            showFailure(KemiAppUpdateFailure.STORAGE)
        } else {
            emitEffect(KemiAppUpdateEffect.PrepareInstall(file))
        }
    }

    private fun handleInstallPermission(granted: Boolean) {
        val file = state.value.downloadedFile
        if (!granted || file == null) {
            showFailure(KemiAppUpdateFailure.PERMISSION, file)
        } else {
            emitEffect(KemiAppUpdateEffect.Install(file))
        }
    }

    private fun showFailure(failure: KemiAppUpdateFailure, downloadedFile: File? = state.value.downloadedFile) {
        updateState {
            KemiAppUpdateState(
                phase = KemiAppUpdatePhase.ERROR,
                downloadedFile = downloadedFile,
                failure = failure,
            )
        }
    }

    internal class Factory(
        private val updateInfo: KemiAppUpdateInfo,
        private val repository: KemiAppUpdateRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return KemiAppUpdateViewModel(updateInfo, repository) as T
        }
    }

    private companion object {
        const val MIN_PERCENT = 0L
        const val PERCENT_SCALE = 100L
    }
}
