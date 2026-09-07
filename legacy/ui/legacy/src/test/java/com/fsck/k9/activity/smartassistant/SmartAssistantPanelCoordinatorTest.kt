package com.fsck.k9.activity.smartassistant

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlin.test.Test
import net.thunderbird.feature.smartassistant.SmartAssistantAccountReference
import net.thunderbird.feature.smartassistant.SmartAssistantContext
import net.thunderbird.feature.smartassistant.SmartAssistantPanelHost
import net.thunderbird.feature.smartassistant.SmartAssistantScene
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SmartAssistantPanelCoordinatorTest {
    @Test
    fun `no-op host keeps panel hidden and does not request context`() {
        val container = createContainerWithChild()
        var contextRequested = false
        val coordinator = SmartAssistantPanelCoordinator(container, UnavailablePanelHost)

        coordinator.updateContext {
            contextRequested = true
            SmartAssistantContext(SmartAssistantScene.MESSAGE_LIST)
        }

        assertThat(coordinator.isAvailable).isFalse()
        assertThat(contextRequested).isFalse()
        assertThat(container.visibility).isEqualTo(View.GONE)
        assertThat(container.childCount).isEqualTo(0)
    }

    @Test
    fun `available host receives lazily created stable context`() {
        val container = createContainerWithChild()
        val host = RecordingPanelHost()
        val coordinator = SmartAssistantPanelCoordinator(container, host)
        val context = SmartAssistantContext(
            scene = SmartAssistantScene.MESSAGE_LIST,
            account = SmartAssistantAccountReference("account"),
        )

        coordinator.updateContext { context }

        assertThat(coordinator.isAvailable).isTrue()
        assertThat(container.visibility).isEqualTo(View.VISIBLE)
        assertThat(host.attachedContainer).isEqualTo(container)
        assertThat(host.lastContext).isEqualTo(context)

        coordinator.destroy()

        assertThat(host.detached).isTrue()
        assertThat(container.visibility).isEqualTo(View.GONE)
        assertThat(container.childCount).isEqualTo(0)
    }

    private fun createContainerWithChild(): FrameLayout {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return FrameLayout(context).apply {
            addView(View(context))
        }
    }

    private class RecordingPanelHost : SmartAssistantPanelHost {
        override val isAvailable = true
        var attachedContainer: ViewGroup? = null
        var lastContext: SmartAssistantContext? = null
        var detached = false

        override fun attach(container: ViewGroup) {
            attachedContainer = container
        }

        override fun updateContext(context: SmartAssistantContext) {
            lastContext = context
        }

        override fun detach() {
            detached = true
            attachedContainer = null
        }
    }

    private object UnavailablePanelHost : SmartAssistantPanelHost {
        override val isAvailable: Boolean = false

        override fun attach(container: ViewGroup) = Unit

        override fun updateContext(context: SmartAssistantContext) = Unit

        override fun detach() = Unit
    }
}
