package com.fsck.k9.ui

import android.content.res.ColorStateList
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.runtime.Composable
import androidx.fragment.app.Fragment
import com.fsck.k9.activity.DualScreenPopupActionList
import com.fsck.k9.activity.createAnchoredPopupContentView
import com.fsck.k9.activity.visibleItems
import com.fsck.k9.ui.base.BaseActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import kotlinx.collections.immutable.toImmutableList
import net.thunderbird.core.ui.theme.api.FeatureThemeProvider
import app.k9mail.core.ui.legacy.designsystem.R as DesignSystemR
import com.google.android.material.R as MaterialR

internal class AnchoredPopupMenuController(
    private val fragment: Fragment,
    private val featureThemeProvider: FeatureThemeProvider,
    private val onMenuItemSelected: (MenuItem) -> Boolean,
) {
    private var popupWindow: PopupWindow? = null

    val usesInCanvasPopup: Boolean
        get() = (fragment.activity as? BaseActivity)?.usesDualScreenInCanvasPopups == true

    fun prepareSubMenuAction(
        menu: Menu,
        itemId: Int,
        @DrawableRes icon: Int,
    ) {
        val menuItem = menu.findItem(itemId)
        if (!usesInCanvasPopup) {
            menuItem.actionView = null
            return
        }

        val actions = menuItem.subMenu?.visibleItems().orEmpty()
        menuItem.actionView = createActionButton(
            icon = icon,
            contentDescription = menuItem.title ?: "",
        ) { anchor ->
            showActionList(anchor, actions, onMenuItemSelected)
        }
    }

    fun prepareOverflowMenu(
        menu: Menu,
        overflowActionIds: List<Int>,
        onActionSelected: (MenuItem) -> Boolean = onMenuItemSelected,
    ) {
        val moreItem = menu.findItem(R.id.dual_screen_more_actions)
        if (!usesInCanvasPopup) {
            moreItem.isVisible = false
            return
        }

        val overflowActions = overflowActionIds.mapNotNull { itemId ->
            menu.findItem(itemId)?.takeIf(MenuItem::isVisible)
        }
        overflowActions.forEach { it.isVisible = false }

        moreItem.isVisible = overflowActions.isNotEmpty()
        moreItem.actionView = createActionButton(
            icon = DesignSystemR.drawable.ic_more_vert,
            contentDescription = moreItem.title ?: "",
        ) { anchor ->
            showActionList(anchor, overflowActions, onActionSelected)
        }
    }

    fun createActionButton(
        @DrawableRes icon: Int,
        contentDescription: CharSequence,
        onClick: (View) -> Unit,
    ): MaterialButton {
        return MaterialButton(
            fragment.requireContext(),
            null,
            MaterialR.attr.materialIconButtonStyle,
        ).apply {
            val color = MaterialColors.getColor(this, MaterialR.attr.colorOnSurface)
            iconTint = ColorStateList.valueOf(color)
            this.icon = AppCompatResources.getDrawable(context, icon)
            this.contentDescription = contentDescription
            setOnClickListener(onClick)
        }
    }

    fun show(anchor: View, content: @Composable () -> Unit) {
        dismiss()

        val popupContent = createAnchoredPopupContentView(
            context = anchor.context,
            lifecycleOwner = fragment.viewLifecycleOwner,
            savedStateRegistryOwner = fragment,
            featureThemeProvider = featureThemeProvider,
            content = content,
        )

        val activity = fragment.activity as? BaseActivity
        if (activity?.showDualScreenInCanvasPopup(anchor, popupContent) != true) {
            popupWindow = PopupWindow(
                popupContent,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true,
            ).also { popup ->
                popup.setOnDismissListener {
                    if (popupWindow === popup) popupWindow = null
                }
                popup.showAsDropDown(anchor)
            }
        }
    }

    fun dismiss() {
        popupWindow?.dismiss()
        popupWindow = null
        (fragment.activity as? BaseActivity)?.dismissDualScreenInCanvasPopup()
    }

    private fun showActionList(
        anchor: View,
        actions: List<MenuItem>,
        onActionSelected: (MenuItem) -> Boolean,
    ) {
        val activity = fragment.activity as? BaseActivity
        if (activity?.showDualScreenPopupMenu(anchor, actions, onActionSelected) == true) return

        show(anchor) {
            DualScreenPopupActionList(
                actions = actions.toImmutableList(),
                onClick = { action ->
                    val subMenuActions = action.subMenu?.visibleItems().orEmpty()
                    if (subMenuActions.isNotEmpty()) {
                        showActionList(anchor, subMenuActions, onActionSelected)
                    } else {
                        dismiss()
                        onActionSelected(action)
                    }
                },
            )
        }
    }
}
