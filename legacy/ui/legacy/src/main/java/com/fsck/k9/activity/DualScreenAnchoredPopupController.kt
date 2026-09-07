package com.fsck.k9.activity

import android.graphics.Rect
import android.view.MotionEvent
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewGroup
import android.widget.FrameLayout
import kotlin.math.min

/**
 * Hosts transient UI inside the authoritative dual-screen canvas.
 *
 * Android window-based popups are always attached to the Activity display. In immersive dual-screen mode that is the
 * lower display, even when the triggering view is rendered and touched through the upper [android.app.Presentation].
 * Keeping the popup in the shared view hierarchy makes it render and receive input in the same logical viewport as
 * its anchor.
 */
internal class DualScreenAnchoredPopupController(
    private val rootView: ViewGroup,
    private val viewportHeightProvider: () -> Int,
) {
    private var popupContainer: View? = null
    private val popupBounds = Rect()

    val isShowing: Boolean
        get() = popupContainer != null

    fun canAnchor(anchor: View): Boolean {
        var current: View? = anchor
        while (current != null) {
            if (current === rootView) return true
            current = current.parent as? View
        }
        return false
    }

    fun show(anchor: View, content: View) {
        dismiss()

        val viewportHeight = viewportHeightProvider()
        if (!canAnchor(anchor) || rootView.width <= 0 || rootView.height <= 0 || viewportHeight <= 0) return

        val anchorBounds = Rect(0, 0, anchor.width, anchor.height)
        rootView.offsetDescendantRectToMyCoords(anchor, anchorBounds)

        val container = BoundedPopupContainer(
            rootView = rootView,
            maxWidth = rootView.width,
            maxHeight = min(viewportHeight, rootView.height),
            onSizeChanged = { width, height ->
                popupContainer?.let { popup ->
                    positionPopup(
                        popup = popup,
                        anchorBounds = anchorBounds,
                        popupWidth = width,
                        popupHeight = height,
                        viewportHeight = viewportHeight,
                    )
                }
            },
        ).apply {
            isClickable = true
            elevation = POPUP_ELEVATION_DP * resources.displayMetrics.density
            addView(
                content,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }

        popupContainer = container
        rootView.addView(
            container,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        container.measure(
            MeasureSpec.makeMeasureSpec(rootView.width, MeasureSpec.AT_MOST),
            MeasureSpec.makeMeasureSpec(min(viewportHeight, rootView.height), MeasureSpec.AT_MOST),
        )
        positionPopup(
            popup = container,
            anchorBounds = anchorBounds,
            popupWidth = container.measuredWidth,
            popupHeight = container.measuredHeight,
            viewportHeight = viewportHeight,
        )
    }

    fun dismiss(): Boolean {
        val popup = popupContainer ?: return false
        popupContainer = null
        popupBounds.setEmpty()
        rootView.removeView(popup)
        return true
    }

    fun dismissIfOutsideWindowTouch(event: MotionEvent) {
        if (event.actionMasked != MotionEvent.ACTION_DOWN) return

        val rootLocation = IntArray(2)
        rootView.getLocationInWindow(rootLocation)
        dismissIfOutside(
            x = event.x - rootLocation[0],
            y = event.y - rootLocation[1],
        )
    }

    fun dismissIfOutsideLogicalTouch(event: MotionEvent) {
        if (event.actionMasked != MotionEvent.ACTION_DOWN) return

        dismissIfOutside(event.x, event.y)
    }

    private fun dismissIfOutside(x: Float, y: Float) {
        if (popupContainer != null && !popupBounds.contains(x.toInt(), y.toInt())) {
            dismiss()
        }
    }

    private fun positionPopup(
        popup: View,
        anchorBounds: Rect,
        popupWidth: Int,
        popupHeight: Int,
        viewportHeight: Int,
    ) {
        val position = DualScreenPopupPositioner.calculate(
            anchorBounds = DualScreenAnchorBounds(
                left = anchorBounds.left,
                top = anchorBounds.top,
                right = anchorBounds.right,
                bottom = anchorBounds.bottom,
            ),
            popupWidth = popupWidth,
            popupHeight = popupHeight,
            rootWidth = rootView.width,
            rootHeight = rootView.height,
            viewportHeight = viewportHeight,
        )
        popup.x = position.x.toFloat()
        popup.y = position.y.toFloat()
        popupBounds.set(position.x, position.y, position.x + popupWidth, position.y + popupHeight)
    }

    private class BoundedPopupContainer(
        rootView: ViewGroup,
        private val maxWidth: Int,
        private val maxHeight: Int,
        private val onSizeChanged: (width: Int, height: Int) -> Unit,
    ) : FrameLayout(rootView.context) {
        private var previousWidth = 0
        private var previousHeight = 0

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(
                    min(MeasureSpec.getSize(widthMeasureSpec), maxWidth),
                    MeasureSpec.AT_MOST,
                ),
                MeasureSpec.makeMeasureSpec(
                    min(MeasureSpec.getSize(heightMeasureSpec), maxHeight),
                    MeasureSpec.AT_MOST,
                ),
            )

            if (measuredWidth != previousWidth || measuredHeight != previousHeight) {
                previousWidth = measuredWidth
                previousHeight = measuredHeight
                onSizeChanged(measuredWidth, measuredHeight)
            }
        }
    }

    private companion object {
        const val POPUP_ELEVATION_DP = 8f
    }
}

internal data class DualScreenPopupPosition(
    val x: Int,
    val y: Int,
)

internal data class DualScreenAnchorBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

internal object DualScreenPopupPositioner {
    fun calculate(
        anchorBounds: DualScreenAnchorBounds,
        popupWidth: Int,
        popupHeight: Int,
        rootWidth: Int,
        rootHeight: Int,
        viewportHeight: Int,
    ): DualScreenPopupPosition {
        require(popupWidth >= 0 && popupHeight >= 0)
        require(rootWidth > 0 && rootHeight > 0 && viewportHeight > 0)

        val anchorCenterY = anchorBounds.top + (anchorBounds.bottom - anchorBounds.top) / 2
        val viewportIndex = (anchorCenterY / viewportHeight)
            .coerceIn(0, (rootHeight - 1) / viewportHeight)
        val viewportTop = viewportIndex * viewportHeight
        val viewportBottom = min(viewportTop + viewportHeight, rootHeight)

        val maxX = (rootWidth - popupWidth).coerceAtLeast(0)
        val x = (anchorBounds.right - popupWidth).coerceIn(0, maxX)
        val y = when {
            anchorBounds.bottom + popupHeight <= viewportBottom -> anchorBounds.bottom
            anchorBounds.top - popupHeight >= viewportTop -> anchorBounds.top - popupHeight
            else -> viewportTop
        }

        return DualScreenPopupPosition(x, y)
    }
}
