package app.k9mail.feature.account.setup.ui.credential

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.k9mail.core.ui.compose.designsystem.atom.button.ButtonOutlined
import app.k9mail.core.ui.compose.designsystem.atom.image.QrCodeImage
import app.k9mail.core.ui.compose.designsystem.atom.text.TextBodyMedium
import app.k9mail.core.ui.compose.designsystem.atom.text.TextBodySmall
import app.k9mail.core.ui.compose.designsystem.atom.text.TextTitleMedium
import app.k9mail.core.ui.compose.designsystem.molecule.ErrorView
import app.k9mail.core.ui.compose.designsystem.molecule.LoadingView
import app.k9mail.feature.account.setup.R
import app.k9mail.feature.account.setup.ui.credential.RemoteCredentialTransferContract.Effect
import app.k9mail.feature.account.setup.ui.credential.RemoteCredentialTransferContract.Event
import net.thunderbird.core.ui.compose.theme2.MainTheme
import net.thunderbird.core.ui.contract.mvi.observe

@Composable
internal fun RemoteCredentialTransferView(
    emailAddress: String,
    onCredentialReceived: (String) -> Unit,
    onCanceled: () -> Unit,
    viewModel: RemoteCredentialTransferContract.ViewModel,
    modifier: Modifier = Modifier,
) {
    val (state, dispatch) = viewModel.observe { effect ->
        when (effect) {
            is Effect.CredentialReceived -> onCredentialReceived(effect.credential)
            Effect.Canceled -> onCanceled()
        }
    }

    LaunchedEffect(emailAddress) {
        dispatch(Event.Start(emailAddress))
    }
    val uiState = state.value

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MainTheme.spacings.double),
    ) {
        when {
            uiState.isLoading -> LoadingView(
                message = stringResource(R.string.account_setup_remote_credential_creating),
            )

            uiState.error != null -> ErrorView(
                title = stringResource(R.string.account_setup_remote_credential_error_title),
                message = uiState.error.toMessage(),
                onRetry = { dispatch(Event.Retry) },
            )

            uiState.qrCodeUrl != null -> {
                TextTitleMedium(text = stringResource(R.string.account_setup_remote_credential_title))
                TextBodySmall(
                    text = stringResource(R.string.account_setup_remote_credential_description),
                    textAlign = TextAlign.Center,
                )
                QrCodeImage(
                    data = uiState.qrCodeUrl,
                    contentDescription = stringResource(R.string.account_setup_remote_credential_qr_description),
                    modifier = Modifier.size(220.dp),
                )
                if (uiState.verificationCode.isNotEmpty()) {
                    TextBodyMedium(
                        text = stringResource(
                            R.string.account_setup_remote_credential_verification_code,
                            uiState.verificationCode,
                        ),
                    )
                }
                TextBodySmall(
                    text = stringResource(
                        R.string.account_setup_remote_credential_expires,
                        uiState.remainingSeconds,
                    ),
                    textAlign = TextAlign.Center,
                )
            }
        }

        ButtonOutlined(
            text = stringResource(R.string.account_setup_remote_credential_cancel),
            onClick = { dispatch(Event.Cancel) },
        )
    }
}

@Composable
private fun RemoteCredentialTransferContract.Error.toMessage(): String {
    return when (this) {
        RemoteCredentialTransferContract.Error.Unavailable ->
            stringResource(R.string.account_setup_remote_credential_error_unavailable)
        RemoteCredentialTransferContract.Error.Expired ->
            stringResource(R.string.account_setup_remote_credential_error_expired)
        RemoteCredentialTransferContract.Error.InvalidPayload ->
            stringResource(R.string.account_setup_remote_credential_error_invalid)
        RemoteCredentialTransferContract.Error.Network ->
            stringResource(R.string.account_setup_remote_credential_error_network)
    }
}
