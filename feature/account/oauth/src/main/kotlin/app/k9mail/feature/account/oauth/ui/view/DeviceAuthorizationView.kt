package app.k9mail.feature.account.oauth.ui.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.k9mail.core.ui.compose.designsystem.atom.button.ButtonOutlined
import app.k9mail.core.ui.compose.designsystem.atom.image.QrCodeImage
import app.k9mail.core.ui.compose.designsystem.atom.text.TextBodySmall
import app.k9mail.core.ui.compose.designsystem.atom.text.TextHeadlineMedium
import app.k9mail.feature.account.oauth.R
import app.k9mail.feature.account.oauth.ui.AccountOAuthContract
import net.thunderbird.core.ui.compose.theme2.MainTheme

@Composable
internal fun DeviceAuthorizationView(
    state: AccountOAuthContract.DeviceAuthorization,
    onCancelClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MainTheme.spacings.double),
        modifier = modifier,
    ) {
        TextBodySmall(
            text = stringResource(R.string.account_oauth_device_instructions),
            textAlign = TextAlign.Center,
        )
        QrCodeImage(
            data = state.verificationUri,
            contentDescription = stringResource(R.string.account_oauth_device_qr_content_description),
            modifier = Modifier.size(DEVICE_QR_SIZE),
        )
        TextBodySmall(
            text = state.verificationUri,
            textAlign = TextAlign.Center,
        )
        TextBodySmall(text = stringResource(R.string.account_oauth_device_code_label))
        TextHeadlineMedium(text = state.userCode)
        TextBodySmall(
            text = stringResource(
                R.string.account_oauth_device_expiration,
                state.remainingSeconds / SECONDS_PER_MINUTE,
                state.remainingSeconds % SECONDS_PER_MINUTE,
            ),
        )
        ButtonOutlined(
            text = stringResource(R.string.account_oauth_device_cancel_button),
            onClick = onCancelClick,
        )
    }
}

private val DEVICE_QR_SIZE = 220.dp
private const val SECONDS_PER_MINUTE = 60
