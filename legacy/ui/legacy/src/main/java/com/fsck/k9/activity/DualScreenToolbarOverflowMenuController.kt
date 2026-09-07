package com.fsck.k9.activity

import android.content.res.ColorStateList
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.ComponentActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.view.menu.MenuItemImpl
import com.fsck.k9.ui.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import app.k9mail.core.ui.legacy.designsystem.R as DesignSystemR
import com.google.android.material.R as MaterialR

/**
 * Replaces Toolbar's window-based overflow with an in-canvas menu while an Activity is projected across two screens.
 */
internal class DualScreenToolbarOverflowMenuController(
    private val activity: ComponentActivity,
    private val showPopupMenu: (
        anchor: View,
        actions: List<MenuItem>,
        onMenuItemSelected: (MenuItem) -> Boolean,
    ) -> Boolean,
) {
    private var routedMenu: Menu? = null
    private var routedActions = emptyList<MenuItem>()

    fun prepare(menu: Menu, usesInCanvasPopups: Boolean) {
        if (!usesInCanvasPopups) {
            restoreRoutedActions()
            menu.findItem(R.id.dual_screen_more_actions)?.isVisible = false
            routedMenu = null
            return
        }

        val retainedActions = if (routedMenu === menu) {
            routedActions.filter { action -> menu.findItem(action.itemId) === action }
        } else {
            emptyList()
        }
        val newActions = menu.dualScreenOverflowItems().filterNot { action -> action in retainedActions }
        val overflowActions = retainedActions + newActions
        if (overflowActions.isEmpty()) return

        val moreItem = menu.findItem(R.id.dual_screen_more_actions) ?: menu.add(
            Menu.NONE,
            R.id.dual_screen_more_actions,
            Menu.NONE,
            R.string.more_options_action,
        )
        moreItem.apply {
            isVisible = true
            setIcon(DesignSystemR.drawable.ic_more_vert)
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            actionView = createOverflowButton(title ?: activity.getText(R.string.more_options_action)) { anchor ->
                showPopupMenu(anchor, overflowActions) { action ->
                    menu.performIdentifierAction(action.itemId, 0)
                }
            }
        }

        overflowActions.forEach { action -> action.isVisible = false }
        routedMenu = menu
        routedActions = overflowActions
    }

    fun clear() {
        restoreRoutedActions()
        routedMenu?.findItem(R.id.dual_screen_more_actions)?.isVisible = false
        routedMenu = null
    }

    private fun restoreRoutedActions() {
        routedActions.forEach { action -> action.isVisible = true }
        routedActions = emptyList()
    }

    private fun createOverflowButton(
        contentDescription: CharSequence,
        onClick: (View) -> Unit,
    ): MaterialButton {
        return MaterialButton(
            activity,
            null,
            MaterialR.attr.materialIconButtonStyle,
        ).apply {
            val color = MaterialColors.getColor(this, MaterialR.attr.colorOnSurface)
            iconTint = ColorStateList.valueOf(color)
            icon = AppCompatResources.getDrawable(context, DesignSystemR.drawable.ic_more_vert)
            this.contentDescription = contentDescription
            setOnClickListener(onClick)
        }
    }
}

@Suppress("RestrictedApi")
internal fun Menu.dualScreenOverflowItems(): List<MenuItem> {
    return visibleItems().filter { item ->
        item.itemId != R.id.dual_screen_more_actions &&
            (item as? MenuItemImpl)?.let { !it.requiresActionButton() && !it.requestsActionButton() } == true
    }
}
