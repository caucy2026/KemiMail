package com.fsck.k9.activity

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import androidx.appcompat.widget.PopupMenu
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import com.fsck.k9.K9RobolectricTest
import com.fsck.k9.ui.R
import kotlin.test.Test

class DualScreenComposeOverflowMenuStateTest : K9RobolectricTest() {
    @Test
    fun `attachment popup should use canvas when its anchor is projected`() {
        val result = DualScreenComposePopupRouting.shouldShowInCanvas(
            usesProjectedCanvas = true,
            canAnchor = true,
            hasVisibleActions = true,
        )

        assertThat(result).isTrue()
    }

    @Test
    fun `attachment popup should keep native behavior on a regular display`() {
        val result = DualScreenComposePopupRouting.shouldShowInCanvas(
            usesProjectedCanvas = false,
            canAnchor = true,
            hasVisibleActions = true,
        )

        assertThat(result).isFalse()
    }

    @Test
    fun `projected compose replaces native overflow items with one canvas action`() {
        // Arrange
        val menu = createComposeMenu().apply(::hideOpenPgpActions)
        val testSubject = DualScreenComposeOverflowMenuState()

        // Act
        testSubject.prepare(menu, usesProjectedCanvas = true)

        // Assert
        assertThat(menu.findItem(R.id.dual_screen_more_actions).isVisible).isTrue()
        assertThat(testSubject.visibleActions.map { it.itemId }).containsExactly(
            R.id.add_from_contacts,
            R.id.save,
            R.id.discard,
            R.id.read_receipt,
        )
        testSubject.visibleActions.forEach { item -> assertThat(item.isVisible).isFalse() }
    }

    @Test
    fun `restoring compose menu preserves visibility before projection hiding`() {
        // Arrange
        val menu = createComposeMenu().apply {
            hideOpenPgpActions(this)
            findItem(R.id.openpgp_encrypt_enable).isVisible = true
        }
        val testSubject = DualScreenComposeOverflowMenuState()
        testSubject.prepare(menu, usesProjectedCanvas = true)

        // Act
        testSubject.restore(menu)
        testSubject.prepare(menu, usesProjectedCanvas = false)

        // Assert
        assertThat(menu.findItem(R.id.openpgp_encrypt_enable).isVisible).isTrue()
        assertThat(menu.findItem(R.id.openpgp_encrypt_disable).isVisible).isFalse()
        assertThat(menu.findItem(R.id.save).isVisible).isTrue()
        assertThat(menu.findItem(R.id.dual_screen_more_actions).isVisible).isFalse()
    }

    private fun createComposeMenu(): Menu {
        val context = ContextThemeWrapper(
            ApplicationProvider.getApplicationContext<Context>(),
            R.style.Theme_Legacy_Test,
        )
        return PopupMenu(context, View(context)).menu.also { menu ->
            MenuInflater(context).inflate(R.menu.message_compose_option, menu)
        }
    }

    private fun hideOpenPgpActions(menu: Menu) {
        listOf(
            R.id.openpgp_encrypt_enable,
            R.id.openpgp_encrypt_disable,
            R.id.openpgp_sign_only,
            R.id.openpgp_sign_only_disable,
            R.id.openpgp_inline_enable,
            R.id.openpgp_inline_disable,
        ).forEach { itemId -> menu.findItem(itemId).isVisible = false }
    }
}
