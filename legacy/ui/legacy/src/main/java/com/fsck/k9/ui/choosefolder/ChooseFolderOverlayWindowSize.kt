package com.fsck.k9.ui.choosefolder

import kotlin.math.min
import kotlin.math.roundToInt

internal data class ChooseFolderOverlaySize(
    val width: Int,
    val height: Int,
)

internal enum class ChooseFolderLaunchMode {
    FULL_SCREEN,
    OVERLAY,
    ;

    companion object {
        fun resolve(hasEligibleSecondaryDisplay: Boolean): ChooseFolderLaunchMode {
            return if (hasEligibleSecondaryDisplay) OVERLAY else FULL_SCREEN
        }
    }
}

internal object ChooseFolderOverlayWindowSize {
    private const val CONTENT_MARGIN_DP = 32
    private const val MAX_WIDTH_DP = 600
    private const val MAX_HEIGHT_DP = 480

    fun calculate(
        availableWidth: Int,
        availableHeight: Int,
        density: Float,
    ): ChooseFolderOverlaySize {
        require(availableWidth > 0)
        require(availableHeight > 0)
        require(density > 0)

        val horizontalMargin = (CONTENT_MARGIN_DP * density).roundToInt()
        val verticalMargin = (CONTENT_MARGIN_DP * density).roundToInt()
        val maxWidth = (MAX_WIDTH_DP * density).roundToInt()
        val maxHeight = (MAX_HEIGHT_DP * density).roundToInt()

        return ChooseFolderOverlaySize(
            width = min(maxWidth, availableWidth - horizontalMargin * 2).coerceAtLeast(1),
            height = min(maxHeight, availableHeight - verticalMargin * 2).coerceAtLeast(1),
        )
    }
}
