package com.fsck.k9.activity

import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.view.ActionMode
import com.fsck.k9.ui.R
import com.google.android.material.appbar.MaterialToolbar

/**
 * Hosts contextual actions inside the shared dual-screen canvas.
 *
 * AppCompat normally adds its contextual action bar to the window decor. That decor isn't part of the view projected
 * to the upper display, so selection actions otherwise remain on the lower display. This controller provides the same
 * [ActionMode] callback contract using a toolbar that belongs to the authoritative canvas.
 */
internal class DualScreenActionModeController(
    private val rootView: ViewGroup,
    private val menuInflater: MenuInflater,
) {
    private var activeMode: DualScreenActionMode? = null

    fun start(callback: ActionMode.Callback): ActionMode? {
        activeMode?.finish()

        val toolbar = LayoutInflater.from(rootView.context)
            .inflate(R.layout.dual_screen_action_mode, rootView, false) as MaterialToolbar
        val toolbarHost = ActionModeToolbarHost(rootView, toolbar)
        val mode = DualScreenActionMode(
            hostView = toolbarHost,
            toolbar = toolbar,
            callback = callback,
            menuInflater = menuInflater,
            onFinished = ::onModeFinished,
        )

        activeMode = mode
        rootView.addView(
            toolbarHost,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        toolbarHost.bringToFront()

        if (!callback.onCreateActionMode(mode, toolbar.menu)) {
            activeMode = null
            rootView.removeView(toolbarHost)
            return null
        }

        toolbar.setNavigationOnClickListener { mode.finish() }
        toolbar.setOnMenuItemClickListener { item -> callback.onActionItemClicked(mode, item) }
        mode.invalidate()
        return mode
    }

    fun finish(): Boolean {
        val mode = activeMode ?: return false
        mode.finish()
        return true
    }

    private fun onModeFinished(mode: DualScreenActionMode) {
        if (activeMode !== mode) return

        activeMode = null
        rootView.removeView(mode.hostView)
    }
}

/** DrawerLayout measures content children at the full canvas height, so constrain this overlay explicitly. */
private class ActionModeToolbarHost(
    rootView: ViewGroup,
    toolbar: MaterialToolbar,
) : FrameLayout(rootView.context) {
    private val toolbarHeight = toolbar.minimumHeight

    init {
        addView(
            toolbar,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT,
            ),
        )
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(
            widthMeasureSpec,
            MeasureSpec.makeMeasureSpec(toolbarHeight, MeasureSpec.EXACTLY),
        )
    }
}

@Suppress("TooManyFunctions")
private class DualScreenActionMode(
    val hostView: View,
    val toolbar: MaterialToolbar,
    private val callback: ActionMode.Callback,
    private val menuInflater: MenuInflater,
    private val onFinished: (DualScreenActionMode) -> Unit,
) : ActionMode() {
    private val titleView = toolbar.findViewById<TextView>(R.id.dual_screen_action_mode_title)
    private var subtitle: CharSequence? = null
    private var customView: View? = null
    private var finished = false

    override fun setTitle(title: CharSequence?) {
        titleView.text = title
    }

    override fun setTitle(resId: Int) {
        title = toolbar.context.getText(resId)
    }

    override fun setSubtitle(subtitle: CharSequence?) {
        this.subtitle = subtitle
    }

    override fun setSubtitle(resId: Int) {
        subtitle = toolbar.context.getText(resId)
    }

    override fun setCustomView(view: View?) {
        customView = view
    }

    override fun invalidate() {
        if (finished) return

        callback.onPrepareActionMode(this, menu)
        toolbar.requestLayout()
        toolbar.invalidate()
    }

    override fun finish() {
        if (finished) return

        finished = true
        onFinished(this)
        callback.onDestroyActionMode(this)
    }

    override fun getMenu(): Menu = toolbar.menu

    override fun getTitle(): CharSequence? = titleView.text

    override fun getSubtitle(): CharSequence? = subtitle

    override fun getCustomView(): View? = customView

    override fun getMenuInflater(): MenuInflater = menuInflater
}
