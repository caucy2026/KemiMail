package com.fsck.k9.ui.messagelist

import android.content.Context
import android.view.ContextThemeWrapper
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import androidx.appcompat.widget.PopupMenu
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotNull
import com.fsck.k9.K9RobolectricTest
import com.fsck.k9.ui.R
import kotlin.test.Test

class MessageListExitMenuTest : K9RobolectricTest() {
    @Test
    fun `legacy message list should place close mail immediately after compose`() {
        val menu = inflateMenu(R.menu.message_list_option_menu)

        assertCloseAction(menu)
    }

    @Test
    fun `new message list should place close mail immediately after compose`() {
        val menu = inflateMenu(R.menu.new_message_list_option_menu)

        assertCloseAction(menu)
    }

    private fun assertCloseAction(menu: Menu) {
        val closeItem = menu.findItem(R.id.close_mail)

        assertThat(closeItem).isNotNull()
        assertThat(closeItem.isVisible).isFalse()
        assertThat(closeItem.title.toString()).isEqualTo("Close mail")
        assertThat(itemIndex(menu, R.id.close_mail)).isEqualTo(itemIndex(menu, R.id.compose) + 1)
    }

    private fun inflateMenu(menuResource: Int): Menu {
        val context = ContextThemeWrapper(
            ApplicationProvider.getApplicationContext<Context>(),
            R.style.Theme_Legacy_Test,
        )
        return PopupMenu(context, View(context)).menu.also { menu ->
            MenuInflater(context).inflate(menuResource, menu)
        }
    }

    private fun itemIndex(menu: Menu, itemId: Int): Int {
        return (0 until menu.size()).first { index -> menu.getItem(index).itemId == itemId }
    }
}
