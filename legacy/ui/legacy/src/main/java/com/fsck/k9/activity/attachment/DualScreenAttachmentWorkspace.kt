package com.fsck.k9.activity.attachment

import android.graphics.drawable.Drawable
import android.net.Uri
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatImageView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.k9mail.core.ui.compose.designsystem.atom.CircularProgressIndicator
import app.k9mail.core.ui.compose.designsystem.atom.Surface
import app.k9mail.core.ui.compose.designsystem.atom.button.ButtonFilled
import app.k9mail.core.ui.compose.designsystem.atom.button.ButtonOutlined
import app.k9mail.core.ui.compose.designsystem.atom.button.ButtonText
import app.k9mail.core.ui.compose.designsystem.atom.text.TextBodyLarge
import app.k9mail.core.ui.compose.designsystem.atom.text.TextBodyMedium
import app.k9mail.core.ui.compose.designsystem.atom.text.TextHeadlineSmall
import app.k9mail.core.ui.compose.designsystem.atom.text.TextTitleLarge
import app.k9mail.core.ui.compose.designsystem.organism.TopAppBarWithBackButton
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.fsck.k9.ui.R
import net.thunderbird.core.ui.compose.designsystem.atom.icon.Icons
import net.thunderbird.core.ui.compose.theme2.MainTheme

internal const val ATTACHMENT_PREVIEW_TEST_TAG = "dual_screen_attachment_preview"
internal const val ATTACHMENT_ACTIONS_TEST_TAG = "dual_screen_attachment_actions"
internal const val ATTACHMENT_OPEN_TEST_TAG = "dual_screen_attachment_open"
internal const val ATTACHMENT_SAVE_TEST_TAG = "dual_screen_attachment_save"
internal const val ATTACHMENT_CLOSE_TEST_TAG = "dual_screen_attachment_close"
internal const val ATTACHMENT_CONTINUOUS_READING_TEST_TAG = "dual_screen_attachment_continuous_reading"
internal const val ATTACHMENT_DROP_TARGET_TEST_TAG = "dual_screen_attachment_drop_target"
internal const val ATTACHMENT_DROP_ACTION_TEST_TAG = "dual_screen_attachment_drop_action"
internal const val ATTACHMENT_DROP_CANCEL_TEST_TAG = "dual_screen_attachment_drop_cancel"

@Composable
internal fun DualScreenAttachmentPreview(
    attachmentUri: Uri,
    attachmentName: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize().testTag(ATTACHMENT_PREVIEW_TEST_TAG)) {
        Column {
            TopAppBarWithBackButton(
                title = attachmentName,
                onBackClick = onClose,
            )
            AttachmentImagePreview(
                attachmentUri = attachmentUri,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        }
    }
}

@Composable
internal fun DualScreenAttachmentActions(
    attachmentName: String,
    attachmentDetails: String,
    onOpenExternally: () -> Unit,
    onSave: () -> Unit,
    onReadContinuously: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize().testTag(ATTACHMENT_ACTIONS_TEST_TAG)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(MainTheme.spacings.triple),
            verticalArrangement = Arrangement.spacedBy(MainTheme.spacings.double),
        ) {
            TextHeadlineSmall(text = stringResource(R.string.dual_screen_attachment_workspace_title))
            TextTitleLarge(
                text = attachmentName,
                overflow = TextOverflow.Ellipsis,
                maxLines = 2,
            )
            TextBodyMedium(
                text = attachmentDetails,
                color = MainTheme.colors.onSurfaceVariant,
            )
            TextBodyLarge(text = stringResource(R.string.dual_screen_attachment_workspace_description))
            ButtonFilled(
                text = stringResource(R.string.dual_screen_attachment_open_external),
                icon = Icons.Outlined.OpenInNew,
                onClick = onOpenExternally,
                modifier = Modifier.fillMaxWidth().testTag(ATTACHMENT_OPEN_TEST_TAG),
            )
            ButtonOutlined(
                text = stringResource(R.string.save_attachment_action),
                icon = Icons.Outlined.Download,
                onClick = onSave,
                modifier = Modifier.fillMaxWidth().testTag(ATTACHMENT_SAVE_TEST_TAG),
            )
            ButtonOutlined(
                text = stringResource(R.string.dual_screen_attachment_continuous_open),
                icon = Icons.Outlined.Visibility,
                onClick = onReadContinuously,
                modifier = Modifier.fillMaxWidth().testTag(ATTACHMENT_CONTINUOUS_READING_TEST_TAG),
            )
            ButtonText(
                text = stringResource(R.string.dual_screen_attachment_close),
                onClick = onClose,
                modifier = Modifier.fillMaxWidth().testTag(ATTACHMENT_CLOSE_TEST_TAG),
            )
        }
    }
}

@Composable
internal fun DualScreenAttachmentDropTarget(
    attachmentName: String,
    onDrop: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag(ATTACHMENT_DROP_TARGET_TEST_TAG)
            .clickable(onClick = onDrop),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(MainTheme.spacings.triple),
            verticalArrangement = Arrangement.spacedBy(MainTheme.spacings.double),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TextHeadlineSmall(text = stringResource(R.string.dual_screen_attachment_drag_title))
            TextTitleLarge(
                text = attachmentName,
                overflow = TextOverflow.Ellipsis,
                maxLines = 2,
            )
            TextBodyLarge(text = stringResource(R.string.dual_screen_attachment_drag_description))
            TextBodyMedium(
                text = stringResource(R.string.dual_screen_attachment_drag_fallback),
                color = MainTheme.colors.onSurfaceVariant,
            )
            ButtonFilled(
                text = stringResource(R.string.dual_screen_attachment_drag_drop_action),
                icon = Icons.Outlined.Visibility,
                onClick = onDrop,
                modifier = Modifier.fillMaxWidth().testTag(ATTACHMENT_DROP_ACTION_TEST_TAG),
            )
            ButtonText(
                text = stringResource(R.string.dual_screen_attachment_drag_cancel),
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth().testTag(ATTACHMENT_DROP_CANCEL_TEST_TAG),
            )
        }
    }
}

@Composable
private fun AttachmentImagePreview(
    attachmentUri: Uri,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val imageView = remember(attachmentUri) {
        AppCompatImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
    }
    var loadState by remember(attachmentUri) { mutableStateOf(PreviewLoadState.LOADING) }
    val previewDescription = stringResource(R.string.dual_screen_attachment_preview_description)

    DisposableEffect(imageView, attachmentUri) {
        loadState = PreviewLoadState.LOADING
        Glide.with(imageView)
            .load(attachmentUri)
            .fitCenter()
            .diskCacheStrategy(DiskCacheStrategy.NONE)
            .listener(createPreviewListener { loadState = it })
            .into(imageView)
        onDispose { Glide.with(imageView).clear(imageView) }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { imageView },
            modifier = Modifier.fillMaxSize().semantics { contentDescription = previewDescription },
        )
        when (loadState) {
            PreviewLoadState.LOADING -> CircularProgressIndicator()
            PreviewLoadState.READY -> Unit
            PreviewLoadState.ERROR -> TextBodyLarge(
                text = stringResource(R.string.dual_screen_attachment_preview_error),
                modifier = Modifier.padding(24.dp),
            )
        }
    }
}

private fun createPreviewListener(onStateChanged: (PreviewLoadState) -> Unit): RequestListener<Drawable> {
    return object : RequestListener<Drawable> {
        override fun onLoadFailed(
            exception: GlideException?,
            model: Any?,
            target: Target<Drawable>,
            isFirstResource: Boolean,
        ): Boolean {
            onStateChanged(PreviewLoadState.ERROR)
            return false
        }

        override fun onResourceReady(
            resource: Drawable,
            model: Any,
            target: Target<Drawable>?,
            dataSource: DataSource,
            isFirstResource: Boolean,
        ): Boolean {
            onStateChanged(PreviewLoadState.READY)
            return false
        }
    }
}

private enum class PreviewLoadState {
    LOADING,
    READY,
    ERROR,
}
