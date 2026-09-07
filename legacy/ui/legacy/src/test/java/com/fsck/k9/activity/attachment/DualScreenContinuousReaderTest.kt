package com.fsck.k9.activity.attachment

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import assertk.assertThat
import assertk.assertions.containsExactly
import net.thunderbird.core.ui.compose.theme2.k9mail.K9MailTheme2
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DualScreenContinuousReaderTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val invokedActions = mutableListOf<String>()

    @Test
    fun `reader controls expose bounded zoom and existing safe attachment actions`() {
        setContent()

        composeTestRule.onNodeWithText("Zoom out").assertIsDisplayed()
        composeTestRule.onNodeWithText("150%").assertIsDisplayed()
        composeTestRule.onNodeWithText("Zoom in").assertIsDisplayed()
        composeTestRule.onNodeWithText("Open with system app").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Save attachment").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `reader controls delegate each action once`() {
        setContent()

        composeTestRule.onNodeWithTag(CONTINUOUS_READER_ZOOM_OUT_TEST_TAG).performClick()
        composeTestRule.onNodeWithTag(CONTINUOUS_READER_ZOOM_RESET_TEST_TAG).performClick()
        composeTestRule.onNodeWithTag(CONTINUOUS_READER_ZOOM_IN_TEST_TAG).performClick()
        composeTestRule.onNodeWithTag(CONTINUOUS_READER_OPEN_TEST_TAG).performScrollTo().performClick()
        composeTestRule.onNodeWithTag(CONTINUOUS_READER_SAVE_TEST_TAG).performScrollTo().performClick()

        assertThat(invokedActions).containsExactly("out", "reset", "in", "open", "save")
    }

    @Test
    @Config(qualifiers = "zh-rCN")
    fun `reader controls expose simplified Chinese labels`() {
        setContent()

        composeTestRule.onNodeWithText("缩小").assertIsDisplayed()
        composeTestRule.onNodeWithText("放大").assertIsDisplayed()
        composeTestRule.onNodeWithText("使用系统应用打开").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("保存附件").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `reader controls remain reachable at two hundred percent font scale`() {
        setContent(fontScale = 2f)

        composeTestRule.onNodeWithTag(CONTINUOUS_READER_ZOOM_OUT_TEST_TAG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(CONTINUOUS_READER_ZOOM_IN_TEST_TAG).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag(CONTINUOUS_READER_OPEN_TEST_TAG).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag(CONTINUOUS_READER_SAVE_TEST_TAG).performScrollTo().assertIsDisplayed()
    }

    private fun setContent(fontScale: Float = 1f) {
        composeTestRule.setContent {
            val currentDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(currentDensity.density, fontScale),
            ) {
                K9MailTheme2 {
                    ContinuousReaderControls(
                        zoom = 1.5f,
                        onZoomOut = { invokedActions.add("out") },
                        onZoomReset = { invokedActions.add("reset") },
                        onZoomIn = { invokedActions.add("in") },
                        onOpenExternally = { invokedActions.add("open") },
                        onSave = { invokedActions.add("save") },
                    )
                }
            }
        }
    }
}
