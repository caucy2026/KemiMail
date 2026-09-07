package com.fsck.k9.ui.messagelist

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MessageListFragmentAttachmentTest {
    @Test
    fun `legacy fragment accepts active message before being attached`() {
        val testSubject = LegacyMessageListFragment()

        testSubject.setActiveMessage(null)
    }

    @Test
    fun `new fragment accepts active message before being attached`() {
        val testSubject = MessageListFragment()

        testSubject.setActiveMessage(null)
    }
}
