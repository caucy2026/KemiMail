package com.fsck.k9.ui.base

import android.app.Activity
import android.app.ActivityOptions
import android.content.Intent
import android.os.Build
import android.view.Display
import androidx.annotation.RequiresApi

/**
 * Relaunches externally entered activities on the primary display before they consume their Intent.
 *
 * KEMI's upper panel is reserved for a Presentation. Launchers, notifications, shortcuts, and external mail Intents
 * can otherwise create an Activity there and leave the app without a primary-display workspace.
 */
object DefaultDisplayActivityRelocator {
    private const val EXTRA_RELOCATION_ATTEMPTED =
        "com.fsck.k9.extra.DEFAULT_DISPLAY_RELOCATION_ATTEMPTED"

    @JvmStatic
    fun relocateIfNeeded(activity: Activity): Boolean {
        return relocateIfNeeded(activity, activity.intent)
    }

    @JvmStatic
    fun relocateIfNeeded(activity: Activity, sourceIntent: Intent): Boolean {
        return relocateIfNeeded(
            activity = activity,
            sourceIntent = sourceIntent,
            currentDisplayId = activity.getDisplayIdCompat(),
            sdkInt = Build.VERSION.SDK_INT,
        )
    }

    internal fun relocateIfNeeded(
        activity: Activity,
        sourceIntent: Intent,
        currentDisplayId: Int,
        sdkInt: Int,
    ): Boolean {
        val relocationAttempted = sourceIntent.getBooleanExtra(EXTRA_RELOCATION_ATTEMPTED, false)
        val canRelaunch = shouldRelocate(sdkInt, currentDisplayId, relocationAttempted) &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

        if (canRelaunch) {
            relaunchOnDefaultDisplay(activity, sourceIntent)
        }

        return canRelaunch
    }

    internal fun shouldRelocate(
        sdkInt: Int,
        currentDisplayId: Int,
        relocationAttempted: Boolean,
    ): Boolean {
        return sdkInt >= Build.VERSION_CODES.O &&
            currentDisplayId != Display.DEFAULT_DISPLAY &&
            !relocationAttempted
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun relaunchOnDefaultDisplay(activity: Activity, sourceIntent: Intent) {
        val relaunchIntent = Intent(sourceIntent).apply {
            setClass(activity, activity.javaClass)
            putExtra(EXTRA_RELOCATION_ATTEMPTED, true)
        }
        val options = ActivityOptions.makeBasic()
            .setLaunchDisplayId(Display.DEFAULT_DISPLAY)
            .toBundle()

        // Mark this instance as finishing first so singleTop activities aren't selected for the relaunch.
        activity.finish()
        activity.startActivity(relaunchIntent, options)
    }
}
