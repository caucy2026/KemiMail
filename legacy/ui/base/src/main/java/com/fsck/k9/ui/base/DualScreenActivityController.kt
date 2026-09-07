package com.fsck.k9.ui.base

import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import androidx.activity.ComponentActivity

/**
 * Controls the projection of an Activity's authoritative view onto a secondary display.
 *
 * The implementation is supplied by the application layer so this base UI module doesn't depend on a concrete
 * dual-screen device implementation.
 */
interface DualScreenActivityController {
    val usesInCanvasPopups: Boolean
        get() = false

    fun start()

    fun stop()

    fun destroy()

    fun prepareOptionsMenu(menu: Menu) {}

    fun onPrimaryTouchEvent(event: MotionEvent) {}

    fun showInCanvasPopup(anchor: View, content: View): Boolean = false

    fun showPopupMenu(
        anchor: View,
        actions: List<MenuItem>,
        onMenuItemSelected: (MenuItem) -> Boolean,
    ): Boolean = false

    fun dismissInCanvasPopup(): Boolean = false
}

fun interface DualScreenActivityControllerFactory {
    fun create(activity: ComponentActivity, sourceView: View): DualScreenActivityController
}
