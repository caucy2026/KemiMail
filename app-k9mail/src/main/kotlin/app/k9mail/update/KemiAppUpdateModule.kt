package app.k9mail.update

import android.app.Application
import com.fsck.k9.BuildConfig
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidApplication
import org.koin.core.qualifier.named
import org.koin.dsl.module

private val UPDATE_HTTP_CLIENT = named("KemiAppUpdateHttpClient")

internal val kemiAppUpdateModule = module {
    single(UPDATE_HTTP_CLIENT) {
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }
    single<KemiPackageMetadataReader> {
        AndroidKemiPackageMetadataReader(androidApplication())
    }
    single<KemiApkVerifier> {
        DefaultKemiApkVerifier(get())
    }
    single<KemiAppUpdateRepository> {
        DefaultKemiAppUpdateRepository(
            context = androidApplication(),
            httpClient = get(UPDATE_HTTP_CLIENT),
            apiBaseUrl = BuildConfig.KEMI_UPDATE_API_BASE_URL,
            apkVerifier = get(),
        )
    }
    single {
        KemiAppUpdateInstaller(logger = get())
    }
    single {
        KemiAppUpdateCoordinator(
            application = get<Application>(),
            repository = get(),
            logger = get(),
        )
    }
}

private const val CONNECT_TIMEOUT_SECONDS = 15L
private const val READ_TIMEOUT_SECONDS = 60L
