package com.fsck.k9.activity.compose

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.k9mail.core.ui.compose.designsystem.atom.button.RadioButton
import app.k9mail.core.ui.compose.designsystem.atom.text.TextBodyMedium
import app.k9mail.core.ui.compose.designsystem.atom.text.TextTitleMedium
import app.k9mail.core.ui.compose.designsystem.organism.AlertDialog
import com.fsck.k9.ui.R
import net.thunderbird.core.preference.DualScreenMode
import net.thunderbird.core.ui.compose.theme2.MainTheme

internal const val DUAL_SCREEN_MODE_BUTTON_TEST_TAG = "dual_screen_mode_button"
internal const val DUAL_SCREEN_MODE_DIALOG_TEST_TAG = "dual_screen_mode_dialog"
internal fun dualScreenModeOptionTestTag(mode: DualScreenMode): String = "dual_screen_mode_option_${mode.name}"

internal data class DualScreenModeEntryState(
    val currentMode: DualScreenMode,
    val dialogVisible: Boolean,
)

internal data class DualScreenModeEntryCallbacks(
    val onModeSelected: (DualScreenMode) -> Unit,
    val onDialogVisibilityChanged: (Boolean) -> Unit,
    val onPositionDragStarted: () -> Unit = {},
    val onPositionDragged: (Float) -> Unit = {},
    val onPositionDragFinished: () -> Unit = {},
    val onPositionToggle: () -> Unit = {},
)

@Composable
internal fun DualScreenModeEntry(
    visible: Boolean,
    state: DualScreenModeEntryState,
    callbacks: DualScreenModeEntryCallbacks,
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    val currentModeName = stringResource(state.currentMode.titleResource())
    DualScreenModeButton(
        currentModeName = currentModeName,
        callbacks = callbacks,
        modifier = modifier,
    )

    if (state.dialogVisible) {
        DualScreenModeDialog(
            currentMode = state.currentMode,
            onModeSelected = callbacks.onModeSelected,
            onDismissRequest = { callbacks.onDialogVisibilityChanged(false) },
        )
    }
}

@Composable
private fun DualScreenModeDialog(
    currentMode: DualScreenMode,
    onModeSelected: (DualScreenMode) -> Unit,
    onDismissRequest: () -> Unit,
) {
    AlertDialog(
        title = stringResource(R.string.dual_screen_mode_dialog_title),
        confirmText = stringResource(R.string.dual_screen_mode_close),
        onConfirmClick = onDismissRequest,
        onDismissRequest = onDismissRequest,
        modifier = Modifier.testTag(DUAL_SCREEN_MODE_DIALOG_TEST_TAG),
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(MainTheme.spacings.default),
        ) {
            DualScreenMode.entries.forEach { mode ->
                RadioButton(
                    selected = mode == currentMode,
                    onClick = {
                        onDismissRequest()
                        onModeSelected(mode)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(dualScreenModeOptionTestTag(mode)),
                    label = {
                        Column(
                            modifier = Modifier.padding(vertical = MainTheme.spacings.default),
                        ) {
                            TextTitleMedium(text = stringResource(mode.titleResource()))
                            TextBodyMedium(text = stringResource(mode.descriptionResource()))
                        }
                    },
                )
            }
        }
    }
}

@StringRes
private fun DualScreenMode.titleResource(): Int = when (this) {
    DualScreenMode.IMMERSIVE -> R.string.dual_screen_mode_immersive
    DualScreenMode.SMART -> R.string.dual_screen_mode_smart
}

@StringRes
private fun DualScreenMode.descriptionResource(): Int = when (this) {
    DualScreenMode.IMMERSIVE -> R.string.dual_screen_mode_immersive_description
    DualScreenMode.SMART -> R.string.dual_screen_mode_smart_description
}
