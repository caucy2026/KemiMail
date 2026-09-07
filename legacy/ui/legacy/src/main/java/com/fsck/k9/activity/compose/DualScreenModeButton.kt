package com.fsck.k9.activity.compose

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.k9mail.core.ui.compose.designsystem.atom.button.ButtonDefaults
import app.k9mail.core.ui.compose.designsystem.atom.button.ButtonFilled
import com.fsck.k9.activity.DUAL_SCREEN_FLOATING_CONTROL_BACKGROUND_ALPHA
import com.fsck.k9.ui.R
import net.thunderbird.core.ui.compose.theme2.MainTheme

@Composable
internal fun DualScreenModeButton(
    currentModeName: String,
    callbacks: DualScreenModeEntryCallbacks,
    modifier: Modifier = Modifier,
) {
    val buttonDescription = stringResource(R.string.dual_screen_mode_button_description, currentModeName)
    val moveButtonDescription = stringResource(R.string.dual_screen_floating_control_move)
    val contentColor = MainTheme.colors.onPrimaryContainer

    ButtonFilled(
        text = currentModeName,
        icon = DualScreenModeIcon,
        onClick = { callbacks.onDialogVisibilityChanged(true) },
        modifier = modifier
            .padding(MainTheme.spacings.double)
            .wrapContentWidth()
            .pointerInput(callbacks) {
                detectHorizontalDragGestures(
                    onDragStart = { callbacks.onPositionDragStarted() },
                    onDragEnd = callbacks.onPositionDragFinished,
                    onDragCancel = callbacks.onPositionDragFinished,
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        callbacks.onPositionDragged(dragAmount)
                    },
                )
            }
            .semantics {
                contentDescription = buttonDescription
                customActions = listOf(
                    CustomAccessibilityAction(moveButtonDescription) {
                        callbacks.onPositionToggle()
                        true
                    },
                )
            }
            .testTag(DUAL_SCREEN_MODE_BUTTON_TEST_TAG),
        colors = ButtonDefaults.filledButtonColors(
            containerColor = MainTheme.colors.primaryContainer.copy(
                alpha = DUAL_SCREEN_FLOATING_CONTROL_BACKGROUND_ALPHA,
            ),
            contentColor = contentColor,
            iconColor = contentColor,
        ),
    )
}

@Suppress("MagicNumber")
private val DualScreenModeIcon: ImageVector = ImageVector.Builder(
    name = "DualScreenMode",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(fill = SolidColor(Color.Black)) {
        moveTo(3f, 3f)
        lineTo(21f, 3f)
        lineTo(21f, 10f)
        lineTo(3f, 10f)
        close()
        moveTo(3f, 14f)
        lineTo(21f, 14f)
        lineTo(21f, 21f)
        lineTo(3f, 21f)
        close()
    }
}.build()
