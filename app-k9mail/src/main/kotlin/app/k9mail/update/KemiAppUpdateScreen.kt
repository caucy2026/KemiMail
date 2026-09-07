package app.k9mail.update

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.k9mail.core.ui.compose.designsystem.atom.CircularProgressIndicator
import app.k9mail.core.ui.compose.designsystem.atom.button.ButtonFilled
import app.k9mail.core.ui.compose.designsystem.atom.button.ButtonText
import app.k9mail.core.ui.compose.designsystem.atom.card.CardFilled
import app.k9mail.core.ui.compose.designsystem.atom.text.TextBodyMedium
import app.k9mail.core.ui.compose.designsystem.atom.text.TextBodySmall
import app.k9mail.core.ui.compose.designsystem.atom.text.TextTitleLarge
import app.k9mail.core.ui.compose.designsystem.organism.TopAppBar
import app.k9mail.core.ui.compose.designsystem.organism.TopAppBarWithBackButton
import app.k9mail.core.ui.compose.designsystem.template.ResponsiveWidthContainer
import app.k9mail.core.ui.compose.designsystem.template.Scaffold
import com.fsck.k9.R
import net.thunderbird.core.ui.compose.theme2.MainTheme

@Composable
internal fun KemiAppUpdateScreen(
    updateInfo: KemiAppUpdateInfo,
    state: KemiAppUpdateState,
    onEvent: (KemiAppUpdateEvent) -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            UpdateTopBar(updateInfo.forceUpdate, onEvent)
        },
    ) { contentPadding ->
        ResponsiveWidthContainer(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) { widthPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(widthPadding)
                    .padding(MainTheme.spacings.double)
                    .verticalScroll(rememberScrollState()),
            ) {
                UpdateCard(updateInfo, state, onEvent)
            }
        }
    }
}

@Composable
private fun UpdateTopBar(
    forceUpdate: Boolean,
    onEvent: (KemiAppUpdateEvent) -> Unit,
) {
    if (forceUpdate) {
        TopAppBar(title = stringResource(R.string.kemi_update_title))
    } else {
        TopAppBarWithBackButton(
            title = stringResource(R.string.kemi_update_title),
            onBackClick = { onEvent(KemiAppUpdateEvent.DismissClicked) },
        )
    }
}

@Composable
private fun UpdateCard(
    updateInfo: KemiAppUpdateInfo,
    state: KemiAppUpdateState,
    onEvent: (KemiAppUpdateEvent) -> Unit,
) {
    val description = if (updateInfo.releaseNotes.isNotBlank()) {
        updateInfo.releaseNotes
    } else {
        stringResource(R.string.kemi_update_description)
    }
    CardFilled(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(MainTheme.spacings.double),
            verticalArrangement = Arrangement.spacedBy(MainTheme.spacings.default),
        ) {
            TextTitleLarge(text = stringResource(R.string.kemi_update_version, updateInfo.versionName))
            TextBodyMedium(text = description)
            if (updateInfo.forceUpdate) {
                TextBodySmall(text = stringResource(R.string.kemi_update_required))
            }
            UpdateStatus(state)
            Spacer(modifier = Modifier.height(MainTheme.spacings.default))
            UpdateActions(updateInfo.forceUpdate, state, onEvent)
        }
    }
}

@Composable
private fun UpdateActions(
    forceUpdate: Boolean,
    state: KemiAppUpdateState,
    onEvent: (KemiAppUpdateEvent) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        if (!forceUpdate) {
            ButtonText(
                text = stringResource(R.string.kemi_update_later),
                onClick = { onEvent(KemiAppUpdateEvent.DismissClicked) },
            )
        }
        ButtonFilled(
            text = stringResource(state.actionText()),
            onClick = {
                val event = if (state.phase == KemiAppUpdatePhase.ERROR) {
                    KemiAppUpdateEvent.RetryClicked
                } else {
                    KemiAppUpdateEvent.UpdateClicked
                }
                onEvent(event)
            },
            enabled = state.phase != KemiAppUpdatePhase.DOWNLOADING,
        )
    }
}

@Composable
private fun UpdateStatus(state: KemiAppUpdateState) {
    when (state.phase) {
        KemiAppUpdatePhase.PROMPT -> Unit
        KemiAppUpdatePhase.DOWNLOADING -> {
            CircularProgressIndicator()
            TextBodyMedium(
                text = state.progressPercent?.let { progress ->
                    stringResource(R.string.kemi_update_downloading_progress, progress)
                } ?: stringResource(R.string.kemi_update_downloading),
            )
        }
        KemiAppUpdatePhase.READY -> {
            TextBodyMedium(text = stringResource(R.string.kemi_update_ready))
        }
        KemiAppUpdatePhase.ERROR -> {
            TextBodyMedium(
                text = stringResource(state.failure.errorText()),
                color = MainTheme.colors.error,
            )
        }
    }
}

@StringRes
private fun KemiAppUpdateState.actionText(): Int = when (phase) {
    KemiAppUpdatePhase.PROMPT -> R.string.kemi_update_now
    KemiAppUpdatePhase.DOWNLOADING -> R.string.kemi_update_downloading
    KemiAppUpdatePhase.READY -> R.string.kemi_update_install
    KemiAppUpdatePhase.ERROR -> R.string.kemi_update_retry
}

@StringRes
private fun KemiAppUpdateFailure?.errorText(): Int = when (this) {
    KemiAppUpdateFailure.NETWORK -> R.string.kemi_update_error_network
    KemiAppUpdateFailure.INVALID_RESPONSE -> R.string.kemi_update_error_response
    KemiAppUpdateFailure.INTEGRITY -> R.string.kemi_update_error_integrity
    KemiAppUpdateFailure.PACKAGE -> R.string.kemi_update_error_package
    KemiAppUpdateFailure.SIGNATURE -> R.string.kemi_update_error_signature
    KemiAppUpdateFailure.STORAGE -> R.string.kemi_update_error_storage
    KemiAppUpdateFailure.PERMISSION -> R.string.kemi_update_error_permission
    KemiAppUpdateFailure.INSTALLATION -> R.string.kemi_update_error_installation
    KemiAppUpdateFailure.DOWNLOAD,
    null,
    -> R.string.kemi_update_error_download
}
