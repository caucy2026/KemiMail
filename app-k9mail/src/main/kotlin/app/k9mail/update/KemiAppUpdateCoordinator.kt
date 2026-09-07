package app.k9mail.update

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.fsck.k9.BuildConfig
import com.fsck.k9.activity.MessageHomeActivity
import java.lang.ref.WeakReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.thunderbird.core.logging.Logger

internal class KemiAppUpdateCoordinator(
    private val application: Application,
    private val repository: KemiAppUpdateRepository,
    private val logger: Logger,
) : Application.ActivityLifecycleCallbacks {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var currentActivity: WeakReference<Activity>? = null
    private var checkStarted = false
    private var updateScreenStarted = false
    private var pendingUpdate: KemiAppUpdateInfo? = null

    fun start() {
        application.registerActivityLifecycleCallbacks(this)
    }

    override fun onActivityResumed(activity: Activity) {
        if (activity !is MessageHomeActivity) return
        currentActivity = WeakReference(activity)

        if (!checkStarted) {
            checkStarted = true
            scope.launch {
                pendingUpdate = runCatching {
                    repository.checkForUpdate(
                        packageName = application.packageName,
                        localVersionCode = BuildConfig.VERSION_CODE.toLong(),
                    )
                }.onFailure { exception ->
                    logger.warn(TAG, exception) { "Automatic update check failed" }
                }.getOrNull()
                showPendingUpdate()
            }
        } else {
            showPendingUpdate()
        }
    }

    private fun showPendingUpdate() {
        if (!updateScreenStarted) {
            val updateInfo = pendingUpdate
            val activity = currentActivity?.get()?.takeUnless { it.isFinishing || it.isDestroyed }
            if (updateInfo != null && activity != null) {
                updateScreenStarted = true
                pendingUpdate = null
                activity.startActivity(KemiAppUpdateActivity.createIntent(activity, updateInfo))
            }
        }
    }

    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit

    private companion object {
        const val TAG = "KemiAppUpdateCoordinator"
    }
}
