package com.fsck.k9.activity.attachment

import android.content.Context
import android.net.Uri
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.fsck.k9.mailstore.AttachmentViewInfo
import kotlin.test.Test
import net.thunderbird.core.ui.theme.api.FeatureThemeProvider
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DualScreenAttachmentWorkspaceCoordinatorTest {
    @Test
    fun `supported drag shows lower target then drop opens both workspace hosts`() {
        val fixture = createFixture()

        assertThat(fixture.testSubject.beginDrag(createAttachment())).isTrue()
        assertThat(fixture.upperHost.visibility).isEqualTo(View.GONE)
        assertThat(fixture.upperHost.childCount).isEqualTo(0)
        assertThat(fixture.lowerHost.visibility).isEqualTo(View.VISIBLE)
        assertThat(fixture.lowerHost.childCount).isEqualTo(1)

        assertThat(fixture.testSubject.completePendingDrag()).isTrue()
        assertThat(fixture.upperHost.visibility).isEqualTo(View.VISIBLE)
        assertThat(fixture.upperHost.childCount).isEqualTo(1)
        assertThat(fixture.lowerHost.visibility).isEqualTo(View.VISIBLE)
        assertThat(fixture.lowerHost.childCount).isEqualTo(1)
    }

    @Test
    fun `unsupported attachment cannot begin drag`() {
        val fixture = createFixture()

        assertThat(fixture.testSubject.beginDrag(createAttachment(mimeType = "application/pdf"))).isFalse()

        assertHostsCleared(fixture)
    }

    @Test
    fun `bounded pdf opens one full canvas reader and clears split hosts`() {
        val fixture = createFixture()

        assertThat(fixture.testSubject.showIfSupported(createAttachment(mimeType = "application/pdf"))).isTrue()

        assertThat(fixture.upperHost.visibility).isEqualTo(View.GONE)
        assertThat(fixture.lowerHost.visibility).isEqualTo(View.GONE)
        assertThat(fixture.continuousReaderHost.visibility).isEqualTo(View.VISIBLE)
        assertThat(fixture.continuousReaderHost.childCount).isEqualTo(1)
    }

    @Test
    fun `image workspace can transition to one full canvas reader`() {
        val fixture = createFixture()
        val attachment = createAttachment()
        fixture.testSubject.showIfSupported(attachment)

        assertThat(fixture.testSubject.showContinuousIfSupported(attachment)).isTrue()

        assertThat(fixture.upperHost.visibility).isEqualTo(View.GONE)
        assertThat(fixture.lowerHost.visibility).isEqualTo(View.GONE)
        assertThat(fixture.continuousReaderHost.visibility).isEqualTo(View.VISIBLE)
    }

    @Test
    fun `cancel pending drag clears target without opening preview`() {
        val fixture = createFixture()
        fixture.testSubject.beginDrag(createAttachment())

        assertThat(fixture.testSubject.handleBack()).isTrue()
        assertThat(fixture.testSubject.completePendingDrag()).isFalse()

        assertHostsCleared(fixture)
    }

    @Test
    fun `supported attachment shows both workspace hosts and dismiss clears them`() {
        val fixture = createFixture()

        assertThat(fixture.testSubject.showIfSupported(createAttachment())).isTrue()
        assertThat(fixture.upperHost.visibility).isEqualTo(View.VISIBLE)
        assertThat(fixture.lowerHost.visibility).isEqualTo(View.VISIBLE)
        assertThat(fixture.upperHost.childCount).isEqualTo(1)
        assertThat(fixture.lowerHost.childCount).isEqualTo(1)

        fixture.testSubject.dismiss()

        assertHostsCleared(fixture)
    }

    @Test
    fun `unsupported request clears a previous preview before falling back`() {
        val fixture = createFixture()
        fixture.testSubject.showIfSupported(createAttachment())

        val handled = fixture.testSubject.showIfSupported(createAttachment(mimeType = "application/zip"))

        assertThat(handled).isFalse()
        assertHostsCleared(fixture)
    }

    @Test
    fun `dismiss clears an active workspace`() {
        val fixture = createFixture()
        fixture.testSubject.showIfSupported(createAttachment())

        fixture.testSubject.dismiss()

        assertHostsCleared(fixture)
    }

    @Test
    fun `back closes any active reader before activity navigation`() {
        val fixture = createFixture()
        fixture.testSubject.showContinuousIfSupported(createAttachment())

        assertThat(fixture.testSubject.handleBack()).isTrue()
        assertThat(fixture.testSubject.handleBack()).isFalse()

        assertHostsCleared(fixture)
    }

    private fun assertHostsCleared(fixture: Fixture) {
        assertThat(fixture.upperHost.visibility).isEqualTo(View.GONE)
        assertThat(fixture.lowerHost.visibility).isEqualTo(View.GONE)
        assertThat(fixture.upperHost.childCount).isEqualTo(0)
        assertThat(fixture.lowerHost.childCount).isEqualTo(0)
        assertThat(fixture.continuousReaderHost.visibility).isEqualTo(View.GONE)
        assertThat(fixture.continuousReaderHost.childCount).isEqualTo(0)
    }

    private fun createFixture(
        onOpenExternally: (AttachmentViewInfo) -> Unit = {},
        onSave: (AttachmentViewInfo) -> Unit = {},
    ): Fixture {
        val applicationContext = ApplicationProvider.getApplicationContext<Context>()
        val themedContext = ContextThemeWrapper(
            applicationContext,
            com.google.android.material.R.style.Theme_Material3_Light_NoActionBar,
        )
        val upperHost = FrameLayout(themedContext)
        val lowerHost = FrameLayout(themedContext)
        val continuousReaderHost = FrameLayout(themedContext)
        val coordinator = DualScreenAttachmentWorkspaceCoordinator(
            upperHost = upperHost,
            lowerHost = lowerHost,
            continuousReaderHost = continuousReaderHost,
            themeProvider = FakeThemeProvider,
            onOpenExternally = onOpenExternally,
            onSave = onSave,
        )
        return Fixture(coordinator, upperHost, lowerHost, continuousReaderHost)
    }

    private fun createAttachment(mimeType: String = "image/png"): AttachmentViewInfo {
        return AttachmentViewInfo(
            mimeType,
            "attachment.png",
            1024,
            Uri.parse("content://com.example.attachments/1"),
            false,
            null,
            true,
        )
    }

    private data class Fixture(
        val testSubject: DualScreenAttachmentWorkspaceCoordinator,
        val upperHost: FrameLayout,
        val lowerHost: FrameLayout,
        val continuousReaderHost: FrameLayout,
    )

    private object FakeThemeProvider : FeatureThemeProvider {
        @Composable
        override fun WithTheme(content: @Composable () -> Unit) {
            content()
        }

        @Composable
        override fun WithTheme(darkTheme: Boolean, content: @Composable () -> Unit) {
            content()
        }
    }
}
