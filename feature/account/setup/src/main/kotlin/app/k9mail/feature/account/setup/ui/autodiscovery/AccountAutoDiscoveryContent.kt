package app.k9mail.feature.account.setup.ui.autodiscovery

import android.content.res.Resources
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import app.k9mail.core.ui.compose.designsystem.atom.button.ButtonOutlined
import app.k9mail.core.ui.compose.designsystem.atom.button.ButtonText
import app.k9mail.core.ui.compose.designsystem.atom.text.TextBodySmall
import app.k9mail.core.ui.compose.designsystem.molecule.ContentLoadingErrorView
import app.k9mail.core.ui.compose.designsystem.molecule.ErrorView
import app.k9mail.core.ui.compose.designsystem.molecule.LoadingView
import app.k9mail.core.ui.compose.designsystem.molecule.input.EmailAddressInput
import app.k9mail.core.ui.compose.designsystem.molecule.input.PasswordInput
import app.k9mail.core.ui.compose.designsystem.organism.AlertDialog
import app.k9mail.core.ui.compose.designsystem.template.ResponsiveWidthContainer
import app.k9mail.feature.account.common.ui.AppTitleTopHeader
import app.k9mail.feature.account.common.ui.WizardNavigationBar
import app.k9mail.feature.account.common.ui.WizardNavigationBarState
import app.k9mail.feature.account.oauth.ui.AccountOAuthContract
import app.k9mail.feature.account.oauth.ui.AccountOAuthView
import app.k9mail.feature.account.setup.R
import app.k9mail.feature.account.setup.ui.autodiscovery.AccountAutoDiscoveryContract.Event
import app.k9mail.feature.account.setup.ui.autodiscovery.AccountAutoDiscoveryContract.State
import app.k9mail.feature.account.setup.ui.autodiscovery.view.AutoDiscoveryResultApprovalView
import app.k9mail.feature.account.setup.ui.autodiscovery.view.AutoDiscoveryResultView
import app.k9mail.feature.account.setup.ui.credential.RemoteCredentialTransferContract
import app.k9mail.feature.account.setup.ui.credential.RemoteCredentialTransferView
import net.thunderbird.core.ui.compose.theme2.MainTheme
import net.thunderbird.feature.thundermail.ui.component.ThundermailButtonPanel

@Composable
@Suppress("LongParameterList")
internal fun AccountAutoDiscoveryContent(
    state: State,
    onEvent: (Event) -> Unit,
    onThundermailClick: () -> Unit,
    onScanQrCodeClick: () -> Unit,
    oAuthViewModel: AccountOAuthContract.ViewModel,
    remoteCredentialTransferViewModel: RemoteCredentialTransferContract.ViewModel,
    brandName: String,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    ResponsiveWidthContainer(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
            .consumeWindowInsets(contentPadding)
            .imePadding()
            .testTag("AccountAutoDiscoveryContent"),
    ) { responsiveWidthPadding ->
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(responsiveWidthPadding),
            ) {
                AppTitleTopHeader(
                    title = brandName,
                )
                Spacer(modifier = Modifier.weight(1f))
                @Suppress("ViewModelForwarding")
                AutoDiscoveryContent(
                    state = state,
                    onEvent = onEvent,
                    onThundermailClick = onThundermailClick,
                    onScanQrCodeClick = onScanQrCodeClick,
                    oAuthViewModel = oAuthViewModel,
                    remoteCredentialTransferViewModel = remoteCredentialTransferViewModel,
                )
                Spacer(modifier = Modifier.weight(1f))
            }

            WizardNavigationBar(
                onNextClick = { onEvent(Event.OnNextClicked) },
                onBackClick = { onEvent(Event.OnBackClicked) },
                state = WizardNavigationBarState(showNext = state.isNextButtonVisible),
            )
        }
    }
}

@Composable
internal fun AutoDiscoveryContent(
    state: State,
    onEvent: (Event) -> Unit,
    onThundermailClick: () -> Unit,
    onScanQrCodeClick: () -> Unit,
    oAuthViewModel: AccountOAuthContract.ViewModel,
    remoteCredentialTransferViewModel: RemoteCredentialTransferContract.ViewModel,
    modifier: Modifier = Modifier,
) {
    val resources = LocalResources.current

    ContentLoadingErrorView(
        state = state,
        loading = {
            LoadingView(
                message = stringResource(id = R.string.account_setup_auto_discovery_loading_message),
                modifier = Modifier.fillMaxSize(),
            )
        },
        error = { error ->
            ErrorView(
                title = stringResource(id = R.string.account_setup_auto_discovery_loading_error),
                message = error.toAutoDiscoveryErrorString(resources),
                onRetry = { onEvent(Event.OnRetryClicked) },
                modifier = Modifier.fillMaxSize(),
            )
        },
        content = { contentState ->
            @Suppress("ViewModelForwarding")
            ContentView(
                state = contentState,
                onEvent = onEvent,
                onThundermailClick = onThundermailClick,
                onScanQrCodeClick = onScanQrCodeClick,
                oAuthViewModel = oAuthViewModel,
                remoteCredentialTransferViewModel = remoteCredentialTransferViewModel,
                resources = resources,
            )
        },
        modifier = Modifier
            .fillMaxSize()
            .then(modifier),
    )
}

@Composable
@Suppress("LongMethod", "ViewModelForwarding")
internal fun ContentView(
    state: State,
    onEvent: (Event) -> Unit,
    onThundermailClick: () -> Unit,
    onScanQrCodeClick: () -> Unit,
    oAuthViewModel: AccountOAuthContract.ViewModel,
    remoteCredentialTransferViewModel: RemoteCredentialTransferContract.ViewModel,
    resources: Resources,
    modifier: Modifier = Modifier,
) {
    var showAuthorizationCodeHelp by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(MainTheme.spacings.quadruple)
            .then(modifier),
    ) {
        if (state.configStep != AccountAutoDiscoveryContract.ConfigStep.EMAIL_ADDRESS) {
            AutoDiscoveryResultView(
                settings = state.autoDiscoverySettings,
                onEditConfigurationClick = { onEvent(Event.OnEditConfigurationClicked) },
            )
            if (state.autoDiscoverySettings != null && state.autoDiscoverySettings.isTrusted.not()) {
                AutoDiscoveryResultApprovalView(
                    approvalState = state.configurationApproved,
                    onApprovalChange = { onEvent(Event.ResultApprovalChanged(it)) },
                )
            }
            Spacer(modifier = Modifier.height(MainTheme.spacings.double))
        }

        AnimatedVisibility(state.emailAddress.value.isBlank()) {
            ThundermailButtonPanel(
                onThundermailClick = onThundermailClick,
                onScanQrCodeClick = onScanQrCodeClick,
                modifier = Modifier
                    .testTag("thundermail_panel")
                    .padding(bottom = MainTheme.spacings.quadruple),
            )
        }

        EmailAddressInput(
            emailAddress = state.emailAddress.value,
            errorMessage = state.emailAddress.error?.toAutoDiscoveryValidationErrorString(resources),
            onEmailAddressChange = { onEvent(Event.EmailAddressChanged(it)) },
            contentPadding = PaddingValues(),
            modifier = Modifier.testTag("account_setup_email_address_input"),
        )

        if (state.configStep == AccountAutoDiscoveryContract.ConfigStep.PASSWORD) {
            Spacer(modifier = Modifier.height(MainTheme.spacings.double))
            PasswordInput(
                password = state.password.value,
                label = stringResource(R.string.account_setup_authorization_code_input_label),
                errorMessage = state.password.error?.toAutoDiscoveryValidationErrorString(resources),
                onPasswordChange = { onEvent(Event.PasswordChanged(it)) },
                contentPadding = PaddingValues(),
                modifier = Modifier.testTag("account_setup_password_input"),
            )
            ButtonText(
                text = stringResource(R.string.account_setup_authorization_code_help_button),
                onClick = { showAuthorizationCodeHelp = true },
                modifier = Modifier
                    .align(Alignment.End)
                    .testTag("account_setup_authorization_code_help"),
            )
            if (remoteCredentialTransferViewModel.isAvailable) {
                Spacer(modifier = Modifier.height(MainTheme.spacings.double))
                ButtonOutlined(
                    text = stringResource(R.string.account_setup_remote_credential_button),
                    onClick = { onEvent(Event.OnRemoteCredentialTransferClicked) },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
                TextBodySmall(
                    text = stringResource(R.string.account_setup_remote_credential_button_description),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        } else if (state.configStep == AccountAutoDiscoveryContract.ConfigStep.OAUTH) {
            val isAutoDiscoverySettingsTrusted = state.autoDiscoverySettings?.isTrusted ?: false
            val isConfigurationApproved = state.configurationApproved.value ?: false
            Spacer(modifier = Modifier.height(MainTheme.spacings.double))
            AccountOAuthView(
                onOAuthResult = { result -> onEvent(Event.OnOAuthResult(result)) },
                viewModel = oAuthViewModel,
                isEnabled = isAutoDiscoverySettingsTrusted || isConfigurationApproved,
            )
            Spacer(modifier = Modifier.height(MainTheme.spacings.double))
            ButtonOutlined(
                text = stringResource(R.string.account_setup_use_password_button),
                onClick = { onEvent(Event.OnUsePasswordClicked) },
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        } else if (state.configStep == AccountAutoDiscoveryContract.ConfigStep.REMOTE_PASSWORD) {
            RemoteCredentialTransferView(
                emailAddress = state.emailAddress.value,
                onCredentialReceived = { onEvent(Event.OnRemoteCredentialReceived(it)) },
                onCanceled = { onEvent(Event.OnRemoteCredentialTransferCanceled) },
                viewModel = remoteCredentialTransferViewModel,
            )
        }
    }

    if (showAuthorizationCodeHelp) {
        AlertDialog(
            title = stringResource(R.string.account_setup_authorization_code_help_title),
            text = stringResource(R.string.account_setup_authorization_code_help_text),
            confirmText = stringResource(R.string.account_setup_authorization_code_help_confirm),
            onConfirmClick = { showAuthorizationCodeHelp = false },
            onDismissRequest = { showAuthorizationCodeHelp = false },
            modifier = Modifier.testTag("account_setup_authorization_code_help_dialog"),
        )
    }
}
