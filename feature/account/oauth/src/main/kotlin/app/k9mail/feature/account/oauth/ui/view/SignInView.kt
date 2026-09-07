package app.k9mail.feature.account.oauth.ui.view

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import app.k9mail.core.ui.compose.designsystem.atom.button.ButtonFilled
import app.k9mail.core.ui.compose.designsystem.atom.button.ButtonOutlined
import app.k9mail.core.ui.compose.designsystem.atom.text.TextBodySmall
import app.k9mail.feature.account.oauth.R
import net.thunderbird.core.ui.compose.theme2.MainTheme

@Composable
internal fun SignInView(
    onSignInClick: () -> Unit,
    isGoogleSignIn: Boolean,
    modifier: Modifier = Modifier,
    onDeviceSignInClick: () -> Unit = {},
    isDeviceAuthorizationSupported: Boolean = false,
    isEnabled: Boolean = true,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MainTheme.spacings.double),
        modifier = modifier,
    ) {
        TextBodySmall(
            text = stringResource(
                id = if (isDeviceAuthorizationSupported) {
                    R.string.account_oauth_device_choice_description
                } else {
                    R.string.account_oauth_sign_in_description
                },
            ),
            textAlign = TextAlign.Center,
        )

        if (isDeviceAuthorizationSupported) {
            ButtonFilled(
                text = stringResource(R.string.account_oauth_device_sign_in_button),
                onClick = onDeviceSignInClick,
                enabled = isEnabled,
            )
            ButtonOutlined(
                text = stringResource(R.string.account_oauth_local_sign_in_button),
                onClick = onSignInClick,
                enabled = isEnabled,
            )
        } else if (isGoogleSignIn) {
            SignInWithGoogleButton(
                onClick = onSignInClick,
                enabled = isEnabled,
            )

            GoogleSignInSupportText()
        } else {
            ButtonFilled(
                text = stringResource(id = R.string.account_oauth_sign_in_button),
                onClick = onSignInClick,
                enabled = isEnabled,
            )
        }
    }
}
