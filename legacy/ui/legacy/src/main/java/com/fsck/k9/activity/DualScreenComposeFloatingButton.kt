package com.fsck.k9.activity

import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton

internal fun FloatingActionButton.keepVisibleDuringDualScreenScrolling() {
    val coordinatorLayoutParams = layoutParams as? CoordinatorLayout.LayoutParams ?: return

    coordinatorLayoutParams.behavior = null
    animate().cancel()
    translationY = 0f
}
