package com.fsck.k9.activity

import android.content.pm.ActivityInfo
import android.view.Display
import android.view.Surface

/**
 * Explicit physical and logical capabilities for one supported dual-screen hardware generation.
 *
 * Physical dimensions are used only to select a display. Logical dimensions define the authoritative Activity
 * canvas. Keeping them separate makes ROM-level scaling an explicit profile change instead of a fuzzy size match.
 */
internal data class DualScreenDeviceProfile(
    val name: String,
    val physicalViewportWidth: Int,
    val physicalViewportHeight: Int,
    val logicalViewportWidth: Int,
    val logicalViewportHeight: Int,
    val primaryDisplayId: Int,
    val preferredSecondaryDisplayId: Int,
    val supportedSecondaryRotations: Set<Int>,
    val requestedOrientation: Int,
    val logicalViewportCount: Int = DUAL_VIEWPORT_COUNT,
) {
    init {
        require(name.isNotBlank())
        require(physicalViewportWidth > 0 && physicalViewportHeight > 0)
        require(logicalViewportWidth > 0 && logicalViewportHeight > 0)
        require(primaryDisplayId != preferredSecondaryDisplayId)
        require(supportedSecondaryRotations.isNotEmpty())
        require(supportedSecondaryRotations.all { it in Surface.ROTATION_0..Surface.ROTATION_270 })
        require(logicalViewportCount == DUAL_VIEWPORT_COUNT)
        require(logicalViewportHeight <= Int.MAX_VALUE / logicalViewportCount)
    }

    val logicalCanvasHeight: Int = logicalViewportHeight * logicalViewportCount
    val primaryTranslationY: Float = -logicalViewportHeight.toFloat()

    fun matchesSecondaryDisplay(width: Int, height: Int, rotation: Int): Boolean {
        return width == physicalViewportWidth &&
            height == physicalViewportHeight &&
            rotation in supportedSecondaryRotations
    }

    fun presentationScaleX(viewportWidth: Int): Float {
        require(viewportWidth > 0)
        return viewportWidth.toFloat() / logicalViewportWidth
    }

    fun presentationScaleY(viewportHeight: Int): Float {
        require(viewportHeight > 0)
        return viewportHeight.toFloat() / logicalViewportHeight
    }

    fun secondaryLogicalPoint(
        viewportX: Float,
        viewportY: Float,
        viewportWidth: Int,
        viewportHeight: Int,
    ): DualScreenLogicalPoint {
        return DualScreenLogicalPoint(
            x = viewportX / presentationScaleX(viewportWidth),
            y = viewportY / presentationScaleY(viewportHeight),
        )
    }

    private companion object {
        const val DUAL_VIEWPORT_COUNT = 2
    }
}

internal data class DualScreenLogicalPoint(
    val x: Float,
    val y: Float,
)

internal object DualScreenDeviceProfiles {
    val KEMI_GENERATION_1 = DualScreenDeviceProfile(
        name = "KEMI generation 1",
        physicalViewportWidth = 1920,
        physicalViewportHeight = 1280,
        logicalViewportWidth = 1920,
        logicalViewportHeight = 1280,
        primaryDisplayId = Display.DEFAULT_DISPLAY,
        preferredSecondaryDisplayId = 2,
        supportedSecondaryRotations = setOf(
            Surface.ROTATION_0,
            Surface.ROTATION_90,
            Surface.ROTATION_180,
            Surface.ROTATION_270,
        ),
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
    )

    val supported: List<DualScreenDeviceProfile> = listOf(KEMI_GENERATION_1)
}
