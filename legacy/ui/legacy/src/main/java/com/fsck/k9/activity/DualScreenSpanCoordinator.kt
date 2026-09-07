package com.fsck.k9.activity

import android.app.Presentation
import android.content.Context
import android.graphics.Canvas
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.os.SystemClock
import android.view.Display
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewTreeObserver
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat.Type.systemBars
import androidx.core.view.WindowInsetsControllerCompat
import com.fsck.k9.ui.R
import com.fsck.k9.ui.base.getDisplayIdCompat
import java.lang.ref.WeakReference
import net.thunderbird.core.preference.DualScreenMode

/**
 * Projects one authoritative Activity view across the two KEMI displays.
 *
 * Immersive mode uses one continuous layout. Smart mode uses an Activity-specific two-zone layout. The selected
 * [DualScreenDeviceProfile] defines the physical display whitelist, logical canvas, display IDs, orientation,
 * scaling, and touch mapping. Both displays dispatch touches to the same UI tree and business state.
 */
@Suppress("TooManyFunctions")
internal class DualScreenSpanCoordinator(
    private val activity: ComponentActivity,
    private val sourceView: View,
    private val dualScreenMode: DualScreenMode,
    private val preparedRuntimeState: DualScreenRuntimeState,
    private val preparedDeviceProfile: DualScreenDeviceProfile?,
    private val displaySelector: DualScreenDisplaySelector = DualScreenDisplaySelector(
        displayManager = activity.getSystemService(DisplayManager::class.java),
    ),
    private val secondaryViewportContentDescription: CharSequence =
        activity.getText(R.string.dual_screen_secondary_viewport_description),
    initialRecoveryPending: Boolean = false,
    private val beforeSecondaryTouchDispatch: (MotionEvent) -> Unit = {},
    private val onRuntimeStateChanged: (DualScreenRuntimeState) -> Unit = {},
    private val onRecoveryPendingChanged: (Boolean) -> Unit = {},
    private val onRecoveryAvailabilityChanged: (Boolean) -> Unit = {},
    private val onSmartWorkspaceLost: () -> Unit = {},
) : DisplayManager.DisplayListener {
    private val displayManager = activity.getSystemService(DisplayManager::class.java)
    private val originalRequestedOrientation = activity.requestedOrientation
    private val originalLayoutWidth = sourceView.layoutParams.width
    private val originalLayoutHeight = sourceView.layoutParams.height
    private val originalTranslationY = sourceView.translationY
    private val originalSystemBarsBehavior = WindowCompat.getInsetsController(
        activity.window,
        activity.window.decorView,
    ).systemBarsBehavior
    private val projectionLifecycle = DualScreenProjectionLifecycle(initialRecoveryPending)

    private var started = false
    private var orientationLocked = false
    private var runtimeState = DualScreenRuntimeState.SINGLE_SCREEN
    private var secondaryPresentation: SpanPresentation? = null

    fun start() {
        if (started) return

        started = true
        displayManager.registerDisplayListener(this, null)
        reconcileOnStart()
    }

    fun stop() {
        if (!started) return

        started = false
        displayManager.unregisterDisplayListener(this)
        applyProjectionDecision(
            decision = projectionLifecycle.onStop(),
            secondaryTarget = null,
            targetState = DualScreenRuntimeState.SINGLE_SCREEN,
            allowSmartWorkspaceRecreation = false,
        )
    }

    fun destroy() {
        stop()
        restoreSourceLayout()
        restoreOrientation()
    }

    fun resumeAfterDisplayReconnect(): Boolean {
        if (!started) return false

        val secondaryTarget = findSecondaryDisplay()
        val targetState = resolveTargetState(secondaryTarget)
        val decision = projectionLifecycle.onRecoveryAccepted(
            isSecondaryDisplayAvailable = secondaryTarget != null,
            isWorkspacePrepared = canActivatePreparedWorkspace(targetState, secondaryTarget),
        )
        applyProjectionDecision(
            decision = decision,
            secondaryTarget = secondaryTarget,
            targetState = targetState,
            allowSmartWorkspaceRecreation = false,
        )

        return decision.action == DualScreenProjectionAction.RECREATE_WORKSPACE
    }

    override fun onDisplayAdded(displayId: Int) = reconcileDisplayTopology()

    override fun onDisplayRemoved(displayId: Int) = reconcileDisplayTopology()

    override fun onDisplayChanged(displayId: Int) = reconcileDisplayTopology()

    private fun reconcileOnStart() {
        val secondaryTarget = findSecondaryDisplay()
        val targetState = resolveTargetState(secondaryTarget)
        val decision = projectionLifecycle.onStart(
            isSecondaryDisplayAvailable = secondaryTarget != null,
            isWorkspacePrepared = canActivatePreparedWorkspace(targetState, secondaryTarget),
            wasProjectionExpected = preparedRuntimeState.usesProjectedCanvas,
        )

        applyProjectionDecision(decision, secondaryTarget, targetState)
    }

    private fun reconcileDisplayTopology() {
        if (!started) return

        val secondaryTarget = findSecondaryDisplay()
        val targetState = resolveTargetState(secondaryTarget)
        val currentPresentation = secondaryPresentation
        val isCurrentProjectionReusable = currentPresentation != null &&
            secondaryTarget != null &&
            currentPresentation.display.displayId == secondaryTarget.display.displayId &&
            currentPresentation.deviceProfile == secondaryTarget.deviceProfile &&
            currentPresentation.isShowing
        val decision = projectionLifecycle.onDisplayTopologyChanged(
            isSecondaryDisplayAvailable = secondaryTarget != null,
            isCurrentProjectionReusable = isCurrentProjectionReusable,
        )

        applyProjectionDecision(decision, secondaryTarget, targetState)
    }

    private fun applyProjectionDecision(
        decision: DualScreenProjectionDecision,
        secondaryTarget: DualScreenDisplayTarget?,
        targetState: DualScreenRuntimeState,
        allowSmartWorkspaceRecreation: Boolean = true,
    ) {
        onRecoveryPendingChanged(decision.recoveryPending)
        onRecoveryAvailabilityChanged(started && decision.recoveryAvailable)

        when (decision.action) {
            DualScreenProjectionAction.NONE -> secondaryPresentation?.requestFrame()
            DualScreenProjectionAction.ACTIVATE -> {
                checkNotNull(secondaryTarget)
                activateProjection(secondaryTarget, targetState)
            }

            DualScreenProjectionAction.DEACTIVATE,
            DualScreenProjectionAction.RECREATE_WORKSPACE,
            -> {
                val shouldRecreateWorkspace = allowSmartWorkspaceRecreation &&
                    decision.recoveryStarted &&
                    (runtimeState.usesSmartWorkspace || preparedRuntimeState.usesSmartWorkspace)
                deactivateSpanning()
                updateRuntimeState(DualScreenRuntimeState.SINGLE_SCREEN)
                if (started && shouldRecreateWorkspace) onSmartWorkspaceLost()
            }
        }
    }

    private fun findSecondaryDisplay(): DualScreenDisplayTarget? {
        return displaySelector.findEligibleSecondaryDisplay(activity.getDisplayIdCompat())
    }

    private fun resolveTargetState(secondaryTarget: DualScreenDisplayTarget?): DualScreenRuntimeState {
        return DualScreenRuntimeState.resolve(
            savedMode = dualScreenMode,
            isEligibleSecondaryDisplayAvailable = secondaryTarget != null,
        )
    }

    private fun canActivatePreparedWorkspace(
        targetState: DualScreenRuntimeState,
        secondaryTarget: DualScreenDisplayTarget?,
    ): Boolean {
        return preparedRuntimeState.canActivatePreparedWorkspace(targetState) &&
            preparedDeviceProfile == secondaryTarget?.deviceProfile
    }

    private fun activateProjection(secondaryTarget: DualScreenDisplayTarget, targetState: DualScreenRuntimeState) {
        val currentPresentation = secondaryPresentation
        if (
            currentPresentation?.display?.displayId == secondaryTarget.display.displayId &&
            currentPresentation.deviceProfile == secondaryTarget.deviceProfile &&
            currentPresentation.isShowing
        ) {
            updateRuntimeState(targetState)
            return
        }

        dismissSecondaryPresentation()
        lockOrientation(secondaryTarget.deviceProfile)

        val candidate = SpanPresentation(
            context = activity,
            display = secondaryTarget.display,
            sourceView = sourceView,
            deviceProfile = secondaryTarget.deviceProfile,
            viewportContentDescription = secondaryViewportContentDescription,
            handleBackPressed = { activity.onBackPressedDispatcher.onBackPressed() },
            beforeTouchDispatch = beforeSecondaryTouchDispatch,
        )
        candidate.setOnDismissListener {
            if (secondaryPresentation === candidate) {
                secondaryPresentation = null
                handleUnexpectedPresentationDismissal()
            }
        }

        try {
            candidate.show()
            secondaryPresentation = candidate
            applySpanningLayout(secondaryTarget.deviceProfile)
            hideSystemBars(activity.window)
            candidate.requestFrame()
            updateRuntimeState(targetState)
        } catch (_: WindowManager.InvalidDisplayException) {
            candidate.setOnDismissListener(null)
            candidate.dismiss()
            handleProjectionFailure()
        }
    }

    private fun handleUnexpectedPresentationDismissal() {
        val secondaryTarget = findSecondaryDisplay()
        val decision = projectionLifecycle.onPresentationDismissed(
            isSecondaryDisplayAvailable = secondaryTarget != null,
        )
        applyProjectionDecision(
            decision = decision,
            secondaryTarget = secondaryTarget,
            targetState = resolveTargetState(secondaryTarget),
        )
    }

    private fun handleProjectionFailure() {
        val decision = projectionLifecycle.onPresentationDismissed(isSecondaryDisplayAvailable = false)
        applyProjectionDecision(
            decision = decision,
            secondaryTarget = null,
            targetState = DualScreenRuntimeState.SINGLE_SCREEN,
        )
    }

    private fun applySpanningLayout(deviceProfile: DualScreenDeviceProfile) {
        val layoutParams = sourceView.layoutParams ?: return
        layoutParams.width = deviceProfile.logicalViewportWidth
        layoutParams.height = deviceProfile.logicalCanvasHeight
        sourceView.layoutParams = layoutParams
        sourceView.translationY = deviceProfile.primaryTranslationY
        sourceView.requestLayout()
    }

    private fun deactivateSpanning() {
        dismissSecondaryPresentation()
        restoreSourceLayout()
        restoreOrientation()
        showSystemBars(activity.window)
    }

    private fun updateRuntimeState(state: DualScreenRuntimeState) {
        if (runtimeState == state) return

        runtimeState = state
        onRuntimeStateChanged(state)
    }

    private fun dismissSecondaryPresentation() {
        val presentation = secondaryPresentation ?: return
        secondaryPresentation = null
        presentation.setOnDismissListener(null)
        presentation.dismiss()
    }

    private fun restoreSourceLayout() {
        val layoutParams = sourceView.layoutParams ?: return
        layoutParams.width = originalLayoutWidth
        layoutParams.height = originalLayoutHeight
        sourceView.layoutParams = layoutParams
        sourceView.translationY = originalTranslationY
        sourceView.requestLayout()
    }

    private fun lockOrientation(deviceProfile: DualScreenDeviceProfile) {
        if (orientationLocked) return

        orientationLocked = true
        activity.requestedOrientation = deviceProfile.requestedOrientation
    }

    private fun restoreOrientation() {
        if (!orientationLocked) return

        orientationLocked = false
        activity.requestedOrientation = originalRequestedOrientation
    }

    private fun hideSystemBars(window: Window) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun showSystemBars(window: Window) {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = originalSystemBarsBehavior
            show(systemBars())
        }
    }

    private class SpanPresentation(
        context: Context,
        display: Display,
        private val sourceView: View,
        val deviceProfile: DualScreenDeviceProfile,
        private val viewportContentDescription: CharSequence,
        private val handleBackPressed: () -> Unit,
        private val beforeTouchDispatch: (MotionEvent) -> Unit,
    ) : Presentation(context, display) {
        private var secondaryViewport: DualScreenSecondaryViewport? = null

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)

            window?.let { window ->
                WindowCompat.setDecorFitsSystemWindows(window, false)
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    hide(systemBars())
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            }

            secondaryViewport = DualScreenSecondaryViewport(
                context = context,
                sourceView = sourceView,
                deviceProfile = deviceProfile,
                viewportContentDescription = viewportContentDescription,
                beforeTouchDispatch = beforeTouchDispatch,
            ).also(::setContentView)
            setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    handleBackPressed.invoke()
                    true
                } else {
                    false
                }
            }
        }

        fun requestFrame() {
            secondaryViewport?.requestCoalescedFrame()
        }
    }
}

internal class DualScreenSecondaryViewport(
    context: Context,
    sourceView: View,
    private val deviceProfile: DualScreenDeviceProfile,
    viewportContentDescription: CharSequence = context.getText(R.string.dual_screen_secondary_viewport_description),
    private val beforeTouchDispatch: (MotionEvent) -> Unit = {},
) : View(context) {
    private val sourceView = WeakReference(sourceView)
    private val bootstrapUntil = SystemClock.uptimeMillis() + BOOTSTRAP_FRAME_WINDOW_MILLIS
    private val sourceDrawListener = ViewTreeObserver.OnDrawListener {
        if (!drawingSource) requestCoalescedFrame()
    }

    private var drawingSource = false
    private var frameScheduled = false

    init {
        setBackgroundColor(context.resolveDualScreenViewportBackgroundColor())
        contentDescription = viewportContentDescription
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        isClickable = true
        isFocusableInTouchMode = true
        sourceView.getViewTreeObserver().addOnDrawListener(sourceDrawListener)
        requestCoalescedFrame()
    }

    fun requestCoalescedFrame() {
        if (frameScheduled) return

        frameScheduled = true
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        frameScheduled = false

        val source = sourceView.get() ?: return
        if (
            width == 0 ||
            height == 0 ||
            source.width < deviceProfile.logicalViewportWidth ||
            source.height < deviceProfile.logicalCanvasHeight
        ) {
            if (SystemClock.uptimeMillis() < bootstrapUntil) requestCoalescedFrame()
            return
        }

        drawingSource = true
        val canvasSaveCount = canvas.save()
        try {
            canvas.scale(
                deviceProfile.presentationScaleX(width),
                deviceProfile.presentationScaleY(height),
            )
            source.draw(canvas)
        } finally {
            canvas.restoreToCount(canvasSaveCount)
            drawingSource = false
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val source = sourceView.get()
        if (source == null || width == 0 || height == 0) return false

        val logicalPoint = deviceProfile.secondaryLogicalPoint(
            viewportX = event.x,
            viewportY = event.y,
            viewportWidth = width,
            viewportHeight = height,
        )
        val logicalEvent = MotionEvent.obtain(event)
        logicalEvent.setLocation(logicalPoint.x, logicalPoint.y)

        try {
            beforeTouchDispatch(logicalEvent)
            source.dispatchTouchEvent(logicalEvent)
        } finally {
            logicalEvent.recycle()
        }

        if (event.action == MotionEvent.ACTION_UP) performClick()

        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onDetachedFromWindow() {
        val source = sourceView.get()
        if (source != null && source.viewTreeObserver.isAlive) {
            source.viewTreeObserver.removeOnDrawListener(sourceDrawListener)
        }
        super.onDetachedFromWindow()
    }

    private fun Context.resolveDualScreenViewportBackgroundColor(): Int {
        val attributes = obtainStyledAttributes(intArrayOf(android.R.attr.colorBackground))
        return try {
            attributes.getColor(0, android.graphics.Color.WHITE)
        } finally {
            attributes.recycle()
        }
    }

    private companion object {
        const val BOOTSTRAP_FRAME_WINDOW_MILLIS = 1_500L
    }
}
