package net.thunderbird.app.common

import android.app.Activity
import android.os.Bundle
import com.fsck.k9.ui.base.DefaultDisplayActivityRelocator
import kotlin.getValue
import net.thunderbird.app.common.startup.StartupRouter
import net.thunderbird.core.android.common.startup.DatabaseUpgradeInterceptor
import org.koin.android.ext.android.inject

class MainActivity : Activity() {

    private val startupRouter: StartupRouter by inject()
    private val databaseUpgradeInterceptor: DatabaseUpgradeInterceptor by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (DefaultDisplayActivityRelocator.relocateIfNeeded(this)) return

        if (databaseUpgradeInterceptor.checkAndHandleUpgrade(this, intent)) {
            finish()
            return
        }

        startupRouter.routeToNextScreen(this)
        finish()
    }
}
