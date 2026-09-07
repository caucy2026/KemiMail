package app.k9mail.feature.account.oauth

import app.k9mail.feature.account.oauth.data.AuthorizationRepository
import app.k9mail.feature.account.oauth.data.AuthorizationStateRepository
import app.k9mail.feature.account.oauth.data.DeviceAuthorizationRepository
import app.k9mail.feature.account.oauth.domain.AccountOAuthDomainContract
import app.k9mail.feature.account.oauth.domain.AccountOAuthDomainContract.UseCase
import app.k9mail.feature.account.oauth.domain.usecase.CheckIsDeviceAuthorizationSupported
import app.k9mail.feature.account.oauth.domain.usecase.CheckIsGoogleSignIn
import app.k9mail.feature.account.oauth.domain.usecase.FinishOAuthSignIn
import app.k9mail.feature.account.oauth.domain.usecase.GetOAuthRequestIntent
import app.k9mail.feature.account.oauth.domain.usecase.PollDeviceAuthorization
import app.k9mail.feature.account.oauth.domain.usecase.StartDeviceAuthorization
import app.k9mail.feature.account.oauth.ui.AccountOAuthContract
import app.k9mail.feature.account.oauth.ui.AccountOAuthViewModel
import net.openid.appauth.AuthorizationService
import net.thunderbird.core.common.coreCommonModule
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidApplication
import org.koin.core.module.Module
import org.koin.dsl.module

val featureAccountOAuthModule: Module = module {
    includes(coreCommonModule)

    factory {
        AuthorizationService(
            androidApplication(),
        )
    }

    factory<AccountOAuthDomainContract.AuthorizationRepository> {
        AuthorizationRepository(
            service = get(),
        )
    }

    factory<AccountOAuthDomainContract.AuthorizationStateRepository> {
        AuthorizationStateRepository()
    }

    single<OkHttpClient> { OkHttpClient() }

    factory<AccountOAuthDomainContract.DeviceAuthorizationRepository> {
        DeviceAuthorizationRepository(httpClient = get())
    }

    factory<UseCase.GetOAuthRequestIntent> {
        GetOAuthRequestIntent(
            repository = get(),
            configurationProvider = get(),
        )
    }

    factory<UseCase.FinishOAuthSignIn> { FinishOAuthSignIn(repository = get()) }

    factory<UseCase.CheckIsGoogleSignIn> { CheckIsGoogleSignIn() }

    factory<UseCase.CheckIsDeviceAuthorizationSupported> {
        CheckIsDeviceAuthorizationSupported(configurationProvider = get())
    }

    factory<UseCase.StartDeviceAuthorization> {
        StartDeviceAuthorization(
            repository = get(),
            configurationProvider = get(),
        )
    }

    factory<UseCase.PollDeviceAuthorization> {
        PollDeviceAuthorization(repository = get())
    }

    factory<AccountOAuthContract.ViewModel> {
        AccountOAuthViewModel(
            getOAuthRequestIntent = get(),
            finishOAuthSignIn = get(),
            checkIsGoogleSignIn = get(),
            checkIsDeviceAuthorizationSupported = get(),
            startDeviceAuthorization = get(),
            pollDeviceAuthorization = get(),
        )
    }
}
