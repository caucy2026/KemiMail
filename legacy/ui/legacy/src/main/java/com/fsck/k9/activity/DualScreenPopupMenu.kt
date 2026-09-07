package com.fsck.k9.activity

import android.content.Context
import android.content.ContextWrapper
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.appcompat.widget.PopupMenu
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.compositionContext
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import app.k9mail.core.ui.compose.designsystem.atom.Surface
import app.k9mail.core.ui.compose.designsystem.atom.text.TextBodyLarge
import com.fsck.k9.ui.base.BaseActivity
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import net.thunderbird.core.ui.compose.designsystem.atom.ClickableSurface
import net.thunderbird.core.ui.compose.theme2.MainTheme
import net.thunderbird.core.ui.theme.api.FeatureThemeProvider

/**
 * Routes an anchored menu into the shared dual-screen canvas when its anchor is rendered there.
 *
 * Native [PopupMenu] windows are tied to the Activity display and therefore appear on the lower physical display.
 * Callers keep using a native popup on regular displays and only delegate here for the projected canvas.
 */
object DualScreenPopupMenu {
    @JvmStatic
    fun showIfNeeded(
        anchor: View,
        menu: Menu,
        onMenuItemClickListener: PopupMenu.OnMenuItemClickListener,
    ): Boolean {
        val activity = anchor.context.findBaseActivity() ?: return false
        return activity.showDualScreenPopupMenu(
            anchor = anchor,
            actions = menu.visibleItems(),
            onMenuItemSelected = onMenuItemClickListener::onMenuItemClick,
        )
    }
}

internal class DualScreenPopupHost(
    private val activity: ComponentActivity,
    rootView: ViewGroup,
    viewportHeightProvider: () -> Int,
    private val featureThemeProvider: FeatureThemeProvider,
    private val isAvailable: () -> Boolean,
    private val onVisibilityChanged: (Boolean) -> Unit = {},
) {
    private val popupController = DualScreenAnchoredPopupController(rootView, viewportHeightProvider)

    fun show(anchor: View, content: View): Boolean {
        if (!isAvailable() || !popupController.canAnchor(anchor)) return false

        popupController.show(anchor, content)
        onVisibilityChanged(true)
        return true
    }

    fun showMenu(
        anchor: View,
        actions: List<MenuItem>,
        onMenuItemSelected: (MenuItem) -> Boolean,
    ): Boolean {
        if (!isAvailable() || !popupController.canAnchor(anchor) || actions.isEmpty()) return false

        showMenuPage(anchor, actions, onMenuItemSelected)
        onVisibilityChanged(true)
        return true
    }

    fun dismiss(): Boolean {
        val dismissed = popupController.dismiss()
        if (dismissed) onVisibilityChanged(false)
        return dismissed
    }

    fun dismissIfOutsideWindowTouch(event: android.view.MotionEvent) {
        popupController.dismissIfOutsideWindowTouch(event)
        if (!popupController.isShowing) onVisibilityChanged(false)
    }

    fun dismissIfOutsideLogicalTouch(event: android.view.MotionEvent) {
        popupController.dismissIfOutsideLogicalTouch(event)
        if (!popupController.isShowing) onVisibilityChanged(false)
    }

    private fun showMenuPage(
        anchor: View,
        actions: List<MenuItem>,
        onMenuItemSelected: (MenuItem) -> Boolean,
    ) {
        val popupContent = createAnchoredPopupContentView(
            context = anchor.context,
            lifecycleOwner = activity,
            savedStateRegistryOwner = activity,
            featureThemeProvider = featureThemeProvider,
        ) {
            DualScreenPopupActionList(
                actions = actions.toImmutableList(),
                onClick = { action ->
                    val subMenuActions = action.subMenu?.visibleItems().orEmpty()
                    if (subMenuActions.isNotEmpty()) {
                        showMenuPage(anchor, subMenuActions, onMenuItemSelected)
                    } else {
                        dismiss()
                        onMenuItemSelected(action)
                    }
                },
            )
        }
        popupController.show(anchor, popupContent)
    }
}

internal fun createAnchoredPopupContentView(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    savedStateRegistryOwner: SavedStateRegistryOwner,
    featureThemeProvider: FeatureThemeProvider,
    content: @Composable () -> Unit,
): FrameLayout {
    val composeView = ComposeView(context).apply {
        setViewTreeLifecycleOwner(lifecycleOwner)
        setViewTreeSavedStateRegistryOwner(savedStateRegistryOwner)
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            featureThemeProvider.WithTheme {
                content()
            }
        }
    }

    return FrameLayout(context).apply {
        id = android.R.id.content
        compositionContext = createLifecycleAwareWindowRecomposer(lifecycle = lifecycleOwner.lifecycle)
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        addView(
            composeView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
    }
}

@Composable
internal fun DualScreenPopupActionList(
    actions: ImmutableList<MenuItem>,
    onClick: (MenuItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .padding(MainTheme.spacings.default)
            .widthIn(min = MENU_MIN_WIDTH, max = MENU_MAX_WIDTH)
            .width(IntrinsicSize.Max),
        color = MainTheme.colors.surfaceContainer,
        tonalElevation = MainTheme.elevations.level2,
        shape = MainTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
        ) {
            actions.forEach { action ->
                ClickableSurface(
                    onClick = { onClick(action) },
                    color = MainTheme.colors.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TextBodyLarge(
                        text = action.title.toString(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(
                            vertical = MainTheme.spacings.default,
                            horizontal = MainTheme.spacings.double,
                        ),
                    )
                }
            }
        }
    }
}

internal fun Menu.visibleItems(): List<MenuItem> {
    return buildList {
        repeat(size()) { index ->
            getItem(index).takeIf(MenuItem::isVisible)?.let(::add)
        }
    }
}

private tailrec fun Context.findBaseActivity(): BaseActivity? {
    return when (this) {
        is BaseActivity -> this
        is ContextWrapper -> baseContext.findBaseActivity()
        else -> null
    }
}

private val MENU_MIN_WIDTH = 196.dp
private val MENU_MAX_WIDTH = 320.dp
