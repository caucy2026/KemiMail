package app.k9mail.core.ui.compose.common.window

import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlin.test.Test

class SpannedDualScreenCanvasTest {

    @Test
    fun `single viewport canvas is not spanned`() {
        assertThat(isSpannedDualScreenCanvas(canvasHeight = 640.dp, viewportHeight = 640.dp)).isFalse()
    }

    @Test
    fun `expanded two viewport canvas is spanned`() {
        assertThat(isSpannedDualScreenCanvas(canvasHeight = 1280.dp, viewportHeight = 640.dp)).isTrue()
    }
}
