package app.k9mail.update

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.fsck.k9.ui.base.BaseActivity
import net.thunderbird.core.ui.contract.mvi.observe
import net.thunderbird.core.ui.theme.api.FeatureThemeProvider
import org.koin.android.ext.android.inject

internal class KemiAppUpdateActivity : BaseActivity() {
    private val repository: KemiAppUpdateRepository by inject()
    private val installer: KemiAppUpdateInstaller by inject()
    private val themeProvider: FeatureThemeProvider by inject()
    private val updateInfo: KemiAppUpdateInfo by lazy { requireNotNull(intent.toUpdateInfo()) }
    private val viewModel: KemiAppUpdateViewModel by viewModels {
        KemiAppUpdateViewModel.Factory(updateInfo, repository)
    }
    private val unknownSourcesLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        viewModel.event(
            KemiAppUpdateEvent.InstallPermissionResult(installer.canRequestPackageInstalls(this)),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(updateInfo.forceUpdate) {
                override fun handleOnBackPressed() = Unit
            },
        )

        setContent {
            themeProvider.WithTheme {
                val (state, dispatch) = viewModel.observe(::handleEffect)
                KemiAppUpdateScreen(
                    updateInfo = updateInfo,
                    state = state.value,
                    onEvent = dispatch,
                )
            }
        }
    }

    private fun handleEffect(effect: KemiAppUpdateEffect) {
        when (effect) {
            is KemiAppUpdateEffect.OpenStore -> {
                viewModel.event(
                    if (installer.openStore(this, effect.deepLink)) {
                        KemiAppUpdateEvent.StoreOpened
                    } else {
                        KemiAppUpdateEvent.StoreUnavailable
                    },
                )
            }
            is KemiAppUpdateEffect.PrepareInstall -> prepareInstall()
            is KemiAppUpdateEffect.Install -> {
                if (!installer.install(this, effect.file)) {
                    viewModel.event(KemiAppUpdateEvent.InstallationFailed)
                }
            }
            KemiAppUpdateEffect.Finish -> finish()
        }
    }

    private fun prepareInstall() {
        if (installer.canRequestPackageInstalls(this)) {
            viewModel.event(KemiAppUpdateEvent.InstallPermissionResult(granted = true))
        } else {
            val intent = installer.createUnknownSourcesIntent(this)
            if (intent != null) {
                unknownSourcesLauncher.launch(intent)
            } else {
                viewModel.event(KemiAppUpdateEvent.InstallPermissionResult(granted = false))
            }
        }
    }

    companion object {
        private const val EXTRA_PACKAGE_NAME = "package_name"
        private const val EXTRA_VERSION_NAME = "version_name"
        private const val EXTRA_VERSION_CODE = "version_code"
        private const val EXTRA_APK_URL = "apk_url"
        private const val EXTRA_APK_SHA256 = "apk_sha256"
        private const val EXTRA_FILE_SIZE = "file_size"
        private const val EXTRA_FORCE_UPDATE = "force_update"
        private const val EXTRA_DEEP_LINK = "deep_link"
        private const val EXTRA_RELEASE_NOTES = "release_notes"

        fun createIntent(context: Context, updateInfo: KemiAppUpdateInfo): Intent {
            return Intent(context, KemiAppUpdateActivity::class.java).apply {
                putExtra(EXTRA_PACKAGE_NAME, updateInfo.packageName)
                putExtra(EXTRA_VERSION_NAME, updateInfo.versionName)
                putExtra(EXTRA_VERSION_CODE, updateInfo.versionCode)
                putExtra(EXTRA_APK_URL, updateInfo.apkUrl)
                putExtra(EXTRA_APK_SHA256, updateInfo.apkSha256)
                putExtra(EXTRA_FILE_SIZE, updateInfo.fileSizeBytes ?: -1L)
                putExtra(EXTRA_FORCE_UPDATE, updateInfo.forceUpdate)
                putExtra(EXTRA_DEEP_LINK, updateInfo.deepLink)
                putExtra(EXTRA_RELEASE_NOTES, updateInfo.releaseNotes)
            }
        }

        private fun Intent.toUpdateInfo(): KemiAppUpdateInfo? {
            val requiredValues = listOfNotNull(
                getStringExtra(EXTRA_PACKAGE_NAME),
                getStringExtra(EXTRA_VERSION_NAME),
                getStringExtra(EXTRA_APK_URL),
                getStringExtra(EXTRA_APK_SHA256),
            )
            val versionCode = getLongExtra(EXTRA_VERSION_CODE, -1L)
            return if (versionCode <= 0 || requiredValues.size != REQUIRED_VALUE_COUNT) {
                null
            } else {
                KemiAppUpdateInfo(
                    packageName = requiredValues[PACKAGE_NAME_INDEX],
                    versionName = requiredValues[VERSION_NAME_INDEX],
                    versionCode = versionCode,
                    apkUrl = requiredValues[APK_URL_INDEX],
                    apkSha256 = requiredValues[APK_SHA256_INDEX],
                    fileSizeBytes = getLongExtra(EXTRA_FILE_SIZE, -1L).takeIf { it > 0 },
                    forceUpdate = getBooleanExtra(EXTRA_FORCE_UPDATE, false),
                    deepLink = getStringExtra(EXTRA_DEEP_LINK),
                    releaseNotes = getStringExtra(EXTRA_RELEASE_NOTES).orEmpty(),
                )
            }
        }

        private const val REQUIRED_VALUE_COUNT = 4
        private const val PACKAGE_NAME_INDEX = 0
        private const val VERSION_NAME_INDEX = 1
        private const val APK_URL_INDEX = 2
        private const val APK_SHA256_INDEX = 3
    }
}
