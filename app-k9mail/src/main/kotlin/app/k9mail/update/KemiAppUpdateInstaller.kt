package app.k9mail.update

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import net.thunderbird.core.logging.Logger

internal class KemiAppUpdateInstaller(
    private val logger: Logger,
) {
    fun openStore(activity: Activity, deepLink: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(deepLink)).apply {
            setPackage(STORE_PACKAGE_NAME)
        }
        return activity.startIfResolvable(intent)
    }

    fun canRequestPackageInstalls(activity: Activity): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O || activity.packageManager.canRequestPackageInstalls()
    }

    fun createUnknownSourcesIntent(activity: Activity): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${activity.packageName}"),
        )
        return intent.takeIf { it.isResolvable(activity) }
    }

    fun install(activity: Activity, apkFile: File): Boolean {
        return runCatching {
            val uri = FileProvider.getUriForFile(
                activity,
                "${activity.packageName}.selfupdatefileprovider",
                apkFile,
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, APK_MIME_TYPE)
                clipData = ClipData.newRawUri(apkFile.name, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                setPackage(PACKAGE_INSTALLER_PACKAGE)
            }
            val handlers = if (installIntent.isResolvable(activity)) {
                activity.packageManager.queryIntentActivities(installIntent, 0)
            } else {
                emptyList()
            }
            if (handlers.isEmpty()) {
                false
            } else {
                handlers.forEach { resolveInfo ->
                    activity.grantUriPermission(
                        resolveInfo.activityInfo.packageName,
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                }
                activity.startActivity(installIntent)
                true
            }
        }.getOrElse { exception ->
            logger.error(TAG, exception) { "Unable to open the system package installer" }
            false
        }
    }

    private fun Activity.startIfResolvable(intent: Intent): Boolean {
        return if (intent.isResolvable(this)) {
            startActivity(intent)
            true
        } else {
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun Intent.isResolvable(activity: Activity): Boolean {
        return resolveActivity(activity.packageManager) != null
    }

    private companion object {
        const val TAG = "KemiAppUpdateInstaller"
        const val STORE_PACKAGE_NAME = "com.newlink.featuredapps"
        const val PACKAGE_INSTALLER_PACKAGE = "com.android.packageinstaller"
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    }
}
