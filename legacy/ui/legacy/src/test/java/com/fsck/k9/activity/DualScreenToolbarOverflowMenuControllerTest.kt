package com.fsck.k9.activity

import android.content.Context
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.ComponentActivity
import androidx.appcompat.widget.PopupMenu
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import com.fsck.k9.ui.R
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController

@RunWith(RobolectricTestRunner::class)
class DualScreenToolbarOverflowMenuControllerTest {
    private var activityController: ActivityController<ComponentActivity>? = null

    @After
    fun tearDown() {
        activityController?.close()
    }

    @Test
    fun `projected overflow should use an in-canvas action and dispatch through the original menu`() {
        // Arrange
        val activity = createActivity()
        val menu = PopupMenu(activity, View(activity)).menu
        val action = menu.add(Menu.NONE, ACTION_NEVER, Menu.NONE, "Never").apply {
            setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        }
        var routedActions = emptyList<MenuItem>()
        var selectedActionId: Int? = null
        val testSubject = DualScreenToolbarOverflowMenuController(activity) { _, actions, onMenuItemSelected ->
            routedActions = actions
            onMenuItemSelected(actions.single())
            true
        }
        menu.setGroupCheckable(Menu.NONE, false, false)
        (menu as androidx.appcompat.view.menu.MenuBuilder).setCallback(
            object : androidx.appcompat.view.menu.MenuBuilder.Callback {
                override fun onMenuItemSelected(
                    menu: androidx.appcompat.view.menu.MenuBuilder,
                    item: MenuItem,
                ): Boolean {
                    selectedActionId = item.itemId
                    return true
                }

                override fun onMenuModeChange(menu: androidx.appcompat.view.menu.MenuBuilder) = Unit
            },
        )

        // Act
        testSubject.prepare(menu, usesInCanvasPopups = true)
        val moreItem = menu.findItem(R.id.dual_screen_more_actions)
        val moreActionView = checkNotNull(moreItem.actionView)
        moreActionView.performClick()

        // Assert
        assertThat(action.isVisible).isFalse()
        assertThat(moreItem.isVisible).isTrue()
        assertThat(moreActionView).isNotNull()
        assertThat(routedActions).containsExactly(action)
        assertThat(selectedActionId).isEqualTo(ACTION_NEVER)
    }

    @Test
    fun `only never actions should be routed into the dual-screen overflow`() {
        // Arrange
        val menu = createMenu().apply {
            add(Menu.NONE, ACTION_NEVER, Menu.NONE, "Never").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
            add(Menu.NONE, ACTION_IF_ROOM, Menu.NONE, "If room")
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
            add(Menu.NONE, ACTION_ALWAYS, Menu.NONE, "Always")
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }

        // Act
        val result = menu.dualScreenOverflowItems().map(MenuItem::getItemId)

        // Assert
        assertThat(result).containsExactly(ACTION_NEVER)
    }

    @Test
    fun `hidden actions and the replacement action should not be routed`() {
        // Arrange
        val menu = createMenu().apply {
            add(Menu.NONE, ACTION_NEVER, Menu.NONE, "Hidden").apply {
                setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
                isVisible = false
            }
            add(Menu.NONE, R.id.dual_screen_more_actions, Menu.NONE, "More")
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        }

        // Act
        val result = menu.dualScreenOverflowItems()

        // Assert
        assertThat(result).isEmpty()
    }

    @Test
    fun `a routed hidden action should still dispatch through the original menu`() {
        // Arrange
        val context = ApplicationProvider.getApplicationContext<Context>()
        val popupMenu = PopupMenu(context, View(context))
        val action = popupMenu.menu.add(Menu.NONE, ACTION_NEVER, Menu.NONE, "Never")
        var selectedActionId: Int? = null
        popupMenu.setOnMenuItemClickListener { selectedAction ->
            selectedActionId = selectedAction.itemId
            true
        }
        action.isVisible = false

        // Act
        val handled = popupMenu.menu.performIdentifierAction(ACTION_NEVER, 0)

        // Assert
        assertThat(handled).isEqualTo(true)
        assertThat(selectedActionId).isEqualTo(ACTION_NEVER)
    }

    private fun createMenu() = PopupMenu(
        ApplicationProvider.getApplicationContext<Context>(),
        View(ApplicationProvider.getApplicationContext()),
    ).menu

    private fun createActivity(): ComponentActivity {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java)
        activityController = controller
        return controller.get().apply {
            setTheme(R.style.Theme_Legacy_Test)
            controller.setup()
        }
    }

    private companion object {
        const val ACTION_NEVER = 1
        const val ACTION_IF_ROOM = 2
        const val ACTION_ALWAYS = 3
    }
}
