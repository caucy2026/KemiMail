package com.fsck.k9.ui.messageview

import android.app.Application
import android.net.Uri
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import com.fsck.k9.mailstore.AttachmentViewInfo
import com.fsck.k9.ui.helper.SizeFormatter
import kotlinx.collections.immutable.persistentListOf
import net.thunderbird.core.ui.compose.theme2.k9mail.K9MailTheme2
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class AttachmentListModalBottomSheetTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `long press on unlocked attachment requests cross screen drag`() {
        val invokedActions = mutableListOf<AttachmentViewInfo>()
        val attachment = createAttachment()
        setContent(
            attachment = attachment,
            isLocked = false,
            onAttachmentLongClick = invokedActions::add,
        )

        composeTestRule.onNodeWithTag(ATTACHMENT_DRAG_SOURCE_TEST_TAG)
            .performTouchInput { longClick() }

        assertThat(invokedActions).containsExactly(attachment)
    }

    @Test
    fun `long press on locked attachment does not request cross screen drag`() {
        val invokedActions = mutableListOf<AttachmentViewInfo>()
        setContent(
            attachment = createAttachment(),
            isLocked = true,
            onAttachmentLongClick = invokedActions::add,
        )

        composeTestRule.onNodeWithTag(ATTACHMENT_DRAG_SOURCE_TEST_TAG)
            .performTouchInput { longClick() }

        assertThat(invokedActions).isEmpty()
    }

    @Test
    fun `long press without safe preview capability does not request cross screen drag`() {
        val invokedActions = mutableListOf<AttachmentViewInfo>()
        setContent(
            attachment = createAttachment(),
            isLocked = false,
            canDragAttachment = false,
            onAttachmentLongClick = invokedActions::add,
        )

        composeTestRule.onNodeWithTag(ATTACHMENT_DRAG_SOURCE_TEST_TAG)
            .performTouchInput { longClick() }

        assertThat(invokedActions).isEmpty()
    }

    private fun setContent(
        attachment: AttachmentViewInfo,
        isLocked: Boolean,
        canDragAttachment: Boolean = true,
        onAttachmentLongClick: (AttachmentViewInfo) -> Unit,
    ) {
        val context = ApplicationProvider.getApplicationContext<Application>()
        composeTestRule.setContent {
            K9MailTheme2 {
                AttachmentListContent(
                    attachments = persistentListOf(AttachmentListItemModel(attachment, isLocked)),
                    sizeFormatter = SizeFormatter(context.resources),
                    callbacks = AttachmentListCallbacks(
                        onDismissRequest = {},
                        drag = AttachmentDragCallbacks(
                            canStart = { canDragAttachment },
                            start = { attachment ->
                                onAttachmentLongClick(attachment)
                                true
                            },
                        ),
                        onAttachmentClick = {},
                        onSaveClick = {},
                        onSaveAllClick = {},
                    ),
                )
            }
        }
    }

    private fun createAttachment(): AttachmentViewInfo {
        return AttachmentViewInfo(
            "image/png",
            "attachment.png",
            1024,
            Uri.parse("content://com.example.attachments/1"),
            false,
            null,
            true,
        )
    }
}
