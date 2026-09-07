package com.fsck.k9.activity

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.Parcelable
import android.view.Gravity
import android.view.KeyEvent
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import android.widget.ProgressBar
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBar
import androidx.appcompat.view.ActionMode
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.isGone
import androidx.drawerlayout.widget.DrawerLayout
import androidx.drawerlayout.widget.DrawerLayout.DrawerListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.fragment.app.commit
import androidx.fragment.app.commitNow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.k9mail.core.android.common.compat.BundleCompat
import app.k9mail.core.android.common.contact.CachingRepository
import app.k9mail.core.android.common.contact.ContactRepository
import app.k9mail.core.ui.compose.common.window.FoldableState
import app.k9mail.core.ui.compose.common.window.FoldableStateObserver
import app.k9mail.core.ui.legacy.designsystem.atom.icon.Icons
import app.k9mail.feature.launcher.FeatureLauncherActivity
import app.k9mail.feature.launcher.FeatureLauncherTarget
import app.k9mail.legacy.message.controller.MessageReference
import com.fsck.k9.CoreResourceProvider
import com.fsck.k9.Preferences
import com.fsck.k9.activity.attachment.DualScreenAttachmentWorkspaceCoordinator
import com.fsck.k9.activity.attachment.canBeginDualScreenAttachmentDrag
import com.fsck.k9.activity.compose.DualScreenModeEntry
import com.fsck.k9.activity.compose.DualScreenModeEntryCallbacks
import com.fsck.k9.activity.compose.DualScreenModeEntryState
import com.fsck.k9.activity.compose.MessageActions
import com.fsck.k9.activity.smartassistant.SmartAssistantPanelCoordinator
import com.fsck.k9.activity.smartassistant.toSmartAssistantDraftReference
import com.fsck.k9.activity.smartassistant.toSmartAssistantMessageReference
import com.fsck.k9.controller.MessagingController
import com.fsck.k9.mailstore.AttachmentViewInfo
import com.fsck.k9.search.isUnifiedFolders
import com.fsck.k9.ui.BuildConfig
import com.fsck.k9.ui.R
import com.fsck.k9.ui.base.BaseActivity
import com.fsck.k9.ui.base.DefaultDisplayActivityRelocator
import com.fsck.k9.ui.base.getDisplayIdCompat
import com.fsck.k9.ui.managefolders.ManageFoldersActivity
import com.fsck.k9.ui.messagelist.DefaultFolderProvider
import com.fsck.k9.ui.messagelist.MessageListFragmentBridgeContract
import com.fsck.k9.ui.messagelist.MessageListFragmentBridgeContract.MessageListFragmentListener
import com.fsck.k9.ui.messageview.AttachmentDragCallbacks
import com.fsck.k9.ui.messageview.MessageViewContainerFragment
import com.fsck.k9.ui.messageview.MessageViewContainerFragment.MessageViewContainerListener
import com.fsck.k9.ui.messageview.MessageViewFragment.MessageViewFragmentListener
import com.fsck.k9.ui.messageview.PlaceholderFragment
import com.fsck.k9.ui.settings.SettingsActivity
import com.fsck.k9.view.ViewSwitcher
import com.fsck.k9.view.ViewSwitcher.OnSwitchCompleteListener
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textview.MaterialTextView
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import net.thunderbird.core.android.account.LegacyAccount
import net.thunderbird.core.android.account.LegacyAccountDto
import net.thunderbird.core.android.account.LegacyAccountDtoManager
import net.thunderbird.core.android.common.startup.DatabaseUpgradeInterceptor
import net.thunderbird.core.logging.Logger
import net.thunderbird.core.logging.legacy.Log
import net.thunderbird.core.preference.DualScreenMode
import net.thunderbird.core.preference.GeneralSettingsManager
import net.thunderbird.core.preference.SplitViewMode
import net.thunderbird.core.preference.display.coreSettings.DisplayCoreSettingsPreferenceManager
import net.thunderbird.core.preference.interaction.PostMarkAsUnreadNavigation
import net.thunderbird.core.preference.interaction.PostRemoveNavigation
import net.thunderbird.core.ui.theme.api.FeatureThemeProvider
import net.thunderbird.feature.account.storage.legacy.mapper.LegacyAccountDataMapper
import net.thunderbird.feature.funding.api.FundingManager
import net.thunderbird.feature.navigation.drawer.api.NavigationDrawer
import net.thunderbird.feature.navigation.drawer.dropdown.DropDownDrawer
import net.thunderbird.feature.navigation.drawer.dropdown.domain.entity.UnifiedDisplayAccount
import net.thunderbird.feature.search.legacy.LocalMessageSearch
import net.thunderbird.feature.search.legacy.SearchAccount
import net.thunderbird.feature.search.legacy.api.MessageSearchField
import net.thunderbird.feature.search.legacy.api.SearchAttribute
import net.thunderbird.feature.search.legacy.api.SearchCondition
import net.thunderbird.feature.search.legacy.serialization.LocalMessageSearchSerializer
import net.thunderbird.feature.smartassistant.SmartAssistantAccountReference
import net.thunderbird.feature.smartassistant.SmartAssistantContext
import net.thunderbird.feature.smartassistant.SmartAssistantPanelHost
import net.thunderbird.feature.smartassistant.SmartAssistantScene
import org.koin.android.ext.android.inject
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.parameter.parametersOf

private const val TAG = "MainActivity"

/**
 * The primary user interface of the application, responsible for displaying and managing the main screen.
 *
 * This Activity serves as the central hub for navigation and content display. It is launched when the
 * application is started from the launcher icon and also handles deep links, such as when a user taps on a
 * "View Message" notification.
 *
 * `MainActivity` manages the overall layout, including the navigation drawer and the main content area,
 * which currently displays either a [MessageListFragmentBridgeContract] or a [MessageViewContainerFragment].
 * It orchestrates the interactions between these fragments and handles the back stack. The responsibilities for
 * managing the action bar, search functionality, and single-pane/split-view layout logic are currently handled
 * here but are intended to be refactored into more dedicated components over time.
 */
@Suppress("TooManyFunctions", "LargeClass")
open class MessageHomeActivity :
    BaseActivity(),
    MessageListFragmentListener,
    MessageViewFragmentListener,
    MessageViewContainerListener,
    FragmentManager.OnBackStackChangedListener,
    OnSwitchCompleteListener {

    override fun useAutomaticDualScreenProjection(): Boolean = false

    private val preferences: Preferences by inject()
    private val accountManager: LegacyAccountDtoManager by inject()
    private val defaultFolderProvider: DefaultFolderProvider by inject()
    private val generalSettingsManager: GeneralSettingsManager by inject()
    private val displayCoreSettingsPreferenceManager: DisplayCoreSettingsPreferenceManager by inject()
    private val messagingController: MessagingController by inject()
    private val contactRepository: ContactRepository by inject()
    private val coreResourceProvider: CoreResourceProvider by inject()
    private val fundingManager: FundingManager by inject()
    private val logger: Logger by inject()
    private val legacyAccountDataMapper: LegacyAccountDataMapper by inject()
    private val databaseUpgradeInterceptor: DatabaseUpgradeInterceptor by inject()
    private val featureThemeProvider: FeatureThemeProvider by inject()
    private val smartAssistantPanelHost: SmartAssistantPanelHost by inject()

    private val foldableStateObserver: FoldableStateObserver by inject { parametersOf(this) }
    private val dualScreenRecoveryViewModel: DualScreenRecoveryViewModel by viewModels()

    private lateinit var actionBar: ActionBar

    private var navigationDrawer: NavigationDrawer? = null
    private var openFolderTransaction: FragmentTransaction? = null
    private var progressBar: ProgressBar? = null
    private var messageViewPlaceHolder: PlaceholderFragment? = null
    private val messageListFragmentFactory: MessageListFragmentBridgeContract.Factory by inject()
    private var messageListFragment: MessageListFragmentBridgeContract? = null
    private var messageViewContainerFragment: MessageViewContainerFragment? = null
    private var dualScreenSpanCoordinator: DualScreenSpanCoordinator? = null
    private var dualScreenPopupController: DualScreenAnchoredPopupController? = null
    private var dualScreenActionModeController: DualScreenActionModeController? = null
    private var smartAssistantPanelCoordinator: SmartAssistantPanelCoordinator? = null
    private var dualScreenAttachmentWorkspaceCoordinator: DualScreenAttachmentWorkspaceCoordinator? = null
    private var dualScreenModeSelectionController: DualScreenModeSelectionController? = null
    private var isDualScreenModeEntryVisible by mutableStateOf(false)
    private var isDualScreenModeDialogVisible by mutableStateOf(false)
    private var currentDualScreenRuntimeState = DualScreenRuntimeState.SINGLE_SCREEN
    private var dualScreenRecoverySnackbar: Snackbar? = null
    private lateinit var dualScreenDisplaySelector: DualScreenDisplaySelector
    private var initialDualScreenDisplayTarget: DualScreenDisplayTarget? = null
    private var savedDualScreenMode = DualScreenMode.IMMERSIVE
    private var initialDualScreenRuntimeState = DualScreenRuntimeState.SINGLE_SCREEN
    private var account: LegacyAccountDto? = null
    private var search: LocalMessageSearch? = null
    private var singleFolderMode = false

    private var messageListActivityConfig: MessageListActivityConfig? = null

    /**
     * `true` if the message list should be displayed as flat list (i.e. no threading)
     * regardless whether or not message threading was enabled in the settings. This is used for
     * filtered views, e.g. when only displaying the unread messages in a folder.
     */
    private var noThreading = false
    private var displayMode: DisplayMode = DisplayMode.MESSAGE_LIST
    private var messageReference: MessageReference? = null

    /**
     * If this is `true`, only the message view will be displayed and pressing the back button will finish the Activity.
     */
    private var messageViewOnly = false
    private var messageListWasDisplayed = false
    private var viewSwitcher: ViewSwitcher? = null
    private var firstDrawCompleted = false
    private var clearNotificationsAfterFirstDraw = false
    private var fullyDrawnReported = false

    private val isShowAccountIndicator: Boolean
        get() = messageListFragment?.isShowAccountIndicator ?: true

    @Suppress("ReturnCount")
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (DefaultDisplayActivityRelocator.relocateIfNeeded(this)) return

        if (databaseUpgradeInterceptor.checkAndHandleUpgrade(this, intent)) {
            finish()
            return
        }

        initializeDualScreenRuntime()

        when {
            initialDualScreenRuntimeState.usesSmartWorkspace -> setLayout(R.layout.smart_message_list)
            useSplitView() -> setLayout(R.layout.split_message_list)
            else -> {
                setLayout(R.layout.message_list)
                viewSwitcher = findViewById<ViewSwitcher>(R.id.container).apply {
                    firstInAnimation = AnimationUtils.loadAnimation(this@MessageHomeActivity, R.anim.slide_in_left)
                    firstOutAnimation = AnimationUtils.loadAnimation(this@MessageHomeActivity, R.anim.slide_out_right)
                    secondInAnimation = AnimationUtils.loadAnimation(this@MessageHomeActivity, R.anim.slide_in_right)
                    secondOutAnimation = AnimationUtils.loadAnimation(this@MessageHomeActivity, R.anim.slide_out_left)
                    setOnSwitchCompleteListener(this@MessageHomeActivity)
                }
            }
        }
        val activityContent = findViewById<ViewGroup>(android.R.id.content)
        val authoritativeRootView = activityContent.getChildAt(0)
        dualScreenPopupController = DualScreenAnchoredPopupController(
            rootView = authoritativeRootView as ViewGroup,
            viewportHeightProvider = { authoritativeRootView.height / DUAL_SCREEN_VIEWPORT_COUNT },
        )
        dualScreenActionModeController = DualScreenActionModeController(authoritativeRootView, menuInflater)
        configureInitialDualScreenLayout(authoritativeRootView)
        initializeSmartAssistantPanel()
        initializeDualScreenAttachmentWorkspace()
        initializeDualScreenExperience(activityContent, authoritativeRootView)

        initializeActionBar()
        initializeDrawer()

        if (!decodeExtras(intent)) {
            return
        }
        restoreRetainedState(savedInstanceState)

        if (isDrawerEnabled) {
            configureDrawer()
        }

        findFragments()
        initializeDisplayMode(savedInstanceState)
        initializeLayout()
        initializeFragments()
        displayViews()
        initializeAfterFirstDraw(authoritativeRootView)

        val backPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (dualScreenPopupController?.dismiss() == true) return
                if (dualScreenActionModeController?.finish() == true) return

                if (dualScreenAttachmentWorkspaceCoordinator?.handleBack() != true) {
                    this@MessageHomeActivity.handleOnBackPressed(this)
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, backPressedCallback)
    }

    private fun initializeSmartAssistantContext() {
        val activeMessage = SmartMessageListContextResolver.activeMessage(
            hasMessageView = messageViewContainerFragment != null,
            fragmentActiveMessage = messageListFragment?.activeMessage,
            launchMessage = messageReference,
        )
        updateSmartAssistantContext(
            scene = if (activeMessage != null) {
                SmartAssistantScene.MESSAGE_READING
            } else {
                SmartAssistantScene.MESSAGE_LIST
            },
            messageReference = activeMessage,
        )
    }

    private fun initializeAfterFirstDraw(rootView: View) {
        rootView.viewTreeObserver.addOnDrawListener(
            object : ViewTreeObserver.OnDrawListener {
                override fun onDraw() {
                    rootView.post {
                        if (rootView.viewTreeObserver.isAlive) {
                            rootView.viewTreeObserver.removeOnDrawListener(this)
                        }
                        if (isDestroyed) return@post

                        firstDrawCompleted = true
                        initializeSmartAssistantContext()
                        initializeFunding()
                        initializeFoldableObserver()

                        if (clearNotificationsAfterFirstDraw) {
                            clearNotificationsAfterFirstDraw = false
                            clearNotifications()
                        }
                    }
                }
            },
        )
    }

    private fun initializeDualScreenRuntime() {
        savedDualScreenMode = displayCoreSettingsPreferenceManager.getConfig().dualScreenMode
        dualScreenDisplaySelector = DualScreenDisplaySelector(getSystemService(DisplayManager::class.java))
        initialDualScreenDisplayTarget =
            dualScreenDisplaySelector.findEligibleSecondaryDisplay(getDisplayIdCompat())
        val resolvedRuntimeState = DualScreenRuntimeState.resolve(
            savedMode = savedDualScreenMode,
            isEligibleSecondaryDisplayAvailable = initialDualScreenDisplayTarget != null,
        )
        initialDualScreenRuntimeState = if (dualScreenRecoveryViewModel.recoveryPending) {
            DualScreenRuntimeState.SINGLE_SCREEN
        } else {
            resolvedRuntimeState
        }
        currentDualScreenRuntimeState = initialDualScreenRuntimeState
    }

    private fun configureInitialDualScreenLayout(authoritativeRootView: View) {
        if (!initialDualScreenRuntimeState.usesSmartWorkspace) return

        SmartDualScreenLayoutConfigurator.apply(
            rootView = authoritativeRootView,
            deviceProfile = checkNotNull(initialDualScreenDisplayTarget).deviceProfile,
        )
    }

    private fun initializeSmartAssistantPanel() {
        val container = findViewById<ViewGroup>(R.id.smart_assistant_panel_host) ?: return
        smartAssistantPanelCoordinator = SmartAssistantPanelCoordinator(
            container = container,
            panelHost = smartAssistantPanelHost,
        )
    }

    private fun initializeDualScreenAttachmentWorkspace() {
        val upperHost = findViewById<ViewGroup>(R.id.dual_screen_attachment_preview_host)
        val lowerHost = findViewById<ViewGroup>(R.id.dual_screen_attachment_action_host)
        val continuousReaderHost = findViewById<ViewGroup>(R.id.dual_screen_continuous_reader_host)
        if (upperHost != null && lowerHost != null && continuousReaderHost != null) {
            dualScreenAttachmentWorkspaceCoordinator = DualScreenAttachmentWorkspaceCoordinator(
                upperHost = upperHost,
                lowerHost = lowerHost,
                continuousReaderHost = continuousReaderHost,
                themeProvider = featureThemeProvider,
                onOpenExternally = { attachment ->
                    messageViewContainerFragment?.openAttachmentExternally(attachment)
                },
                onSave = { attachment ->
                    messageViewContainerFragment?.saveAttachment(attachment)
                },
            )
        }
    }

    private fun updateSmartAssistantContext(
        scene: SmartAssistantScene,
        messageReference: MessageReference? = null,
    ) {
        smartAssistantPanelCoordinator?.updateContext {
            val accountUuid = messageReference?.accountUuid ?: account?.uuid
            SmartAssistantContext(
                scene = scene,
                account = accountUuid?.let(::SmartAssistantAccountReference),
                folder = SmartMessageListContextResolver.folderReference(
                    activeMessage = messageReference,
                    currentAccountUuid = account?.uuid,
                    currentFolderIds = search?.folderIds.orEmpty(),
                ),
                message = messageReference?.takeIf { scene == SmartAssistantScene.MESSAGE_READING }
                    ?.toSmartAssistantMessageReference(),
                draft = messageReference?.takeIf { scene == SmartAssistantScene.DRAFT_COMPOSING }
                    ?.toSmartAssistantDraftReference(),
            )
        }
    }

    private fun initializeDualScreenExperience(
        activityContent: ViewGroup,
        authoritativeRootView: View,
    ) {
        dualScreenModeSelectionController = DualScreenModeSelectionController(
            initialMode = savedDualScreenMode,
            persistMode = ::persistDualScreenMode,
            recreateActivity = ::recreate,
        )
        initializeDualScreenModeEntry(
            activityContent = activityContent,
            currentMode = savedDualScreenMode,
            onModeSelected = { mode -> dualScreenModeSelectionController?.select(mode) },
        )
        dualScreenSpanCoordinator = DualScreenSpanCoordinator(
            activity = this,
            sourceView = authoritativeRootView,
            dualScreenMode = savedDualScreenMode,
            preparedRuntimeState = initialDualScreenRuntimeState,
            preparedDeviceProfile = initialDualScreenDisplayTarget?.deviceProfile,
            displaySelector = dualScreenDisplaySelector,
            initialRecoveryPending = dualScreenRecoveryViewModel.recoveryPending,
            beforeSecondaryTouchDispatch = { event ->
                dualScreenPopupController?.dismissIfOutsideLogicalTouch(event)
            },
            onRuntimeStateChanged = { state ->
                currentDualScreenRuntimeState = state
                if (!state.usesProjectedCanvas) {
                    dualScreenPopupController?.dismiss()
                    dualScreenActionModeController?.finish()
                }
                invalidateOptionsMenu()
                isDualScreenModeEntryVisible = state.isDualScreenAvailable
                if (!state.usesSmartWorkspace) {
                    dualScreenAttachmentWorkspaceCoordinator?.dismiss()
                }
                if (!state.isDualScreenAvailable) {
                    isDualScreenModeDialogVisible = false
                }
            },
            onRecoveryPendingChanged = { recoveryPending ->
                dualScreenRecoveryViewModel.recoveryPending = recoveryPending
            },
            onRecoveryAvailabilityChanged = { recoveryAvailable ->
                updateDualScreenRecoveryPrompt(activityContent, recoveryAvailable)
            },
            onSmartWorkspaceLost = ::recreate,
        )
    }

    private fun updateDualScreenRecoveryPrompt(activityContent: View, recoveryAvailable: Boolean) {
        if (!recoveryAvailable) {
            dualScreenRecoverySnackbar?.dismiss()
            dualScreenRecoverySnackbar = null
            return
        }
        if (dualScreenRecoverySnackbar?.isShown == true) return

        dualScreenRecoverySnackbar = Snackbar.make(
            activityContent,
            R.string.dual_screen_recovery_available,
            Snackbar.LENGTH_INDEFINITE,
        ).setAction(R.string.dual_screen_recovery_action) {
            val recreateWorkspace = dualScreenSpanCoordinator?.resumeAfterDisplayReconnect() == true
            if (recreateWorkspace) recreate()
        }.also(Snackbar::show)
    }

    private fun initializeDualScreenModeEntry(
        activityContent: ViewGroup,
        currentMode: DualScreenMode,
        onModeSelected: (DualScreenMode) -> Unit,
    ) {
        val modeEntry = ComposeView(this).apply {
            id = R.id.dual_screen_mode_entry
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        }
        val positionMover = DualScreenFloatingControlMover(
            view = modeEntry,
            direction = DualScreenFloatingControlEdge.START.inwardDirection(activityContent.layoutDirection),
            insetDistance = resources.getDimension(R.dimen.dualScreenFloatingControlInset),
            initialPosition = dualScreenRecoveryViewModel.modeControlPosition,
            onPositionChanged = { position -> dualScreenRecoveryViewModel.modeControlPosition = position },
        )
        modeEntry.setContent {
            featureThemeProvider.WithTheme {
                DualScreenModeEntry(
                    visible = isDualScreenModeEntryVisible,
                    state = DualScreenModeEntryState(
                        currentMode = currentMode,
                        dialogVisible = isDualScreenModeDialogVisible,
                    ),
                    callbacks = DualScreenModeEntryCallbacks(
                        onModeSelected = onModeSelected,
                        onDialogVisibilityChanged = { visible -> isDualScreenModeDialogVisible = visible },
                        onPositionDragStarted = positionMover::startDrag,
                        onPositionDragged = positionMover::dragBy,
                        onPositionDragFinished = positionMover::finishDrag,
                        onPositionToggle = positionMover::togglePosition,
                    ),
                )
            }
        }
        val layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.START or Gravity.BOTTOM,
        )
        activityContent.addView(modeEntry, layoutParams)
    }

    internal fun configureDualScreenComposeFloatingButton(button: FloatingActionButton) {
        if (!currentDualScreenRuntimeState.isDualScreenAvailable) return

        button.keepVisibleDuringDualScreenScrolling()
        button.applyDualScreenFloatingControlStyle()
        DualScreenFloatingControlMover(
            view = button,
            direction = DualScreenFloatingControlEdge.END.inwardDirection(button.layoutDirection),
            insetDistance = resources.getDimension(R.dimen.dualScreenFloatingControlInset),
            initialPosition = dualScreenRecoveryViewModel.composeControlPosition,
            onPositionChanged = { position -> dualScreenRecoveryViewModel.composeControlPosition = position },
        ).installTouchDrag(getString(R.string.dual_screen_floating_control_move))
    }

    private fun persistDualScreenMode(mode: DualScreenMode) {
        val currentSettings = displayCoreSettingsPreferenceManager.getConfig()
        displayCoreSettingsPreferenceManager.save(currentSettings.copy(dualScreenMode = mode))
    }

    private fun initializeFoldableObserver() {
        // Register lifecycle observer
        lifecycle.addObserver(foldableStateObserver)

        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                foldableStateObserver.foldableState
                    .collect { foldableState ->
                        if (generalSettingsManager.getConfig().display.coreSettings.splitViewMode ==
                            SplitViewMode.WHEN_UNFOLDED
                        ) {
                            handleFoldableStateChange(foldableState)
                        }
                    }
            }
        }
    }

    private fun handleFoldableStateChange(foldableState: FoldableState) {
        if (
            !shouldHandleFoldableStateChange(
                preparedState = initialDualScreenRuntimeState,
                currentState = currentDualScreenRuntimeState,
                recoveryPending = dualScreenRecoveryViewModel.recoveryPending,
            )
        ) {
            logger.debug(TAG) { "Ignoring foldable state change while dual-screen lifecycle is active" }
            return
        }

        logger.debug(TAG) { "Handling foldable state change: $foldableState" }

        val shouldUseSplitView = foldableState == FoldableState.UNFOLDED
        val isCurrentlySplitView = displayMode == DisplayMode.SPLIT_VIEW

        if (shouldUseSplitView && !isCurrentlySplitView) {
            // Switch to split view
            logger.debug(TAG) { "Switching to split view due to unfold" }
            recreateWithSplitView()
        } else if (!shouldUseSplitView && isCurrentlySplitView) {
            // Switch to single pane view
            logger.debug(TAG) { "Switching to single pane view due to fold" }
            recreateWithSinglePane()
        }
    }

    private fun recreateWithSplitView() {
        // Recreate activity to properly initialize split view layout
        recreate()
    }

    private fun recreateWithSinglePane() {
        // Recreate activity to properly initialize single pane layout
        recreate()
    }

    private fun initializeFunding() {
        fundingManager.addFundingReminder {
            FeatureLauncherActivity.launch(
                context = this,
                target = FeatureLauncherTarget.Funding,
            )
        }
    }

    @Suppress("ReturnCount")
    public override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)

        if (DefaultDisplayActivityRelocator.relocateIfNeeded(this, intent)) return

        if (databaseUpgradeInterceptor.checkAndHandleUpgrade(this, intent)) {
            finish()
            return
        }

        if (isFinishing) {
            return
        }

        setIntent(intent)

        // Start with a fresh fragment back stack
        supportFragmentManager.popBackStackImmediate(
            FIRST_FRAGMENT_TRANSACTION,
            FragmentManager.POP_BACK_STACK_INCLUSIVE,
        )

        removeMessageListFragment()
        removeMessageViewContainerFragment()

        messageReference = null
        search = null

        if (!decodeExtras(intent)) {
            return
        }

        if (isDrawerEnabled) {
            configureDrawer()
        }

        initializeDisplayMode(null)
        initializeFragments()
        displayViews()
    }

    private fun findFragments() {
        val fragmentManager = supportFragmentManager
        messageListFragment = fragmentManager.findFragmentById(
            R.id.message_list_container,
        ) as? MessageListFragmentBridgeContract
        messageViewContainerFragment =
            fragmentManager.findFragmentByTag(FRAGMENT_TAG_MESSAGE_VIEW_CONTAINER) as? MessageViewContainerFragment

        messageListFragment?.let { messageListFragment ->
            messageViewContainerFragment?.setViewModel(messageListFragment.legacyViewModel)
            initializeFromLocalSearch(messageListFragment.localSearch)
        }
    }

    private fun initializeFragments() {
        val fragmentManager = supportFragmentManager
        fragmentManager.addOnBackStackChangedListener(this)

        val hasMessageListFragment = messageListFragment != null
        if (!hasMessageListFragment) {
            val fragmentTransaction = fragmentManager.beginTransaction()
            val messageListFragment = messageListFragmentFactory.newInstance(
                search = search!!,
                isThreadDisplay = false,
                threadedList = generalSettingsManager.getConfig()
                    .display
                    .inboxSettings
                    .isThreadedViewEnabled &&
                    !noThreading,
            )
            fragmentTransaction.add(R.id.message_list_container, messageListFragment as Fragment)
            if (messageReference == null) {
                fragmentTransaction.commit()
            } else {
                fragmentTransaction.commitNow()
            }

            this.messageListFragment = messageListFragment
        }

        // Check if the fragment wasn't restarted and has a MessageReference in the arguments.
        // If so, open the referenced message.
        if (!hasMessageListFragment && messageViewContainerFragment == null && messageReference != null) {
            openMessage(messageReference!!)
        }
    }

    /**
     * Set the initial display mode (message list, message view, or split view).
     *
     * **Note:**
     * This method has to be called after [.findFragments] because the result depends on
     * the availability of a [com.fsck.k9.ui.messageview.MessageViewFragment] instance.
     */
    private fun initializeDisplayMode(savedInstanceState: Bundle?) {
        if (useSplitView()) {
            displayMode = DisplayMode.SPLIT_VIEW
            return
        }

        if (savedInstanceState != null) {
            val savedDisplayMode = BundleCompat.getSerializable(
                savedInstanceState,
                STATE_DISPLAY_MODE,
                DisplayMode::class.java,
            )

            if (savedDisplayMode != null && savedDisplayMode != DisplayMode.SPLIT_VIEW) {
                displayMode = savedDisplayMode
                return
            }
        }

        displayMode = if (messageViewContainerFragment != null || messageReference != null) {
            DisplayMode.MESSAGE_VIEW
        } else {
            DisplayMode.MESSAGE_LIST
        }
    }

    private fun useSplitView(): Boolean {
        return when {
            initialDualScreenRuntimeState.usesSmartWorkspace -> true
            initialDualScreenRuntimeState.usesImmersiveCanvas -> false
            else -> {
                val splitViewMode = generalSettingsManager.getConfig().display.coreSettings.splitViewMode
                val orientation = resources.configuration.orientation
                when (splitViewMode) {
                    SplitViewMode.ALWAYS -> true
                    SplitViewMode.NEVER -> false
                    SplitViewMode.WHEN_IN_LANDSCAPE -> orientation == Configuration.ORIENTATION_LANDSCAPE
                    SplitViewMode.WHEN_UNFOLDED -> foldableStateObserver.currentState == FoldableState.UNFOLDED
                }
            }
        }
    }

    private fun initializeLayout() {
        progressBar = findViewById(R.id.message_list_progress)
        messageViewPlaceHolder = if (initialDualScreenRuntimeState.usesSmartWorkspace) {
            PlaceholderFragment.newInstance(
                SmartMessageListContextResolver.emptyMessage(search?.isManualSearch == true),
            )
        } else {
            PlaceholderFragment()
        }
    }

    private fun displayViews() {
        when (displayMode) {
            DisplayMode.MESSAGE_LIST -> {
                showMessageList()
            }

            DisplayMode.MESSAGE_VIEW -> {
                showMessageView()
            }

            DisplayMode.SPLIT_VIEW -> {
                val messageListFragment = checkNotNull(this.messageListFragment)

                messageListWasDisplayed = true
                messageListFragment.setFullyActive()

                messageViewContainerFragment.let { messageViewContainerFragment ->
                    if (messageViewContainerFragment == null) {
                        showMessageViewPlaceHolder()
                    } else {
                        messageViewContainerFragment.isActive = true
                    }
                }

                setDrawerLockState()
                onMessageListDisplayed()
            }
        }
    }

    private fun decodeExtras(intent: Intent): Boolean {
        if (intent.action === Intent.ACTION_SEARCH &&
            !intent.component?.className.equals(
                MessageSearchActivity::class.java.name,
            )
        ) {
            finish()
        }

        val launchData = decodeExtrasToLaunchData(intent)
        // If Unified Folders was disabled show default account instead
        val search = if (launchData.search.isUnifiedFolders &&
            !generalSettingsManager.getConfig().display.inboxSettings.isShowUnifiedInbox
        ) {
            createDefaultLocalSearch()
        } else {
            launchData.search
        }

        // If no account has been specified, keep the currently active account when opening the Unified Folders
        val account = launchData.account
            ?: account?.takeIf { launchData.search.isUnifiedFolders }
            ?: search.firstAccount()

        if (account == null) {
            finish()
            return false
        }

        this.account = account
        this.search = search
        singleFolderMode = search.folderIds.size == 1
        noThreading = launchData.noThreading
        messageReference = launchData.messageReference
        messageViewOnly = launchData.messageViewOnly

        return true
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod", "NestedBlockDepth", "ReturnCount")
    private fun decodeExtrasToLaunchData(intent: Intent): LaunchData {
        val action = intent.action
        val queryString = intent.getStringExtra(SearchManager.QUERY)

        if (action == ACTION_SHORTCUT) {
            // Handle shortcut intents
            val specialFolder = intent.getStringExtra(EXTRA_SPECIAL_FOLDER)
            if (specialFolder == SearchAccount.UNIFIED_FOLDERS) {
                return LaunchData(search = createSearchAccount().relatedSearch)
            }

            val accountUuid = intent.getStringExtra(EXTRA_ACCOUNT)
            if (accountUuid != null) {
                val account = accountManager.getAccount(accountUuid)
                if (account == null) {
                    Log.d("Account %s not found.", accountUuid)
                    return LaunchData(createDefaultLocalSearch())
                }

                val folderId = defaultFolderProvider.getDefaultFolder(account)
                val search = LocalMessageSearch().apply {
                    addAccountUuid(accountUuid)
                    addAllowedFolder(folderId)
                }

                return LaunchData(search = search)
            }
        } else if (action == Intent.ACTION_SEARCH && queryString != null) {
            // Query was received from Search Dialog
            val query = queryString.trim()

            val search = LocalMessageSearch().apply {
                isManualSearch = true
                or(
                    SearchCondition(
                        MessageSearchField.SENDER,
                        SearchAttribute.CONTAINS,
                        query,
                    ),
                )
                or(
                    SearchCondition(
                        MessageSearchField.TO,
                        SearchAttribute.CONTAINS,
                        query,
                    ),
                )
                or(
                    SearchCondition(
                        MessageSearchField.CC,
                        SearchAttribute.CONTAINS,
                        query,
                    ),
                )
                or(
                    SearchCondition(
                        MessageSearchField.BCC,
                        SearchAttribute.CONTAINS,
                        query,
                    ),
                )
                or(
                    SearchCondition(
                        MessageSearchField.SUBJECT,
                        SearchAttribute.CONTAINS,
                        query,
                    ),
                )
                or(
                    SearchCondition(
                        MessageSearchField.MESSAGE_CONTENTS,
                        SearchAttribute.CONTAINS,
                        query,
                    ),
                )
            }

            val appData = intent.getBundleExtra(SearchManager.APP_DATA)
            if (appData != null) {
                val searchAccountUuid = appData.getString(EXTRA_SEARCH_ACCOUNT)
                if (searchAccountUuid != null) {
                    search.addAccountUuid(searchAccountUuid)
                    // searches started from a folder list activity will provide an account, but no folder
                    if (appData.containsKey(EXTRA_SEARCH_FOLDER)) {
                        val folderId = appData.getLong(EXTRA_SEARCH_FOLDER)
                        search.addAllowedFolder(folderId)
                    }
                } else if (BuildConfig.DEBUG) {
                    throw AssertionError("Invalid app data in search intent")
                }
            }

            return LaunchData(
                search = search,
                noThreading = true,
            )
        } else if (intent.hasExtra(EXTRA_MESSAGE_REFERENCE)) {
            val messageReferenceString = intent.getStringExtra(EXTRA_MESSAGE_REFERENCE)
            val messageReference = MessageReference.parse(messageReferenceString)

            if (messageReference != null) {
                val search = if (intent.hasByteArrayExtra(EXTRA_SEARCH)) {
                    intent.getByteArrayExtra(EXTRA_SEARCH)?.let {
                        LocalMessageSearchSerializer.deserialize(it)
                    } ?: messageReference.toLocalSearch()
                } else {
                    messageReference.toLocalSearch()
                }

                return LaunchData(
                    search = search,
                    messageReference = messageReference,
                    messageViewOnly = intent.getBooleanExtra(EXTRA_MESSAGE_VIEW_ONLY, false),
                )
            }
        } else if (intent.hasByteArrayExtra(EXTRA_SEARCH)) {
            // regular LocalSearch object was passed
            val search = intent.getByteArrayExtra(EXTRA_SEARCH)?.let {
                LocalMessageSearchSerializer.deserialize(it)
            }
            val noThreading = intent.getBooleanExtra(EXTRA_NO_THREADING, false)
            val account = intent.getStringExtra(EXTRA_ACCOUNT)?.let { accountUuid ->
                accountManager.getAccount(accountUuid)
            }

            return if (search == null) {
                Log.e("No search data found in intent extras.")
                LaunchData(createDefaultLocalSearch())
            } else {
                LaunchData(search = search, account = account, noThreading = noThreading)
            }
        }

        // Default action
        val search = if (generalSettingsManager.getConfig().display.inboxSettings.isShowUnifiedInbox) {
            createSearchAccount().relatedSearch
        } else {
            createDefaultLocalSearch()
        }

        return LaunchData(search)
    }

    private fun createDefaultLocalSearch(uuid: String? = null): LocalMessageSearch {
        val account = uuid?.let { preferences.getAccount(it) } ?: run {
            preferences.defaultAccount ?: error("No default account available")
        }
        return LocalMessageSearch().apply {
            addAccountUuid(account.uuid)
            addAllowedFolder(defaultFolderProvider.getDefaultFolder(account))
        }
    }

    public override fun onResume() {
        super.onResume()

        if (messageListActivityConfig == null) {
            messageListActivityConfig = MessageListActivityConfig.create(generalSettingsManager)
        } else if (messageListActivityConfig != MessageListActivityConfig.create(generalSettingsManager)) {
            recreateMessageList(this)
        }

        if (displayMode != DisplayMode.MESSAGE_VIEW) {
            onMessageListDisplayed()
        }
    }

    override fun onStart() {
        super.onStart()

        dualScreenSpanCoordinator?.start()

        if (contactRepository is CachingRepository) {
            (contactRepository as CachingRepository).clearCache()
        }
    }

    override fun onStop() {
        isDualScreenModeDialogVisible = false
        dualScreenAttachmentWorkspaceCoordinator?.dismiss()
        dualScreenPopupController?.dismiss()
        dualScreenSpanCoordinator?.stop()
        super.onStop()
    }

    override fun onDestroy() {
        dualScreenRecoverySnackbar?.dismiss()
        dualScreenRecoverySnackbar = null
        smartAssistantPanelCoordinator?.destroy()
        smartAssistantPanelCoordinator = null
        dualScreenAttachmentWorkspaceCoordinator?.dismiss()
        dualScreenAttachmentWorkspaceCoordinator = null
        dualScreenModeSelectionController = null
        dualScreenSpanCoordinator?.destroy()
        dualScreenSpanCoordinator = null
        dualScreenActionModeController?.finish()
        dualScreenActionModeController = null
        dualScreenPopupController?.dismiss()
        dualScreenPopupController = null
        super.onDestroy()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        dualScreenPopupController?.dismissIfOutsideWindowTouch(event)
        return super.dispatchTouchEvent(event)
    }

    public override val usesDualScreenInCanvasPopups: Boolean
        get() = currentDualScreenRuntimeState.usesProjectedCanvas

    public override fun showDualScreenInCanvasPopup(anchor: View, content: View): Boolean {
        val popupController = dualScreenPopupController
        if (!usesDualScreenInCanvasPopups || popupController == null || !popupController.canAnchor(anchor)) return false

        popupController.show(anchor, content)
        return true
    }

    public override fun dismissDualScreenInCanvasPopup(): Boolean {
        return dualScreenPopupController?.dismiss() == true
    }

    public override fun showDualScreenPopupMenu(
        anchor: View,
        actions: List<MenuItem>,
        onMenuItemSelected: (MenuItem) -> Boolean,
    ): Boolean {
        val popupController = dualScreenPopupController
        if (
            popupController == null ||
            !usesDualScreenInCanvasPopups ||
            !popupController.canAnchor(anchor) ||
            actions.isEmpty()
        ) {
            return false
        }

        showDualScreenPopupMenuPage(
            anchor = anchor,
            actions = actions,
            onMenuItemSelected = onMenuItemSelected,
        )
        return true
    }

    private fun showDualScreenPopupMenuPage(
        anchor: View,
        actions: List<MenuItem>,
        onMenuItemSelected: (MenuItem) -> Boolean,
    ) {
        val popupContent = createAnchoredPopupContentView(
            context = anchor.context,
            lifecycleOwner = this,
            savedStateRegistryOwner = this,
            featureThemeProvider = featureThemeProvider,
        ) {
            DualScreenPopupActionList(
                actions = actions.toImmutableList(),
                onClick = { action ->
                    val subMenuActions = action.subMenu?.visibleItems().orEmpty()
                    if (subMenuActions.isNotEmpty()) {
                        showDualScreenPopupMenuPage(
                            anchor = anchor,
                            actions = subMenuActions,
                            onMenuItemSelected = onMenuItemSelected,
                        )
                    } else {
                        dismissDualScreenInCanvasPopup()
                        onMenuItemSelected(action)
                    }
                },
            )
        }
        dualScreenPopupController?.show(anchor, popupContent)
    }

    public override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putSerializable(STATE_DISPLAY_MODE, displayMode)
        MessageHomeRetainedState(
            messageViewOnly = messageViewOnly,
            messageListWasDisplayed = messageListWasDisplayed,
        ).writeTo(outState)
    }

    private fun restoreRetainedState(savedInstanceState: Bundle?) {
        val retainedState = MessageHomeRetainedState.restore(savedInstanceState) ?: return
        messageViewOnly = retainedState.messageViewOnly
        messageListWasDisplayed = retainedState.messageListWasDisplayed
    }

    private fun initializeActionBar() {
        actionBar = supportActionBar!!
        actionBar.setDisplayHomeAsUpEnabled(true)
        actionBar.setDisplayShowTitleEnabled(false)
    }

    private fun initializeDrawer() {
        if (!isDrawerEnabled) {
            val drawerLayout = findViewById<DrawerLayout>(R.id.navigation_drawer_layout)
            drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
            return
        }

        initializeFolderDrawer()
    }

    private fun initializeFolderDrawer() {
        navigationDrawer = DropDownDrawer(
            parent = this,
            openAccount = { accountId -> openRealAccount(accountId) },
            openAddAccount = { launchAddAccountScreen() },
            openFolder = { accountId, folderId -> openFolder(accountId, folderId) },
            openUnifiedFolder = { openUnifiedFolders() },
            openManageFolders = { launchManageFoldersScreen() },
            openSettings = { SettingsActivity.launch(this) },
            createDrawerListener = { createDrawerListener() },
        )
    }

    private fun createDrawerListener(): DrawerListener {
        return object : DrawerListener {
            override fun onDrawerClosed(drawerView: View) {
                if (openFolderTransaction != null) {
                    commitOpenFolderTransaction()
                }
            }

            override fun onDrawerStateChanged(newState: Int) = Unit

            override fun onDrawerOpened(drawerView: View) {
                collapseSearchView()
                messageListFragment?.finishActionMode()
            }

            override fun onDrawerSlide(drawerView: View, slideOffset: Float) = Unit
        }
    }

    private fun openFolder(accountId: String, folderId: Long) {
        if (displayMode == DisplayMode.SPLIT_VIEW) {
            removeMessageViewContainerFragment()
            showMessageViewPlaceHolder()
        }

        val search = LocalMessageSearch()
        search.addAccountUuid(accountId)
        search.addAllowedFolder(folderId)

        performSearch(search)
    }

    private fun openFolderImmediately(folderId: Long) {
        openFolder(account!!.uuid, folderId)
        commitOpenFolderTransaction()
    }

    private fun commitOpenFolderTransaction() {
        openFolderTransaction!!.commit()
        openFolderTransaction = null

        messageListFragment!!.setFullyActive()

        onMessageListDisplayed()
    }

    private fun openUnifiedFolders() {
        actionDisplaySearch(
            this,
            createSearchAccount().relatedSearch,
            false,
            false,
        )
    }

    private fun launchManageFoldersScreen() {
        if (account == null) {
            Log.e("Tried to open \"Manage folders\", but no account selected!")
            return
        }

        ManageFoldersActivity.launch(this, account!!)
    }

    private fun launchAddAccountScreen() {
        FeatureLauncherActivity.launch(
            context = this,
            target = FeatureLauncherTarget.AccountSetup,
        )
    }

    fun openRealAccount(accountId: String) {
        if (accountId == UnifiedDisplayAccount.UNIFIED_ACCOUNT_ID) {
            openUnifiedFolders()
        } else {
            val account = accountManager.getAccount(accountId) ?: return
            val folderId = defaultFolderProvider.getDefaultFolder(account)

            val search = LocalMessageSearch()
            search.addAllowedFolder(folderId)
            search.addAccountUuid(account.uuid)
            if (folderId == account.autoExpandFolderId) {
                performSearch(search)
            } else {
                actionDisplaySearch(this, search, noThreading = false, newTask = false)
                initializeFolderDrawer()
            }
        }
    }

    private fun performSearch(search: LocalMessageSearch) {
        initializeFromLocalSearch(search)

        val fragmentManager = supportFragmentManager

        check(!(BuildConfig.DEBUG && fragmentManager.backStackEntryCount > 0)) {
            "Don't call performSearch() while there are fragments on the back stack"
        }

        val openFolderTransaction = fragmentManager.beginTransaction()
        val messageListFragment = messageListFragmentFactory.newInstance(
            search = search,
            isThreadDisplay = false,
            threadedList = generalSettingsManager.getConfig().display.inboxSettings.isThreadedViewEnabled,
        )
        openFolderTransaction.replace(R.id.message_list_container, messageListFragment as Fragment)

        this.messageListFragment = messageListFragment
        this.openFolderTransaction = openFolderTransaction
    }

    protected open val isDrawerEnabled: Boolean = true

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        var eventHandled = false
        if (event.action == KeyEvent.ACTION_DOWN && isSearchViewCollapsed()) {
            eventHandled = onCustomKeyDown(event)
        }

        if (!eventHandled) {
            eventHandled = super.dispatchKeyEvent(event)
        }

        return eventHandled
    }

    @Suppress("NestedBlockDepth")
    private fun handleOnBackPressed(callback: OnBackPressedCallback) {
        if (isDrawerEnabled && navigationDrawer!!.isOpen) {
            navigationDrawer!!.close()
        } else if (displayMode == DisplayMode.MESSAGE_VIEW) {
            if (messageViewOnly) {
                finish()
            } else {
                showMessageList()
            }
        } else if (!isSearchViewCollapsed()) {
            collapseSearchView()
        } else if (isDrawerEnabled && account != null && supportFragmentManager.backStackEntryCount == 0) {
            if (generalSettingsManager.getConfig().display.inboxSettings.isShowUnifiedInbox) {
                if (search!!.id != SearchAccount.UNIFIED_FOLDERS) {
                    openUnifiedFolders()
                } else {
                    dispatchOnBackPressed(callback)
                }
            } else {
                val defaultFolderId = defaultFolderProvider.getDefaultFolder(account!!)
                val currentFolder = if (singleFolderMode) search!!.folderIds[0] else null
                if (currentFolder == null || defaultFolderId != currentFolder) {
                    openFolderImmediately(defaultFolderId)
                } else {
                    dispatchOnBackPressed(callback)
                }
            }
        } else {
            dispatchOnBackPressed(callback)
        }
    }

    /**
     * Dispatch back press to the system
     */
    private fun dispatchOnBackPressed(callback: OnBackPressedCallback) {
        callback.isEnabled = false
        onBackPressedDispatcher.onBackPressed()
        callback.isEnabled = true
    }

    /**
     * Handle hotkeys
     *
     * This method is called by [.dispatchKeyEvent] before any view had the chance to consume this key event.
     *
     * @return `true` if this event was consumed.
     */
    @Suppress("LongMethod", "CyclomaticComplexMethod", "ReturnCount")
    private fun onCustomKeyDown(event: KeyEvent): Boolean {
        if (!event.hasNoModifiers()) return false

        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                if (messageViewContainerFragment != null &&
                    displayMode != DisplayMode.MESSAGE_LIST &&
                    generalSettingsManager.getConfig().interaction.useVolumeKeysForNavigation
                ) {
                    showPreviousMessage()
                    return true
                }
            }

            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                if (messageViewContainerFragment != null &&
                    displayMode != DisplayMode.MESSAGE_LIST &&
                    generalSettingsManager.getConfig().interaction.useVolumeKeysForNavigation
                ) {
                    showNextMessage()
                    return true
                }
            }

            KeyEvent.KEYCODE_DEL -> {
                onDeleteHotKey()
                return true
            }

            KeyEvent.KEYCODE_DPAD_LEFT -> {
                return if (messageViewContainerFragment != null && displayMode == DisplayMode.MESSAGE_VIEW) {
                    showPreviousMessage()
                } else {
                    false
                }
            }

            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                return if (messageViewContainerFragment != null && displayMode == DisplayMode.MESSAGE_VIEW) {
                    showNextMessage()
                } else {
                    false
                }
            }
        }

        when (if (event.unicodeChar != 0) event.unicodeChar.toChar() else null) {
            'c' -> {
                messageListFragment!!.onCompose()
                return true
            }

            'o' -> {
                messageListFragment!!.onCycleSort()
                return true
            }

            'i' -> {
                messageListFragment!!.onReverseSort()
                return true
            }

            'd' -> {
                onDeleteHotKey()
                return true
            }

            's' -> {
                messageListFragment!!.toggleMessageSelect()
                return true
            }

            'g' -> {
                if (displayMode == DisplayMode.MESSAGE_LIST) {
                    messageListFragment!!.onToggleFlagged()
                } else if (messageViewContainerFragment != null) {
                    messageViewContainerFragment!!.onToggleFlagged()
                }
                return true
            }

            'm' -> {
                if (displayMode == DisplayMode.MESSAGE_LIST) {
                    messageListFragment!!.onMove()
                } else if (messageViewContainerFragment != null) {
                    messageViewContainerFragment!!.onMove()
                }
                return true
            }

            'v' -> {
                if (displayMode == DisplayMode.MESSAGE_LIST) {
                    messageListFragment!!.onArchive()
                } else if (messageViewContainerFragment != null) {
                    messageViewContainerFragment!!.onArchive()
                }
                return true
            }

            'y' -> {
                if (displayMode == DisplayMode.MESSAGE_LIST) {
                    messageListFragment!!.onCopy()
                } else if (messageViewContainerFragment != null) {
                    messageViewContainerFragment!!.onCopy()
                }
                return true
            }

            'z' -> {
                if (displayMode == DisplayMode.MESSAGE_LIST) {
                    messageListFragment!!.onToggleRead()
                } else if (messageViewContainerFragment != null) {
                    messageViewContainerFragment!!.onToggleRead()
                }
                return true
            }

            'f' -> {
                if (messageViewContainerFragment != null) {
                    messageViewContainerFragment!!.onForward()
                }
                return true
            }

            'a' -> {
                if (messageViewContainerFragment != null) {
                    messageViewContainerFragment!!.onReplyAll()
                }
                return true
            }

            'r' -> {
                if (messageViewContainerFragment != null) {
                    messageViewContainerFragment!!.onReply()
                }
                return true
            }

            'j', 'p' -> {
                if (messageViewContainerFragment != null) {
                    showPreviousMessage()
                }
                return true
            }

            'n', 'k' -> {
                if (messageViewContainerFragment != null) {
                    showNextMessage()
                }
                return true
            }
        }

        return false
    }

    private fun onDeleteHotKey() {
        if (displayMode == DisplayMode.MESSAGE_LIST) {
            messageListFragment!!.onDelete()
        } else if (messageViewContainerFragment != null) {
            messageViewContainerFragment!!.onDelete()
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        // Swallow these events too to avoid the audible notification of a volume change
        if (generalSettingsManager.getConfig().interaction.useVolumeKeysForNavigation) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                Log.v("Swallowed key up.")
                return true
            }
        }

        return super.onKeyUp(keyCode, event)
    }

    @Suppress("NestedBlockDepth")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        if (id == android.R.id.home) {
            if (displayMode != DisplayMode.MESSAGE_VIEW && !isAdditionalMessageListDisplayed) {
                if (isDrawerEnabled) {
                    if (navigationDrawer!!.isOpen) {
                        navigationDrawer!!.close()
                    } else {
                        navigationDrawer!!.open()
                    }
                } else {
                    finish()
                }
            } else {
                goBack()
            }
            return true
        }

        return super.onOptionsItemSelected(item)
    }

    private fun isSearchViewCollapsed(): Boolean {
        return messageListFragment?.isSearchViewCollapsed() ?: true
    }

    private fun collapseSearchView() {
        messageListFragment?.collapseSearchView()
    }

    private fun setActionBarTitle(title: String, subtitle: String? = null) {
        findViewById<MaterialTextView>(R.id.toolbarTitle).text = title
        findViewById<MaterialTextView>(R.id.toolbarSubtitle).apply {
            if (subtitle != null) {
                text = subtitle
                isGone = false
            } else {
                text = null
                isGone = true
            }
        }
    }

    override fun setMessageListTitle(title: String, subtitle: String?) {
        if (displayMode != DisplayMode.MESSAGE_VIEW) {
            setActionBarTitle(title, subtitle)
        }
    }

    override fun setMessageListProgressEnabled(enable: Boolean) {
        progressBar!!.visibility = if (enable) View.VISIBLE else View.INVISIBLE
    }

    override fun setMessageListProgress(level: Int) {
        progressBar!!.progress = level
    }

    override fun openMessage(messageReference: MessageReference) {
        dualScreenAttachmentWorkspaceCoordinator?.dismiss()
        val account = accountManager.getAccount(messageReference.accountUuid) ?: error("Account not found")
        val folderId = messageReference.folderId

        val draftsFolderId = account.draftsFolderId
        if (draftsFolderId != null && folderId == draftsFolderId) {
            displayMode = DisplayMode.MESSAGE_LIST
            updateSmartAssistantContext(SmartAssistantScene.DRAFT_COMPOSING, messageReference)
            MessageActions.actionEditDraft(this, messageReference)
        } else {
            val fragment = MessageViewContainerFragment.newInstance(
                reference = messageReference,
                isShowAccountIndicator = isShowAccountIndicator,
            )
            supportFragmentManager.commitNow {
                replace(R.id.message_view_container, fragment, FRAGMENT_TAG_MESSAGE_VIEW_CONTAINER)
            }

            messageViewContainerFragment = fragment

            messageListFragment?.let { messageListFragment ->
                fragment.setViewModel(messageListFragment.legacyViewModel)
            }

            if (displayMode == DisplayMode.SPLIT_VIEW) {
                fragment.isActive = true
            } else {
                showMessageView()
            }

            updateSmartAssistantContext(SmartAssistantScene.MESSAGE_READING, messageReference)
        }

        collapseSearchView()
    }

    override fun onForward(messageReference: MessageReference, decryptionResultForReply: Parcelable?) {
        MessageActions.actionForward(this, messageReference, decryptionResultForReply)
    }

    override fun onForwardAsAttachment(messageReference: MessageReference, decryptionResultForReply: Parcelable?) {
        MessageActions.actionForwardAsAttachment(this, messageReference, decryptionResultForReply)
    }

    override fun onEditAsNewMessage(messageReference: MessageReference) {
        MessageActions.actionEditDraft(this, messageReference)
    }

    override fun onReply(messageReference: MessageReference, decryptionResultForReply: Parcelable?) {
        MessageActions.actionReply(this, messageReference, false, decryptionResultForReply)
    }

    override fun onReplyAll(messageReference: MessageReference, decryptionResultForReply: Parcelable?) {
        MessageActions.actionReply(this, messageReference, true, decryptionResultForReply)
    }

    override fun onViewAttachmentInDualScreenWorkspace(attachment: AttachmentViewInfo): Boolean {
        return dualScreenAttachmentWorkspaceCoordinator?.showIfSupported(attachment) == true
    }

    override fun getAttachmentDragCallbacks(): AttachmentDragCallbacks {
        return AttachmentDragCallbacks(
            canStart = { attachment ->
                currentDualScreenRuntimeState.usesSmartWorkspace &&
                    dualScreenAttachmentWorkspaceCoordinator != null &&
                    canBeginDualScreenAttachmentDrag(attachment)
            },
            start = { attachment ->
                currentDualScreenRuntimeState.usesSmartWorkspace &&
                    dualScreenAttachmentWorkspaceCoordinator?.beginDrag(attachment) == true
            },
        )
    }

    override fun onCompose(account: LegacyAccount?) {
        val accountDto = account?.let { legacyAccountDataMapper.toDto(account) }
        MessageActions.actionCompose(this, accountDto)
    }

    override fun onBackStackChanged() {
        findFragments()
        messageListFragment?.setFullyActive()

        if (isDrawerEnabled && !isAdditionalMessageListDisplayed) {
            unlockDrawer()
        }

        if (displayMode == DisplayMode.SPLIT_VIEW) {
            showMessageViewPlaceHolder()
        }
    }

    private fun addMessageListFragment(fragment: MessageListFragmentBridgeContract) {
        messageListFragment?.isActive = false

        supportFragmentManager.commit {
            replace(R.id.message_list_container, fragment as Fragment)

            setReorderingAllowed(true)

            if (supportFragmentManager.backStackEntryCount == 0) {
                addToBackStack(FIRST_FRAGMENT_TRANSACTION)
            } else {
                addToBackStack(null)
            }
        }

        messageListFragment = fragment
        fragment.setFullyActive()

        if (isDrawerEnabled) {
            lockDrawer()
        }
    }

    override fun onSearchRequested(): Boolean {
        if (displayMode == DisplayMode.MESSAGE_VIEW) {
            return false
        }

        messageListFragment?.expandSearchView()
        return true
    }

    override fun startSearch(query: String, account: LegacyAccount?, folderId: Long?): Boolean {
        // If this search was started from a MessageList of a single folder, pass along that folder info
        // so that we can enable remote search.
        val appData = if (account != null && folderId != null) {
            Bundle().apply {
                putString(EXTRA_SEARCH_ACCOUNT, account.uuid)
                putLong(EXTRA_SEARCH_FOLDER, folderId)
            }
        } else {
            // TODO Handle the case where we're searching from within a search result.
            null
        }
        val searchIntent = Intent(this, MessageSearchActivity::class.java).apply {
            action = Intent.ACTION_SEARCH
            putExtra(SearchManager.QUERY, query)
            putExtra(SearchManager.APP_DATA, appData)
        }
        startActivity(searchIntent)

        return true
    }

    override fun startSupportActionMode(callback: ActionMode.Callback): ActionMode? {
        collapseSearchView()
        if (usesDualScreenInCanvasPopups) {
            return dualScreenActionModeController?.start(callback)
        }
        return super.startSupportActionMode(callback)
    }

    override fun showThread(account: LegacyAccount, threadRootId: Long) {
        showMessageViewPlaceHolder()

        val tmpSearch = LocalMessageSearch().apply {
            id = search?.id ?: "ShowThread-${account.uuid}-$threadRootId"
            addAccountUuid(account.uuid)
            and(MessageSearchField.THREAD_ID, threadRootId.toString(), SearchAttribute.EQUALS)
        }

        initializeFromLocalSearch(tmpSearch)

        val fragment = messageListFragmentFactory.newInstance(
            search = tmpSearch,
            isThreadDisplay = true,
            threadedList = false,
        )
        addMessageListFragment(fragment)
    }

    private fun showMessageViewPlaceHolder() {
        dualScreenAttachmentWorkspaceCoordinator?.dismiss()
        removeMessageViewContainerFragment()

        // Add placeholder fragment if necessary
        val fragmentManager = supportFragmentManager
        if (fragmentManager.findFragmentByTag(FRAGMENT_TAG_PLACEHOLDER) == null) {
            val fragmentTransaction = fragmentManager.beginTransaction()
            fragmentTransaction.replace(R.id.message_view_container, messageViewPlaceHolder!!, FRAGMENT_TAG_PLACEHOLDER)
            fragmentTransaction.commit()
        }

        messageListFragment!!.setActiveMessage(null)
        updateSmartAssistantContext(SmartAssistantScene.MESSAGE_LIST)
    }

    private fun removeMessageViewContainerFragment() {
        if (messageViewContainerFragment != null) {
            val fragmentTransaction = supportFragmentManager.beginTransaction()
            fragmentTransaction.remove(messageViewContainerFragment!!)
            messageViewContainerFragment = null
            fragmentTransaction.commit()

            showDefaultTitleView()
        }
    }

    private fun removeMessageListFragment() {
        val fragmentTransaction = supportFragmentManager.beginTransaction()
        fragmentTransaction.remove(messageListFragment!! as Fragment)
        messageListFragment = null
        fragmentTransaction.commit()
    }

    override fun goBack() {
        onBackPressedDispatcher.onBackPressed()
    }

    override fun closeMail() {
        finishAndRemoveTask()
    }

    override fun closeMessageView() {
        returnToMessageList()
    }

    override fun setActiveMessage(messageReference: MessageReference) {
        val messageListFragment = checkNotNull(messageListFragment)

        dualScreenAttachmentWorkspaceCoordinator?.dismiss()
        messageListFragment.setActiveMessage(messageReference)
        updateSmartAssistantContext(SmartAssistantScene.MESSAGE_READING, messageReference)
    }

    override fun performNavigationAfterMessageRemoval() {
        when (generalSettingsManager.getConfig().interaction.messageViewPostRemoveNavigation) {
            PostRemoveNavigation.ReturnToMessageList.name -> returnToMessageList()
            PostRemoveNavigation.ShowPreviousMessage.name -> showPreviousMessageOrReturn()
            PostRemoveNavigation.ShowNextMessage.name -> showNextMessageOrReturn()
        }
    }

    override fun performNavigationAfterMarkAsUnread() {
        when (generalSettingsManager.getConfig().interaction.messageViewPostMarkAsUnreadNavigation) {
            PostMarkAsUnreadNavigation.StayOnCurrentMessage -> Unit
            PostMarkAsUnreadNavigation.ReturnToMessageList -> returnToMessageList()
        }
    }

    private fun returnToMessageList() {
        if (displayMode == DisplayMode.SPLIT_VIEW) {
            showMessageViewPlaceHolder()
        } else {
            showMessageList()
        }
    }

    private fun showPreviousMessageOrReturn() {
        if (!showPreviousMessage()) {
            returnToMessageList()
        }
    }

    private fun showNextMessageOrReturn() {
        if (!showNextMessage()) {
            returnToMessageList()
        }
    }

    override fun setProgress(enable: Boolean) {
        setProgressBarIndeterminateVisibility(enable)
    }

    private fun showNextMessage(): Boolean {
        val messageViewContainerFragment = checkNotNull(messageViewContainerFragment)

        return messageViewContainerFragment.showNextMessage()
    }

    private fun showPreviousMessage(): Boolean {
        val messageViewContainerFragment = checkNotNull(messageViewContainerFragment)

        return messageViewContainerFragment.showPreviousMessage()
    }

    private fun showMessageList() {
        messageViewOnly = false
        messageListWasDisplayed = true
        displayMode = DisplayMode.MESSAGE_LIST

        messageViewContainerFragment?.isActive = false
        messageListFragment!!.isActive = true
        messageListFragment!!.setActiveMessage(null)

        viewSwitcher!!.showFirstView()

        setDrawerLockState()

        showDefaultTitleView()

        onMessageListDisplayed()
    }

    private fun setDrawerLockState() {
        if (!isDrawerEnabled) return

        if (isAdditionalMessageListDisplayed) {
            lockDrawer()
        } else {
            unlockDrawer()
        }
    }

    private fun showMessageView() {
        val messageViewContainerFragment = checkNotNull(this.messageViewContainerFragment)

        displayMode = DisplayMode.MESSAGE_VIEW
        messageListFragment?.isActive = false
        messageViewContainerFragment.isActive = true

        if (!messageListWasDisplayed) {
            viewSwitcher!!.animateFirstView = false
        }
        viewSwitcher!!.showSecondView()

        if (isDrawerEnabled) {
            lockDrawer()
        }

        showMessageTitleView()
    }

    private fun showDefaultTitleView() {
        if (messageListFragment != null) {
            messageListFragment!!.updateTitle()
        }
    }

    private fun showMessageTitleView() {
        setActionBarTitle("")
    }

    override fun onSwitchComplete(displayedChild: Int) {
        if (displayedChild == 0) {
            removeMessageViewContainerFragment()
            messageListFragment?.onFullyActive()
        }
    }

    private fun onMessageListDisplayed() {
        if (firstDrawCompleted) {
            clearNotifications()
        } else {
            clearNotificationsAfterFirstDraw = true
        }
    }

    override fun onMessageListReady() {
        if (fullyDrawnReported) return

        fullyDrawnReported = true
        val decorView = window.decorView
        decorView.viewTreeObserver.addOnDrawListener(
            object : ViewTreeObserver.OnDrawListener {
                override fun onDraw() {
                    decorView.post {
                        if (decorView.viewTreeObserver.isAlive) {
                            decorView.viewTreeObserver.removeOnDrawListener(this)
                        }
                        if (!isDestroyed) reportFullyDrawn()
                    }
                }
            },
        )
    }

    private fun clearNotifications() {
        messagingController.clearNotifications(search)
    }

    private val isAdditionalMessageListDisplayed: Boolean
        get() = supportFragmentManager.backStackEntryCount > 0

    private fun lockDrawer() {
        navigationDrawer!!.lock()
        actionBar.setHomeAsUpIndicator(Icons.Outlined.ArrowBack)
    }

    private fun unlockDrawer() {
        navigationDrawer!!.unlock()
        actionBar.setHomeAsUpIndicator(Icons.Outlined.Menu)
    }

    private fun initializeFromLocalSearch(search: LocalMessageSearch) {
        this.search = search
        singleFolderMode = false

        val folderIds = search.folderIds
        if (search.searchAllAccounts()) {
            val accountUuids = search.accountUuids
            if (accountUuids.size == 1) {
                account = accountManager.getAccount(accountUuids.elementAt(0))
                singleFolderMode = folderIds.size == 1
            } else {
                account = null
            }
        } else {
            account = SmartMessageListContextResolver.explicitAccountUuid(search.accountUuids)
                ?.let(accountManager::getAccount)
            singleFolderMode = folderIds.size == 1
        }

        configureDrawer()
    }

    private fun LocalMessageSearch.firstAccount(): LegacyAccountDto? {
        return if (searchAllAccounts()) {
            preferences.defaultAccount
        } else {
            val accountUuid = accountUuids.first()
            accountManager.getAccount(accountUuid)
        }
    }

    private fun MessageReference.toLocalSearch(): LocalMessageSearch {
        return LocalMessageSearch().apply {
            addAccountUuid(accountUuid)
            addAllowedFolder(folderId)
        }
    }

    private fun MessageListFragmentBridgeContract.setFullyActive() {
        isActive = true
        onFullyActive()
    }

    private fun configureDrawer() {
        val drawer = navigationDrawer ?: return
        val accountUuid = account?.uuid ?: return Unit.also {
            logger.warn(TAG) { "The account property is null. Skipping drawer configuration. " }
            logger.verbose(TAG) { "drawer = $drawer, localSearch = $search" }
        }
        drawer.selectAccount(accountUuid)

        search?.let { search ->
            when {
                singleFolderMode -> search.getFolderIdAtIndexOrNull(0)?.let { folderId ->
                    drawer.selectFolder(
                        accountUuid = search.accountUuids.elementAt(0),
                        folderId = folderId,
                    )
                }

                // Don't select any item in the drawer because the Unified Inbox is displayed,
                // but not listed in the drawer
                search.id == SearchAccount.UNIFIED_FOLDERS &&
                    !generalSettingsManager.getConfig().display.inboxSettings.isShowUnifiedInbox -> drawer.deselect()

                search.id == SearchAccount.UNIFIED_FOLDERS -> drawer.selectUnifiedInbox()
            }
        } ?: logger.warn(TAG) { "Couldn't select folder for $accountUuid as LocalSearch is null." }
    }

    private fun createSearchAccount(): SearchAccount {
        return SearchAccount.createUnifiedFoldersSearch(
            title = coreResourceProvider.searchUnifiedFoldersTitle(),
            detail = coreResourceProvider.searchUnifiedFoldersDetail(),
        )
    }

    private enum class DisplayMode {
        MESSAGE_LIST,
        MESSAGE_VIEW,
        SPLIT_VIEW,
    }

    private class LaunchData(
        val search: LocalMessageSearch,
        val account: LegacyAccountDto? = null,
        val messageReference: MessageReference? = null,
        val noThreading: Boolean = false,
        val messageViewOnly: Boolean = false,
    )

    companion object : KoinComponent {
        private const val DUAL_SCREEN_VIEWPORT_COUNT = 2
        private const val EXTRA_SEARCH = "search_bytes"
        private const val EXTRA_NO_THREADING = "no_threading"

        private const val ACTION_SHORTCUT = "shortcut"
        private const val EXTRA_SPECIAL_FOLDER = "special_folder"

        const val EXTRA_ACCOUNT = "account_uuid"
        private const val EXTRA_MESSAGE_REFERENCE = "message_reference"
        private const val EXTRA_MESSAGE_VIEW_ONLY = "message_view_only"

        // used for remote search
        const val EXTRA_SEARCH_ACCOUNT = "com.fsck.k9.search_account"
        private const val EXTRA_SEARCH_FOLDER = "com.fsck.k9.search_folder"

        private const val STATE_DISPLAY_MODE = "displayMode"
        private const val FIRST_FRAGMENT_TRANSACTION = "first"
        private const val FRAGMENT_TAG_MESSAGE_VIEW_CONTAINER = "MessageViewContainerFragment"
        private const val FRAGMENT_TAG_PLACEHOLDER = "MessageViewPlaceholder"

        private val defaultFolderProvider: DefaultFolderProvider by inject()
        private val coreResourceProvider: CoreResourceProvider by inject()

        @JvmStatic
        @JvmOverloads
        fun actionDisplaySearch(
            context: Context,
            search: LocalMessageSearch?,
            noThreading: Boolean,
            newTask: Boolean,
            clearTop: Boolean = true,
        ) {
            context.startActivity(intentDisplaySearch(context, search, noThreading, newTask, clearTop))
        }

        @JvmStatic
        fun intentDisplaySearch(
            context: Context?,
            search: LocalMessageSearch?,
            noThreading: Boolean,
            newTask: Boolean,
            clearTop: Boolean,
        ): Intent {
            return Intent(context, MessageHomeActivity::class.java).apply {
                if (search != null) {
                    putExtra(EXTRA_SEARCH, LocalMessageSearchSerializer.serialize(search))
                }
                putExtra(EXTRA_NO_THREADING, noThreading)

                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)

                if (clearTop) addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                if (newTask) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        fun createUnifiedInboxIntent(
            context: Context,
            account: LegacyAccountDto,
        ): Intent {
            return Intent(context, MessageHomeActivity::class.java).apply {
                val search = SearchAccount.createUnifiedFoldersSearch(
                    title = coreResourceProvider.searchUnifiedFoldersTitle(),
                    detail = coreResourceProvider.searchUnifiedFoldersDetail(),
                ).relatedSearch

                putExtra(EXTRA_ACCOUNT, account.uuid)
                putExtra(EXTRA_SEARCH, LocalMessageSearchSerializer.serialize(search))
                putExtra(EXTRA_NO_THREADING, false)

                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        fun createNewMessagesIntent(context: Context, account: LegacyAccountDto): Intent {
            val search = LocalMessageSearch().apply {
                id = SearchAccount.NEW_MESSAGES
                addAccountUuid(account.uuid)
                and(MessageSearchField.NEW_MESSAGE, "1", SearchAttribute.EQUALS)
            }

            return intentDisplaySearch(context, search, noThreading = false, newTask = true, clearTop = true)
        }

        @JvmStatic
        fun shortcutIntent(context: Context?, specialFolder: String?): Intent {
            return Intent(context, MessageHomeActivity::class.java).apply {
                action = ACTION_SHORTCUT
                putExtra(EXTRA_SPECIAL_FOLDER, specialFolder)

                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        @JvmStatic
        fun shortcutIntentForAccount(context: Context, accountUuid: String): Intent {
            return Intent(context, MessageHomeActivity::class.java).apply {
                action = ACTION_SHORTCUT
                putExtra(EXTRA_ACCOUNT, accountUuid)

                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }

        fun actionDisplayMessageIntent(
            context: Context,
            messageReference: MessageReference,
            openInUnifiedInbox: Boolean = false,
            messageViewOnly: Boolean = false,
        ): Intent {
            return actionDisplayMessageTemplateIntent(context, openInUnifiedInbox, messageViewOnly).apply {
                putExtra(EXTRA_MESSAGE_REFERENCE, messageReference.toIdentityString())
            }
        }

        fun actionDisplayMessageTemplateIntent(
            context: Context,
            openInUnifiedInbox: Boolean,
            messageViewOnly: Boolean,
        ): Intent {
            return Intent(context, MessageHomeActivity::class.java).apply {
                if (openInUnifiedInbox) {
                    val search = SearchAccount.createUnifiedFoldersSearch(
                        title = coreResourceProvider.searchUnifiedFoldersTitle(),
                        detail = coreResourceProvider.searchUnifiedFoldersDetail(),
                    ).relatedSearch
                    putExtra(EXTRA_SEARCH, LocalMessageSearchSerializer.serialize(search))
                }

                putExtra(EXTRA_MESSAGE_VIEW_ONLY, messageViewOnly)

                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
        }

        fun actionDisplayMessageTemplateFillIntent(messageReference: MessageReference): Intent {
            return Intent().apply {
                putExtra(EXTRA_MESSAGE_REFERENCE, messageReference.toIdentityString())
            }
        }

        @JvmStatic
        fun launch(context: Context) {
            val intent = Intent(context, MessageHomeActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }

            context.startActivity(intent)
        }

        @JvmStatic
        fun launch(context: Context, account: LegacyAccountDto) {
            val folderId = defaultFolderProvider.getDefaultFolder(account)

            val search = LocalMessageSearch().apply {
                addAllowedFolder(folderId)
                addAccountUuid(account.uuid)
            }

            actionDisplaySearch(context, search, noThreading = false, newTask = false)
        }

        /**
         * Display the default folder of a given account.
         */
        fun launch(context: Context, accountUuid: String) {
            val intent = shortcutIntentForAccount(context, accountUuid)
            context.startActivity(intent)
        }

        @JvmStatic
        fun recreateMessageList(context: Context) {
            val intent = Intent(context, MessageHomeActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }

            context.startActivity(intent)
        }
    }
}

private fun Intent.hasByteArrayExtra(name: String): Boolean {
    return getByteArrayExtra(name) != null
}
