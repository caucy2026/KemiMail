package app.k9mail.feature.onboarding.welcome.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.k9mail.core.ui.compose.common.window.isSpannedDualScreenCanvas
import app.k9mail.core.ui.compose.designsystem.atom.Surface
import app.k9mail.core.ui.compose.designsystem.atom.text.TextBodyLarge
import app.k9mail.core.ui.compose.designsystem.atom.text.TextDisplayMedium
import app.k9mail.core.ui.compose.designsystem.template.LazyColumnWithHeaderFooter
import app.k9mail.core.ui.compose.designsystem.template.ResponsiveContent
import app.k9mail.feature.onboarding.welcome.R
import net.thunderbird.core.ui.compose.theme2.MainTheme
import org.jetbrains.compose.resources.painterResource

private const val CIRCLE_COLOR = 0xFFEEEEEE
private const val CIRCLE_SIZE_DP = 200
private const val LOGO_SIZE_DP = 125

@Composable
internal fun WelcomeContent(
    onStartClick: () -> Unit,
    onImportClick: () -> Unit,
    appName: String,
    showImportButton: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
    ) {
        ResponsiveContent { contentPadding ->
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val viewportHeight = LocalConfiguration.current.screenHeightDp.dp

                if (isSpannedDualScreenCanvas(maxHeight, viewportHeight)) {
                    DualScreenWelcomeContent(
                        onStartClick = onStartClick,
                        onImportClick = onImportClick,
                        appName = appName,
                        showImportButton = showImportButton,
                        viewportHeight = viewportHeight,
                        contentPadding = contentPadding,
                    )
                } else {
                    SingleScreenWelcomeContent(
                        onStartClick = onStartClick,
                        onImportClick = onImportClick,
                        appName = appName,
                        showImportButton = showImportButton,
                        contentPadding = contentPadding,
                    )
                }
            }
        }
    }
}

@Composable
private fun SingleScreenWelcomeContent(
    onStartClick: () -> Unit,
    onImportClick: () -> Unit,
    appName: String,
    showImportButton: Boolean,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    LazyColumnWithHeaderFooter(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.SpaceEvenly,
        header = {
            WelcomeHeaderSection(title = appName)
        },
        footer = {
            WelcomeFooterSection(
                showImportButton = showImportButton,
                onStartClick = onStartClick,
                onImportClick = onImportClick,
            )
        },
        content = {
            item { WelcomeMessageItem() }
        },
    )
}

@Composable
private fun DualScreenWelcomeContent(
    onStartClick: () -> Unit,
    onImportClick: () -> Unit,
    appName: String,
    showImportButton: Boolean,
    viewportHeight: Dp,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(viewportHeight)
                .padding(contentPadding),
            contentAlignment = Alignment.Center,
        ) {
            WelcomeHeaderSection(
                title = appName,
                includeMessage = true,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(viewportHeight)
                .padding(contentPadding),
        ) {
            WelcomeActions(
                showImportButton = showImportButton,
                onStartClick = onStartClick,
                onImportClick = onImportClick,
                modifier = Modifier.align(Alignment.Center),
            )
            WelcomeDevelopedBy(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        horizontal = MainTheme.spacings.double,
                        vertical = MainTheme.spacings.double,
                    ),
            )
        }
    }
}

@Composable
private fun WelcomeHeaderSection(
    title: String,
    modifier: Modifier = Modifier,
    includeMessage: Boolean = false,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .defaultItemModifier()
            .padding(top = if (includeMessage) 0.dp else MainTheme.spacings.quadruple),
        verticalArrangement = Arrangement.spacedBy(MainTheme.spacings.double),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        WelcomeLogo()
        WelcomeTitleItem(title = title)
        if (includeMessage) {
            WelcomeMessageItem()
        }
    }
}

@Composable
private fun WelcomeLogo(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(Color(CIRCLE_COLOR))
                .size(CIRCLE_SIZE_DP.dp),
        ) {
            Image(
                painter = painterResource(MainTheme.images.logo),
                contentDescription = null,
                modifier = Modifier
                    .size(LOGO_SIZE_DP.dp)
                    .align(Alignment.Center),
            )
        }
    }
}

@Composable
private fun WelcomeTitleItem(
    title: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
    ) {
        WelcomeTitle(
            title = title,
            modifier = Modifier.defaultItemModifier(),
        )
    }
}

@Composable
private fun WelcomeTitle(
    title: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = MainTheme.spacings.quadruple),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TextDisplayMedium(
            text = title,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun WelcomeMessageItem(
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
    ) {
        WelcomeMessage(
            modifier = Modifier.defaultItemModifier(),
        )
    }
}

@Composable
private fun WelcomeMessage(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = Modifier
            .padding(horizontal = MainTheme.spacings.quadruple)
            .then(modifier),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TextBodyLarge(
            text = stringResource(id = R.string.onboarding_welcome_text),
            textAlign = TextAlign.Center,
        )
    }
}

private fun Modifier.defaultItemModifier() = composed {
    fillMaxWidth()
        .padding(MainTheme.spacings.default)
}
