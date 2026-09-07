package com.fsck.k9.activity

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.Test

class DualScreenFloatingControlPositionControllerTest {
    @Test
    fun `right-moving control should stay inside its two anchors`() {
        val testSubject = DualScreenFloatingControlPositionController(
            direction = DualScreenFloatingControlDirection.RIGHT,
            insetDistance = 96f,
        )

        testSubject.startDrag(0f)

        assertThat(testSubject.dragBy(-40f)).isEqualTo(0f)
        assertThat(testSubject.dragBy(120f)).isEqualTo(96f)
    }

    @Test
    fun `left-moving control should stay inside its two anchors`() {
        val testSubject = DualScreenFloatingControlPositionController(
            direction = DualScreenFloatingControlDirection.LEFT,
            insetDistance = 96f,
        )

        testSubject.startDrag(0f)

        assertThat(testSubject.dragBy(40f)).isEqualTo(0f)
        assertThat(testSubject.dragBy(-120f)).isEqualTo(-96f)
    }

    @Test
    fun `control should settle at edge before the midpoint`() {
        val testSubject = DualScreenFloatingControlPositionController(
            direction = DualScreenFloatingControlDirection.RIGHT,
            insetDistance = 96f,
        )
        testSubject.startDrag(0f)
        testSubject.dragBy(47f)

        val result = testSubject.settle()

        assertThat(result).isEqualTo(0f)
        assertThat(testSubject.position).isEqualTo(DualScreenFloatingControlPosition.EDGE)
    }

    @Test
    fun `control should settle inward at or beyond the midpoint`() {
        val testSubject = DualScreenFloatingControlPositionController(
            direction = DualScreenFloatingControlDirection.LEFT,
            insetDistance = 96f,
        )
        testSubject.startDrag(0f)
        testSubject.dragBy(-48f)

        val result = testSubject.settle()

        assertThat(result).isEqualTo(-96f)
        assertThat(testSubject.position).isEqualTo(DualScreenFloatingControlPosition.INSET)
    }

    @Test
    fun `toggle should move inward and then restore the edge position`() {
        val testSubject = DualScreenFloatingControlPositionController(
            direction = DualScreenFloatingControlDirection.RIGHT,
            insetDistance = 96f,
        )

        val insetTranslation = testSubject.toggle()
        val restoredTranslation = testSubject.toggle()

        assertThat(insetTranslation).isEqualTo(96f)
        assertThat(restoredTranslation).isEqualTo(0f)
        assertThat(testSubject.position).isEqualTo(DualScreenFloatingControlPosition.EDGE)
    }
}
