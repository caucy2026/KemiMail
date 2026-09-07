package net.thunderbird.core.android.crashreport

import android.content.Context
import android.content.pm.PackageInfo
import android.os.Build

internal class AndroidCrashReportEnvironmentProvider(
    private val context: Context,
) : CrashReportEnvironmentProvider {
    override fun get(): CrashReportEnvironment {
        val packageInfo = findPackageInfo()
        return CrashReportEnvironment(
            appVersion = packageInfo?.versionName ?: "unknown",
            appVersionCode = packageInfo?.safeVersionCode() ?: 0L,
            androidSdk = Build.VERSION.SDK_INT,
            manufacturer = Build.MANUFACTURER ?: "unknown",
            model = Build.MODEL ?: "unknown",
        )
    }

    private fun findPackageInfo(): PackageInfo? {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0)
        } catch (_: Exception) {
            null
        }
    }

    @Suppress("DEPRECATION")
    private fun PackageInfo.safeVersionCode(): Long {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode else versionCode.toLong()
    }
}
