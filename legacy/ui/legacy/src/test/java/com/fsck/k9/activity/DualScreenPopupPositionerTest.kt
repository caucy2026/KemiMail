package com.fsck.k9.activity

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DualScreenPopupPositionerTest {
    @Test
    fun `showing another popup should replace the current popup`() {
        // Arrange
        val context = ApplicationProvider.getApplicationContext<Context>()
        val rootView = FrameLayout(context).apply {
            layout(0, 0, 1920, 2560)
        }
        val anchor = View(context)
        rootView.addView(anchor)
        anchor.layout(1680, 40, 1800, 100)
        val testSubject = DualScreenAnchoredPopupController(
            rootView = rootView,
            viewportHeightProvider = { 1280 },
        )
        testSubject.show(anchor, View(context))
        val firstPopup = rootView.getChildAt(1)

        // Act
        testSubject.show(anchor, View(context))

        // Assert
        assertThat(firstPopup.parent).isNull()
        assertThat(rootView.childCount).isEqualTo(2)
    }

    @Test
    fun `popup should stay below an anchor on the upper display`() {
        // Arrange
        val anchorBounds = DualScreenAnchorBounds(left = 1680, top = 40, right = 1800, bottom = 100)

        // Act
        val result = DualScreenPopupPositioner.calculate(
            anchorBounds = anchorBounds,
            popupWidth = 300,
            popupHeight = 500,
            rootWidth = 1920,
            rootHeight = 2560,
            viewportHeight = 1280,
        )

        // Assert
        assertThat(result).isEqualTo(DualScreenPopupPosition(x = 1500, y = 100))
    }

    @Test
    fun `popup should move above its anchor instead of crossing into the lower display`() {
        // Arrange
        val anchorBounds = DualScreenAnchorBounds(left = 1680, top = 1160, right = 1800, bottom = 1220)

        // Act
        val result = DualScreenPopupPositioner.calculate(
            anchorBounds = anchorBounds,
            popupWidth = 300,
            popupHeight = 500,
            rootWidth = 1920,
            rootHeight = 2560,
            viewportHeight = 1280,
        )

        // Assert
        assertThat(result).isEqualTo(DualScreenPopupPosition(x = 1500, y = 660))
    }

    @Test
    fun `popup triggered on the lower display should stay on the lower display`() {
        // Arrange
        val anchorBounds = DualScreenAnchorBounds(left = 1680, top = 1320, right = 1800, bottom = 1380)

        // Act
        val result = DualScreenPopupPositioner.calculate(
            anchorBounds = anchorBounds,
            popupWidth = 300,
            popupHeight = 500,
            rootWidth = 1920,
            rootHeight = 2560,
            viewportHeight = 1280,
        )

        // Assert
        assertThat(result).isEqualTo(DualScreenPopupPosition(x = 1500, y = 1380))
    }
}
