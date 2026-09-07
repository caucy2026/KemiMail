package com.fsck.k9.activity

import kotlin.math.abs

internal enum class DualScreenFloatingControlPosition {
    EDGE,
    INSET,
}

internal enum class DualScreenFloatingControlDirection(
    val multiplier: Float,
) {
    LEFT(-1f),
    RIGHT(1f),
}

/** Keeps a floating control on one of two horizontal anchors while allowing a bounded drag between them. */
internal class DualScreenFloatingControlPositionController(
    private val direction: DualScreenFloatingControlDirection,
    private val insetDistance: Float,
    initialPosition: DualScreenFloatingControlPosition = DualScreenFloatingControlPosition.EDGE,
) {
    init {
        require(insetDistance >= 0f) { "Inset distance must not be negative" }
    }

    var position: DualScreenFloatingControlPosition = initialPosition
        private set

    private var currentTranslation = targetTranslation(initialPosition)

    fun startDrag(translationX: Float) {
        currentTranslation = translationX.coerceIn(translationRange())
    }

    fun dragBy(deltaX: Float): Float {
        currentTranslation = (currentTranslation + deltaX).coerceIn(translationRange())
        return currentTranslation
    }

    fun settle(): Float {
        position = if (abs(currentTranslation) >= insetDistance / 2f) {
            DualScreenFloatingControlPosition.INSET
        } else {
            DualScreenFloatingControlPosition.EDGE
        }
        currentTranslation = targetTranslation(position)
        return currentTranslation
    }

    fun toggle(): Float {
        position = when (position) {
            DualScreenFloatingControlPosition.EDGE -> DualScreenFloatingControlPosition.INSET
            DualScreenFloatingControlPosition.INSET -> DualScreenFloatingControlPosition.EDGE
        }
        currentTranslation = targetTranslation(position)
        return currentTranslation
    }

    fun currentTarget(): Float = targetTranslation(position)

    private fun targetTranslation(position: DualScreenFloatingControlPosition): Float = when (position) {
        DualScreenFloatingControlPosition.EDGE -> 0f
        DualScreenFloatingControlPosition.INSET -> direction.multiplier * insetDistance
    }

    private fun translationRange(): ClosedFloatingPointRange<Float> = when (direction) {
        DualScreenFloatingControlDirection.LEFT -> -insetDistance..0f
        DualScreenFloatingControlDirection.RIGHT -> 0f..insetDistance
    }
}
