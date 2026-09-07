package com.fsck.k9.activity

import android.os.Bundle

/**
 * Activity-owned state that has to be restored before [MessageHomeActivity] initializes and displays its fragments.
 *
 * Fragment-owned state such as the current search, selected message, list selection, and scroll position continues to
 * be restored by FragmentManager and the fragment view hierarchy.
 */
internal data class MessageHomeRetainedState(
    val messageViewOnly: Boolean,
    val messageListWasDisplayed: Boolean,
) {
    fun writeTo(outState: Bundle) {
        outState.putBoolean(STATE_MESSAGE_VIEW_ONLY, messageViewOnly)
        outState.putBoolean(STATE_MESSAGE_LIST_WAS_DISPLAYED, messageListWasDisplayed)
    }

    companion object {
        fun restore(savedInstanceState: Bundle?): MessageHomeRetainedState? {
            if (savedInstanceState == null) return null

            return MessageHomeRetainedState(
                messageViewOnly = savedInstanceState.getBoolean(STATE_MESSAGE_VIEW_ONLY),
                messageListWasDisplayed = savedInstanceState.getBoolean(STATE_MESSAGE_LIST_WAS_DISPLAYED),
            )
        }

        private const val STATE_MESSAGE_VIEW_ONLY = "messageViewOnly"
        private const val STATE_MESSAGE_LIST_WAS_DISPLAYED = "messageListWasDisplayed"
    }
}
