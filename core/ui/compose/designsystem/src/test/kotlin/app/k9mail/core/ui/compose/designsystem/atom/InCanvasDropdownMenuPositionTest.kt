package app.k9mail.core.ui.compose.designsystem.atom

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.Test

class InCanvasDropdownMenuPositionTest {
    @Test
    fun `upper-screen anchor keeps menu in upper viewport`() {
        val position = calculateInCanvasDropdownPosition(
            anchorBounds = IntRect(left = 1800, top = 40, right = 1880, bottom = 120),
            popupSize = IntSize(width = 240, height = 180),
            rootSize = IntSize(width = 1920, height = 2560),
            viewportHeight = 1280,
        )

        assertThat(position).isEqualTo(IntOffset(x = 1640, y = 120))
    }

    @Test
    fun `lower-screen anchor keeps menu in lower viewport`() {
        val position = calculateInCanvasDropdownPosition(
            anchorBounds = IntRect(left = 1800, top = 1320, right = 1880, bottom = 1400),
            popupSize = IntSize(width = 240, height = 180),
            rootSize = IntSize(width = 1920, height = 2560),
            viewportHeight = 1280,
        )

        assertThat(position).isEqualTo(IntOffset(x = 1640, y = 1400))
    }
}
