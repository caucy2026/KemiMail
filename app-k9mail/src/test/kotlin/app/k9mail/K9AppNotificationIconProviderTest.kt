package app.k9mail

import app.k9mail.provider.K9AppNotificationIconProvider
import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test

class K9AppNotificationIconProviderTest {
    @Test
    fun `provides correct KEMI Mail notification icon`() {
        val provider = K9AppNotificationIconProvider()
        val icon = provider.pushNotificationIcon

        assertThat(icon)
            .isEqualTo(app.k9mail.core.ui.legacy.theme2.k9mail.R.drawable.ic_logo_kemi_mail_white)
    }
}
