package com.fsck.k9.activity

import android.hardware.display.DisplayManager
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import com.fsck.k9.ui.base.DualScreenActivityController
import com.fsck.k9.ui.base.DualScreenActivityControllerFactory
import com.fsck.k9.ui.base.getDisplayIdCompat
import net.thunderbird.core.preference.DualScreenMode
import net.thunderbird.core.preference.display.coreSettings.DisplayCoreSettingsPreferenceManager
import net.thunderbird.core.ui.theme.api.FeatureThemeProvider

internal class DefaultDualScreenActivityControllerFactory(
    private val displayCoreSettingsPreferenceManager: DisplayCoreSettingsPreferenceManager,
    private val featureThemeProvider: FeatureThemeProvider,
) : DualScreenActivityControllerFactory {
    override fun create(activity: ComponentActivity, sourceView: View): DualScreenActivityController {
        val savedMode = displayCoreSettingsPreferenceManager.getConfig().dualScreenMode
        val displaySelector = DualScreenDisplaySelector(
            displayManager = activity.getSystemService(DisplayManager::class.java),
        )
        val displayTarget = displaySelector.findEligibleSecondaryDisplay(activity.getDisplayIdCompat())
        val workspace = DefaultDualScreenActivityWorkspace.resolve(
            savedMode = savedMode,
            deviceProfile = displayTarget?.deviceProfile,
        )

        return DefaultDualScreenActivityController(
            activity = activity,
            sourceView = sourceView,
            savedMode = savedMode,
            displaySelector = displaySelector,
            workspace = workspace,
            featureThemeProvider = featureThemeProvider,
        )
    }
}

private class DefaultDualScreenActivityController(
    activity: ComponentActivity,
    sourceView: View,
    savedMode: DualScreenMode,
    displaySelector: DualScreenDisplaySelector,
    workspace: DefaultDualScreenActivityWorkspace,
    featureThemeProvider: FeatureThemeProvider,
) : DualScreenActivityController {
    private val spanCoordinator: DualScreenSpanCoordinator
    private var runtimeState = DualScreenRuntimeState.SINGLE_SCREEN
    private val popupBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            if (dismissInCanvasPopup()) return

            isEnabled = false
            activity.onBackPressedDispatcher.onBackPressed()
        }
    }
    private val popupHost = (sourceView as? ViewGroup)?.let { rootView ->
        DualScreenPopupHost(
            activity = activity,
            rootView = rootView,
            viewportHeightProvider = {
                workspace.deviceProfile?.logicalViewportHeight ?: rootView.height / DUAL_SCREEN_VIEWPORT_COUNT
            },
            featureThemeProvider = featureThemeProvider,
            isAvailable = { runtimeState.usesProjectedCanvas },
            onVisibilityChanged = { isVisible -> popupBackCallback.isEnabled = isVisible },
        )
    }
    private val toolbarOverflowMenuController = DualScreenToolbarOverflowMenuController(
        activity = activity,
        showPopupMenu = { anchor, actions, onMenuItemSelected ->
            showPopupMenu(anchor, actions, onMenuItemSelected)
        },
    )

    init {
        activity.onBackPressedDispatcher.addCallback(activity, popupBackCallback)
        spanCoordinator = DualScreenSpanCoordinator(
            activity = activity,
            sourceView = sourceView,
            dualScreenMode = savedMode,
            preparedRuntimeState = workspace.runtimeState,
            preparedDeviceProfile = workspace.deviceProfile,
            displaySelector = displaySelector,
            beforeSecondaryTouchDispatch = { event -> popupHost?.dismissIfOutsideLogicalTouch(event) },
            onRuntimeStateChanged = { state ->
                runtimeState = state
                if (!state.usesProjectedCanvas) dismissInCanvasPopup()
                activity.invalidateMenu()
            },
            onRecoveryAvailabilityChanged = { recoveryAvailable ->
                if (recoveryAvailable) spanCoordinator.resumeAfterDisplayReconnect()
            },
        )
    }

    override fun start() = spanCoordinator.start()

    override fun stop() {
        dismissInCanvasPopup()
        toolbarOverflowMenuController.clear()
        spanCoordinator.stop()
    }

    override fun destroy() {
        dismissInCanvasPopup()
        toolbarOverflowMenuController.clear()
        popupBackCallback.remove()
        spanCoordinator.destroy()
    }

    override val usesInCanvasPopups: Boolean
        get() = runtimeState.usesProjectedCanvas

    override fun prepareOptionsMenu(menu: Menu) {
        toolbarOverflowMenuController.prepare(menu, usesInCanvasPopups)
    }

    override fun onPrimaryTouchEvent(event: MotionEvent) {
        popupHost?.dismissIfOutsideWindowTouch(event)
    }

    override fun showInCanvasPopup(anchor: View, content: View): Boolean {
        return popupHost?.show(anchor, content) == true
    }

    override fun showPopupMenu(
        anchor: View,
        actions: List<MenuItem>,
        onMenuItemSelected: (MenuItem) -> Boolean,
    ): Boolean {
        return popupHost?.showMenu(anchor, actions, onMenuItemSelected) == true
    }

    override fun dismissInCanvasPopup(): Boolean {
        return popupHost?.dismiss() == true
    }

    private companion object {
        const val DUAL_SCREEN_VIEWPORT_COUNT = 2
    }
}

internal data class DefaultDualScreenActivityWorkspace(
    val runtimeState: DualScreenRuntimeState,
    val deviceProfile: DualScreenDeviceProfile?,
) {
    companion object {
        fun resolve(
            savedMode: DualScreenMode,
            deviceProfile: DualScreenDeviceProfile?,
        ): DefaultDualScreenActivityWorkspace {
            val runtimeState = DualScreenRuntimeState.resolve(
                savedMode = savedMode,
                isEligibleSecondaryDisplayAvailable = deviceProfile != null,
            )

            return DefaultDualScreenActivityWorkspace(runtimeState, deviceProfile)
        }
    }
}
