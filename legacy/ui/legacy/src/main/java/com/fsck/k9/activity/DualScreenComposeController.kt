package com.fsck.k9.activity

import android.content.res.ColorStateList
import android.hardware.display.DisplayManager
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import com.fsck.k9.mailstore.MessageViewInfo
import com.fsck.k9.message.SimpleMessageFormat
import com.fsck.k9.message.extractors.BodyTextExtractor
import com.fsck.k9.ui.R
import com.fsck.k9.ui.base.getDisplayIdCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import kotlinx.collections.immutable.toImmutableList
import net.thunderbird.core.preference.DualScreenMode
import net.thunderbird.core.ui.theme.api.FeatureThemeProvider
import app.k9mail.core.ui.legacy.designsystem.R as DesignSystemR
import com.google.android.material.R as MaterialR

/**
 * Adds dual-screen presentation to the existing single-authority compose Activity.
 *
 * The lower workspace contains the only editable recipients, body, attachments, draft, and send path. The upper
 * smart workspace is a transient, read-only rendering of the source message and never persists its own mail state.
 */
internal class DualScreenComposeController private constructor(
    private val activity: MessageCompose,
    private val savedMode: DualScreenMode,
    private val displaySelector: DualScreenDisplaySelector,
    private val initialDisplayTarget: DualScreenDisplayTarget?,
    private val workspace: DualScreenComposeWorkspace,
    private val recoveryViewModel: DualScreenRecoveryViewModel,
    private val featureThemeProvider: FeatureThemeProvider,
) {
    @get:LayoutRes
    val layoutResource: Int = workspace.layoutResource

    private var spanCoordinator: DualScreenSpanCoordinator? = null
    private var referenceView: DualScreenComposeReferenceView? = null
    private var composePopupController: DualScreenComposePopupController? = null
    private var recoverySnackbar: Snackbar? = null
    private var workspaceRecreationRequested = false
    private var currentRuntimeState = workspace.runtimeState

    fun attach(activityContent: ViewGroup, authoritativeRootView: View) {
        check(spanCoordinator == null)

        composePopupController = DualScreenComposePopupController(
            activity = activity,
            rootView = authoritativeRootView as ViewGroup,
            viewportHeightProvider = {
                workspace.deviceProfile?.logicalViewportHeight ?: authoritativeRootView.height
            },
            featureThemeProvider = featureThemeProvider,
        )

        workspace.deviceProfile?.takeIf { workspace.runtimeState.usesSmartWorkspace }?.let { deviceProfile ->
            SmartDualScreenComposeLayoutConfigurator.apply(authoritativeRootView, deviceProfile)
            referenceView = DualScreenComposeReferenceView.create(authoritativeRootView)
        }

        spanCoordinator = DualScreenSpanCoordinator(
            activity = activity,
            sourceView = authoritativeRootView,
            dualScreenMode = savedMode,
            preparedRuntimeState = workspace.runtimeState,
            preparedDeviceProfile = initialDisplayTarget?.deviceProfile,
            displaySelector = displaySelector,
            secondaryViewportContentDescription =
            activity.getText(R.string.dual_screen_compose_reference_description),
            initialRecoveryPending = recoveryViewModel.recoveryPending,
            beforeSecondaryTouchDispatch = { event ->
                composePopupController?.dismissIfOutsideLogicalTouch(event)
            },
            onRuntimeStateChanged = { state ->
                currentRuntimeState = state
                if (!state.usesProjectedCanvas) composePopupController?.dismiss()
                activity.invalidateOptionsMenu()
            },
            onRecoveryPendingChanged = { recoveryPending ->
                recoveryViewModel.recoveryPending = recoveryPending
            },
            onRecoveryAvailabilityChanged = { recoveryAvailable ->
                updateRecoveryPrompt(activityContent, recoveryAvailable)
            },
            onSmartWorkspaceLost = ::recreateWorkspaceOnce,
        )
    }

    fun showInitialReference(action: MessageCompose.Action) {
        referenceView?.showInitial(action)
    }

    fun showSourceReference(messageViewInfo: MessageViewInfo?, action: MessageCompose.Action) {
        if (!action.usesSourceReference()) return

        if (messageViewInfo == null) {
            referenceView?.showUnavailable()
        } else {
            referenceView?.showSource(DualScreenComposeReferenceContent.from(messageViewInfo))
        }
    }

    fun setStarted(started: Boolean) {
        if (started) {
            spanCoordinator?.start()
        } else {
            composePopupController?.dismiss()
            spanCoordinator?.stop()
        }
    }

    fun destroy() {
        recoverySnackbar?.dismiss()
        recoverySnackbar = null
        referenceView = null
        composePopupController?.dismiss()
        composePopupController = null
        spanCoordinator?.destroy()
        spanCoordinator = null
    }

    fun prepareOptionsMenu(menu: Menu, prepareBaseMenu: Runnable) {
        composePopupController?.prepareOptionsMenu(
            menu = menu,
            prepareBaseMenu = prepareBaseMenu,
            usesProjectedCanvas = currentRuntimeState.usesProjectedCanvas,
        ) ?: prepareBaseMenu.run()
    }

    fun handlePopupInteraction(event: MotionEvent?): Boolean {
        return composePopupController?.handleInteraction(event) == true
    }

    fun showPopupMenuIfNeeded(
        anchor: View,
        menu: Menu,
        onMenuItemClickListener: PopupMenu.OnMenuItemClickListener,
    ): Boolean {
        return composePopupController?.showMenuIfNeeded(
            anchor = anchor,
            menu = menu,
            onMenuItemClickListener = onMenuItemClickListener,
            usesProjectedCanvas = currentRuntimeState.usesProjectedCanvas,
        ) == true
    }

    private fun updateRecoveryPrompt(anchor: View, recoveryAvailable: Boolean) {
        if (!recoveryAvailable) {
            recoverySnackbar?.dismiss()
            recoverySnackbar = null
            return
        }
        if (recoverySnackbar?.isShown == true) return

        recoverySnackbar = Snackbar.make(
            anchor,
            R.string.dual_screen_recovery_available,
            Snackbar.LENGTH_INDEFINITE,
        ).setAction(R.string.dual_screen_recovery_action) {
            val recreateWorkspace = spanCoordinator?.resumeAfterDisplayReconnect() == true
            if (recreateWorkspace) recreateWorkspaceOnce()
        }.also(Snackbar::show)
    }

    private fun recreateWorkspaceOnce() {
        if (workspaceRecreationRequested) return

        workspaceRecreationRequested = true
        activity.recreate()
    }

    companion object {
        @JvmStatic
        fun create(
            activity: MessageCompose,
            savedMode: DualScreenMode,
            featureThemeProvider: FeatureThemeProvider,
        ): DualScreenComposeController {
            val displaySelector = DualScreenDisplaySelector(activity.getSystemService(DisplayManager::class.java))
            val initialDisplayTarget = displaySelector.findEligibleSecondaryDisplay(activity.getDisplayIdCompat())
            val recoveryViewModel = ViewModelProvider(activity)[DualScreenRecoveryViewModel::class.java]
            val workspace = DualScreenComposeWorkspace.resolve(
                savedMode = savedMode,
                deviceProfile = initialDisplayTarget?.deviceProfile,
                recoveryPending = recoveryViewModel.recoveryPending,
            )

            return DualScreenComposeController(
                activity = activity,
                savedMode = savedMode,
                displaySelector = displaySelector,
                initialDisplayTarget = initialDisplayTarget,
                workspace = workspace,
                recoveryViewModel = recoveryViewModel,
                featureThemeProvider = featureThemeProvider,
            )
        }
    }
}

private class DualScreenComposePopupController(
    private val activity: MessageCompose,
    rootView: ViewGroup,
    viewportHeightProvider: () -> Int,
    private val featureThemeProvider: FeatureThemeProvider,
) {
    private val anchoredPopupController = DualScreenAnchoredPopupController(rootView, viewportHeightProvider)
    private val overflowMenuState = DualScreenComposeOverflowMenuState()
    private val composeMenuItemClickListener = PopupMenu.OnMenuItemClickListener(activity::onOptionsItemSelected)
    private var usesProjectedCanvas = false

    fun prepareOptionsMenu(menu: Menu, prepareBaseMenu: Runnable, usesProjectedCanvas: Boolean) {
        overflowMenuState.restore(menu)
        prepareBaseMenu.run()
        overflowMenuState.prepare(menu, usesProjectedCanvas)
        this.usesProjectedCanvas = usesProjectedCanvas

        val moreItem = menu.findItem(R.id.dual_screen_more_actions)
        moreItem.actionView = if (moreItem.isVisible) createMoreActionButton(moreItem.title ?: "") else null
    }

    fun handleInteraction(event: MotionEvent?): Boolean {
        if (event == null) return anchoredPopupController.dismiss()

        anchoredPopupController.dismissIfOutsideWindowTouch(event)
        return false
    }

    fun dismissIfOutsideLogicalTouch(event: MotionEvent) {
        anchoredPopupController.dismissIfOutsideLogicalTouch(event)
    }

    fun dismiss() {
        anchoredPopupController.dismiss()
    }

    fun showMenuIfNeeded(
        anchor: View,
        menu: Menu,
        onMenuItemClickListener: PopupMenu.OnMenuItemClickListener,
        usesProjectedCanvas: Boolean,
    ): Boolean {
        val actions = menu.visibleItems()
        val shouldShowInCanvas = DualScreenComposePopupRouting.shouldShowInCanvas(
            usesProjectedCanvas = usesProjectedCanvas,
            canAnchor = anchoredPopupController.canAnchor(anchor),
            hasVisibleActions = actions.isNotEmpty(),
        )
        if (!shouldShowInCanvas) return false

        this.usesProjectedCanvas = true
        showMenuPage(anchor, actions, onMenuItemClickListener)
        return true
    }

    private fun createMoreActionButton(contentDescription: CharSequence): MaterialButton {
        return MaterialButton(
            activity,
            null,
            MaterialR.attr.materialIconButtonStyle,
        ).apply {
            val color = MaterialColors.getColor(this, MaterialR.attr.colorOnSurface)
            iconTint = ColorStateList.valueOf(color)
            icon = AppCompatResources.getDrawable(context, DesignSystemR.drawable.ic_more_vert)
            this.contentDescription = contentDescription
            setOnClickListener(::showOverflowMenu)
        }
    }

    private fun showOverflowMenu(anchor: View) {
        showMenuPage(anchor, overflowMenuState.visibleActions, composeMenuItemClickListener)
    }

    private fun showMenuPage(
        anchor: View,
        actions: List<MenuItem>,
        onMenuItemClickListener: PopupMenu.OnMenuItemClickListener,
    ) {
        if (!usesProjectedCanvas || actions.isEmpty()) return

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
                        showMenuPage(anchor, subMenuActions, onMenuItemClickListener)
                    } else {
                        anchoredPopupController.dismiss()
                        onMenuItemClickListener.onMenuItemClick(action)
                    }
                },
            )
        }
        anchoredPopupController.show(anchor, popupContent)
    }
}

internal object DualScreenComposePopupRouting {
    fun shouldShowInCanvas(
        usesProjectedCanvas: Boolean,
        canAnchor: Boolean,
        hasVisibleActions: Boolean,
    ): Boolean = usesProjectedCanvas && canAnchor && hasVisibleActions
}

internal class DualScreenComposeOverflowMenuState(
    private val overflowActionIds: List<Int> = listOf(
        R.id.add_from_contacts,
        R.id.save,
        R.id.discard,
        R.id.read_receipt,
        R.id.openpgp_encrypt_enable,
        R.id.openpgp_encrypt_disable,
        R.id.openpgp_sign_only,
        R.id.openpgp_sign_only_disable,
        R.id.openpgp_inline_enable,
        R.id.openpgp_inline_disable,
    ),
) {
    private val visibilityBeforeHiding = mutableMapOf<Int, Boolean>()

    var visibleActions: List<MenuItem> = emptyList()
        private set

    fun restore(menu: Menu) {
        visibilityBeforeHiding.forEach { (itemId, wasVisible) ->
            menu.findItem(itemId)?.isVisible = wasVisible
        }
        visibilityBeforeHiding.clear()
        visibleActions = emptyList()
    }

    fun prepare(menu: Menu, usesProjectedCanvas: Boolean) {
        val moreItem = menu.findItem(R.id.dual_screen_more_actions)
        if (!usesProjectedCanvas) {
            moreItem.isVisible = false
            visibleActions = emptyList()
            return
        }

        val overflowItems = overflowActionIds.mapNotNull(menu::findItem)
        overflowItems.forEach { item -> visibilityBeforeHiding[item.itemId] = item.isVisible }
        visibleActions = overflowItems.filter(MenuItem::isVisible)
        visibleActions.forEach { it.isVisible = false }
        moreItem.isVisible = visibleActions.isNotEmpty()
    }
}

internal data class DualScreenComposeWorkspace(
    val runtimeState: DualScreenRuntimeState,
    val deviceProfile: DualScreenDeviceProfile?,
) {
    @get:LayoutRes
    val layoutResource: Int = if (runtimeState.usesSmartWorkspace) {
        R.layout.smart_message_compose
    } else {
        R.layout.message_compose
    }

    companion object {
        fun resolve(
            savedMode: DualScreenMode,
            deviceProfile: DualScreenDeviceProfile?,
            recoveryPending: Boolean,
        ): DualScreenComposeWorkspace {
            val resolvedState = DualScreenRuntimeState.resolve(
                savedMode = savedMode,
                isEligibleSecondaryDisplayAvailable = deviceProfile != null,
            )
            val initialState = if (recoveryPending) DualScreenRuntimeState.SINGLE_SCREEN else resolvedState

            return DualScreenComposeWorkspace(initialState, deviceProfile)
        }
    }
}

internal object SmartDualScreenComposeLayoutConfigurator {
    fun apply(rootView: View, deviceProfile: DualScreenDeviceProfile) {
        val referencePanel = checkNotNull(rootView.findViewById<View>(R.id.dual_screen_compose_reference_panel))
        val editorPanel = checkNotNull(rootView.findViewById<View>(R.id.dual_screen_compose_editor_panel))

        setViewportHeight(referencePanel, deviceProfile.logicalViewportHeight)
        setViewportHeight(editorPanel, deviceProfile.logicalViewportHeight)
    }

    private fun setViewportHeight(view: View, viewportHeight: Int) {
        view.layoutParams = (view.layoutParams as LinearLayout.LayoutParams).apply {
            height = viewportHeight
            weight = 0f
        }
    }
}

internal data class DualScreenComposeReferenceContent(
    val subject: String,
    val body: String,
    val truncated: Boolean,
) {
    companion object {
        private const val MAX_BODY_CHARACTERS = 100_000

        fun from(messageViewInfo: MessageViewInfo): DualScreenComposeReferenceContent {
            val body = messageViewInfo.rootPart?.let { rootPart ->
                BodyTextExtractor.getBodyTextFromMessage(rootPart, SimpleMessageFormat.TEXT)
            }.orEmpty()

            return fromText(messageViewInfo.subject, body)
        }

        fun fromText(subject: String?, body: String): DualScreenComposeReferenceContent {
            val truncated = body.length > MAX_BODY_CHARACTERS
            return DualScreenComposeReferenceContent(
                subject = subject.orEmpty(),
                body = if (truncated) body.take(MAX_BODY_CHARACTERS) else body,
                truncated = truncated,
            )
        }
    }
}

private class DualScreenComposeReferenceView private constructor(rootView: View) {
    private val guidanceView = rootView.findViewById<TextView>(R.id.dual_screen_compose_reference_guidance)
    private val subjectView = rootView.findViewById<TextView>(R.id.dual_screen_compose_reference_subject)
    private val bodyView = rootView.findViewById<TextView>(R.id.dual_screen_compose_reference_body)
    private val truncatedView = rootView.findViewById<TextView>(R.id.dual_screen_compose_reference_truncated)

    fun showInitial(action: MessageCompose.Action) {
        guidanceView.setText(action.initialReferenceText())
        subjectView.isVisible = false
        bodyView.isVisible = false
        truncatedView.isVisible = false
    }

    fun showSource(content: DualScreenComposeReferenceContent) {
        guidanceView.setText(R.string.dual_screen_compose_reference_source_hint)
        if (content.subject.isBlank()) {
            subjectView.setText(R.string.dual_screen_compose_reference_no_subject)
        } else {
            subjectView.text = content.subject
        }
        if (content.body.isBlank()) {
            bodyView.setText(R.string.dual_screen_compose_reference_no_body)
        } else {
            bodyView.text = content.body
        }
        subjectView.isVisible = true
        bodyView.isVisible = true
        truncatedView.isVisible = content.truncated
    }

    fun showUnavailable() {
        guidanceView.setText(R.string.dual_screen_compose_reference_unavailable)
        subjectView.isVisible = false
        bodyView.isVisible = false
        truncatedView.isVisible = false
    }

    companion object {
        fun create(rootView: View): DualScreenComposeReferenceView {
            return DualScreenComposeReferenceView(rootView)
        }
    }
}

internal fun MessageCompose.Action.usesSourceReference(): Boolean {
    return this == MessageCompose.Action.REPLY ||
        this == MessageCompose.Action.REPLY_ALL ||
        this == MessageCompose.Action.FORWARD ||
        this == MessageCompose.Action.FORWARD_AS_ATTACHMENT
}

internal fun MessageCompose.Action.initialReferenceText(): Int = when (this) {
    MessageCompose.Action.COMPOSE -> R.string.dual_screen_compose_reference_compose_hint
    MessageCompose.Action.EDIT_DRAFT -> R.string.dual_screen_compose_reference_draft_hint
    MessageCompose.Action.REPLY,
    MessageCompose.Action.REPLY_ALL,
    MessageCompose.Action.FORWARD,
    MessageCompose.Action.FORWARD_AS_ATTACHMENT,
    -> R.string.dual_screen_compose_reference_loading_hint
}
