package app.k9mail.feature.onboarding.welcome.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.k9mail.core.ui.compose.designsystem.atom.button.ButtonFilled
import app.k9mail.core.ui.compose.designsystem.atom.button.ButtonText
import app.k9mail.core.ui.compose.designsystem.atom.text.TextBodySmall
import app.k9mail.feature.onboarding.welcome.R
import net.thunderbird.core.ui.compose.common.modifier.testTagAsResourceId
import net.thunderbird.core.ui.compose.theme2.MainTheme

@Composable
internal fun WelcomeFooterSection(
    showImportButton: Boolean,
    onStartClick: () -> Unit,
    onImportClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = MainTheme.spacings.quadruple),
    ) {
        WelcomeFooter(
            showImportButton = showImportButton,
            onStartClick = onStartClick,
            onImportClick = onImportClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = MainTheme.spacings.quadruple),
        )
    }
}

@Composable
private fun WelcomeFooter(
    showImportButton: Boolean,
    onStartClick: () -> Unit,
    onImportClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(bottom = MainTheme.spacings.double),
        verticalArrangement = Arrangement.spacedBy(MainTheme.spacings.quarter),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        WelcomeActions(
            showImportButton = showImportButton,
            onStartClick = onStartClick,
            onImportClick = onImportClick,
        )
        WelcomeDevelopedBy(
            modifier = Modifier
                .padding(top = MainTheme.spacings.quadruple)
                .padding(horizontal = MainTheme.spacings.double),
        )
    }
}

@Composable
internal fun WelcomeActions(
    showImportButton: Boolean,
    onStartClick: () -> Unit,
    onImportClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(MainTheme.spacings.quarter),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ButtonFilled(
            text = stringResource(id = R.string.onboarding_welcome_start_button),
            onClick = onStartClick,
            modifier = Modifier.testTagAsResourceId("onboarding_welcome_start_button"),
        )
        if (showImportButton) {
            ButtonText(
                text = stringResource(id = R.string.onboarding_welcome_import_button),
                onClick = onImportClick,
            )
        }
    }
}

@Composable
internal fun WelcomeDevelopedBy(
    modifier: Modifier = Modifier,
) {
    TextBodySmall(
        text = stringResource(R.string.onboarding_welcome_developed_by),
        modifier = modifier,
    )
}
