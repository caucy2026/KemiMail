package app.k9mail.core.ui.compose.common.window

import androidx.compose.ui.unit.Dp

private const val DUAL_SCREEN_CANVAS_MIN_VIEWPORTS = 1.5f

/**
 * Detects the canvas created by the automatic dual-screen projection.
 *
 * The projection expands the measured content height to two viewports while the window configuration continues to
 * report the height of one physical display.
 */
fun isSpannedDualScreenCanvas(canvasHeight: Dp, viewportHeight: Dp): Boolean {
    return canvasHeight >= viewportHeight * DUAL_SCREEN_CANVAS_MIN_VIEWPORTS
}
