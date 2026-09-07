package app.k9mail

import app.k9mail.update.KemiAppUpdateCoordinator
import net.thunderbird.app.common.BaseApplication
import org.koin.android.ext.android.inject
import org.koin.core.module.Module

class K9App : BaseApplication() {
    private val appUpdateCoordinator: KemiAppUpdateCoordinator by inject()

    override fun onCreate() {
        super.onCreate()
        appUpdateCoordinator.start()
    }

    override fun provideAppModule(): Module = appModule
}
