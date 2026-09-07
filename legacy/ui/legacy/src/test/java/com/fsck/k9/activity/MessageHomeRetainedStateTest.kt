package com.fsck.k9.activity

import android.os.Bundle
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MessageHomeRetainedStateTest {
    @Test
    fun `fresh launch has no retained state`() {
        assertThat(MessageHomeRetainedState.restore(null)).isNull()
    }

    @Test
    fun `message navigation state survives Activity recreation`() {
        val state = MessageHomeRetainedState(
            messageViewOnly = true,
            messageListWasDisplayed = true,
        )
        val bundle = Bundle()

        state.writeTo(bundle)

        assertThat(MessageHomeRetainedState.restore(bundle)).isEqualTo(state)
    }

    @Test
    fun `missing values restore to safe navigation defaults`() {
        assertThat(MessageHomeRetainedState.restore(Bundle())).isEqualTo(
            MessageHomeRetainedState(
                messageViewOnly = false,
                messageListWasDisplayed = false,
            ),
        )
    }
}
