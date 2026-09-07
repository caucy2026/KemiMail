package com.fsck.k9.ui.base

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlin.test.Test

class DualScreenActivityProjectionPolicyTest {
    @Test
    fun `regular activity should use automatic dual screen projection`() {
        val result = shouldUseAutomaticDualScreenProjection(ThemeType.DEFAULT)

        assertThat(result).isTrue()
    }

    @Test
    fun `dialog activity should keep its dedicated window`() {
        val result = shouldUseAutomaticDualScreenProjection(ThemeType.DIALOG)

        assertThat(result).isFalse()
    }
}
