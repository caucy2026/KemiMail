package com.fsck.k9.activity

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import com.google.android.material.color.MaterialColors
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlin.math.abs
import kotlin.math.roundToInt
import com.google.android.material.R as MaterialR

internal const val DUAL_SCREEN_FLOATING_CONTROL_BACKGROUND_ALPHA = 0.72f
private const val POSITION_ANIMATION_DURATION_MILLIS = 160L
private const val COLOR_CHANNEL_MAX = 255

internal enum class DualScreenFloatingControlEdge {
    START,
    END,
    ;

    fun inwardDirection(layoutDirection: Int): DualScreenFloatingControlDirection {
        val startDirection = if (layoutDirection == View.LAYOUT_DIRECTION_RTL) {
            DualScreenFloatingControlDirection.LEFT
        } else {
            DualScreenFloatingControlDirection.RIGHT
        }
        return when (this) {
            START -> startDirection
            END -> if (startDirection == DualScreenFloatingControlDirection.RIGHT) {
                DualScreenFloatingControlDirection.LEFT
            } else {
                DualScreenFloatingControlDirection.RIGHT
            }
        }
    }
}

internal class DualScreenFloatingControlMover(
    private val view: View,
    direction: DualScreenFloatingControlDirection,
    insetDistance: Float,
    initialPosition: DualScreenFloatingControlPosition,
    private val onPositionChanged: (DualScreenFloatingControlPosition) -> Unit,
) {
    private val positionController = DualScreenFloatingControlPositionController(
        direction = direction,
        insetDistance = insetDistance,
        initialPosition = initialPosition,
    )

    init {
        view.translationX = positionController.currentTarget()
    }

    fun startDrag() {
        view.animate().cancel()
        positionController.startDrag(view.translationX)
    }

    fun dragBy(deltaX: Float) {
        view.translationX = positionController.dragBy(deltaX)
    }

    fun finishDrag() {
        animateTo(positionController.settle())
        onPositionChanged(positionController.position)
    }

    fun togglePosition() {
        animateTo(positionController.toggle())
        onPositionChanged(positionController.position)
    }

    @SuppressLint("ClickableViewAccessibility")
    fun installTouchDrag(accessibilityActionLabel: String) {
        view.setOnTouchListener(HorizontalDragTouchListener(this, view))
        ViewCompat.addAccessibilityAction(view, accessibilityActionLabel) { _, _ ->
            togglePosition()
            true
        }
    }

    private fun animateTo(translationX: Float) {
        view.animate()
            .translationX(translationX)
            .setDuration(POSITION_ANIMATION_DURATION_MILLIS)
            .start()
    }
}

internal fun FloatingActionButton.applyDualScreenFloatingControlStyle() {
    val containerColor = MaterialColors.getColor(this, MaterialR.attr.colorPrimaryContainer)
    val contentColor = MaterialColors.getColor(this, MaterialR.attr.colorOnPrimaryContainer)
    val translucentContainerColor = ColorUtils.setAlphaComponent(
        containerColor,
        (DUAL_SCREEN_FLOATING_CONTROL_BACKGROUND_ALPHA * COLOR_CHANNEL_MAX).roundToInt(),
    )

    backgroundTintList = ColorStateList.valueOf(translucentContainerColor)
    imageTintList = ColorStateList.valueOf(contentColor)
    compatElevation = 0f
    compatHoveredFocusedTranslationZ = 0f
    compatPressedTranslationZ = 0f
}

private class HorizontalDragTouchListener(
    private val mover: DualScreenFloatingControlMover,
    view: View,
) : View.OnTouchListener {
    private val touchSlop = ViewConfiguration.get(view.context).scaledTouchSlop
    private var downRawX = 0f
    private var lastRawX = 0f
    private var isDragging = false

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                lastRawX = event.rawX
                isDragging = false
                mover.startDrag()
                false
            }

            MotionEvent.ACTION_MOVE -> handleMove(view, event)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> finishDrag(view)
            else -> isDragging
        }
    }

    private fun handleMove(view: View, event: MotionEvent): Boolean {
        if (!isDragging && abs(event.rawX - downRawX) >= touchSlop) {
            isDragging = true
            view.parent?.requestDisallowInterceptTouchEvent(true)
            mover.dragBy(event.rawX - downRawX)
        } else if (isDragging) {
            mover.dragBy(event.rawX - lastRawX)
        }
        lastRawX = event.rawX
        return isDragging
    }

    private fun finishDrag(view: View): Boolean {
        if (!isDragging) return false

        view.isPressed = false
        view.parent?.requestDisallowInterceptTouchEvent(false)
        mover.finishDrag()
        isDragging = false
        return true
    }
}
