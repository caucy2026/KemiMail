package com.fsck.k9.activity

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.fsck.k9.ui.R
import kotlin.test.Test
import net.thunderbird.core.preference.DualScreenMode

class DualScreenComposeWorkspaceTest {
    private val deviceProfile = DualScreenDeviceProfiles.KEMI_GENERATION_1

    @Test
    fun `smart mode with an eligible display prepares the two-zone compose workspace`() {
        val workspace = DualScreenComposeWorkspace.resolve(
            savedMode = DualScreenMode.SMART,
            deviceProfile = deviceProfile,
            recoveryPending = false,
        )

        assertThat(workspace.runtimeState).isEqualTo(DualScreenRuntimeState.SMART)
        assertThat(workspace.deviceProfile).isEqualTo(deviceProfile)
        assertThat(workspace.layoutResource).isEqualTo(R.layout.smart_message_compose)
    }

    @Test
    fun `immersive mode keeps the single continuous compose layout`() {
        val workspace = DualScreenComposeWorkspace.resolve(
            savedMode = DualScreenMode.IMMERSIVE,
            deviceProfile = deviceProfile,
            recoveryPending = false,
        )

        assertThat(workspace.runtimeState).isEqualTo(DualScreenRuntimeState.IMMERSIVE)
        assertThat(workspace.layoutResource).isEqualTo(R.layout.message_compose)
    }

    @Test
    fun `single-screen launch keeps the established compose layout`() {
        val workspace = DualScreenComposeWorkspace.resolve(
            savedMode = DualScreenMode.SMART,
            deviceProfile = null,
            recoveryPending = false,
        )

        assertThat(workspace.runtimeState).isEqualTo(DualScreenRuntimeState.SINGLE_SCREEN)
        assertThat(workspace.layoutResource).isEqualTo(R.layout.message_compose)
    }

    @Test
    fun `pending recovery does not create a smart workspace before user confirmation`() {
        val workspace = DualScreenComposeWorkspace.resolve(
            savedMode = DualScreenMode.SMART,
            deviceProfile = deviceProfile,
            recoveryPending = true,
        )

        assertThat(workspace.runtimeState).isEqualTo(DualScreenRuntimeState.SINGLE_SCREEN)
        assertThat(workspace.layoutResource).isEqualTo(R.layout.message_compose)
    }

    @Test
    fun `reply and forward actions use the source message while new mail and drafts do not`() {
        val sourceReferenceActions = listOf(
            MessageCompose.Action.REPLY,
            MessageCompose.Action.REPLY_ALL,
            MessageCompose.Action.FORWARD,
            MessageCompose.Action.FORWARD_AS_ATTACHMENT,
        )

        sourceReferenceActions.forEach { action -> assertThat(action.usesSourceReference()).isTrue() }
        assertThat(MessageCompose.Action.COMPOSE.usesSourceReference()).isFalse()
        assertThat(MessageCompose.Action.EDIT_DRAFT.usesSourceReference()).isFalse()
    }
}
