package com.fsck.k9.activity.attachment

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.k9mail.core.ui.compose.designsystem.atom.CircularProgressIndicator
import app.k9mail.core.ui.compose.designsystem.atom.Surface
import app.k9mail.core.ui.compose.designsystem.atom.button.ButtonOutlined
import app.k9mail.core.ui.compose.designsystem.atom.button.ButtonText
import app.k9mail.core.ui.compose.designsystem.atom.text.TextBodyLarge
import app.k9mail.core.ui.compose.designsystem.atom.text.TextBodyMedium
import app.k9mail.core.ui.compose.designsystem.organism.TopAppBarWithBackButton
import com.fsck.k9.mailstore.AttachmentViewInfo
import com.fsck.k9.ui.R
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.thunderbird.core.ui.compose.theme2.MainTheme

internal const val CONTINUOUS_READER_TEST_TAG = "dual_screen_continuous_reader"
internal const val CONTINUOUS_READER_LOADING_TEST_TAG = "dual_screen_continuous_reader_loading"
internal const val CONTINUOUS_READER_ERROR_TEST_TAG = "dual_screen_continuous_reader_error"
internal const val CONTINUOUS_READER_OPEN_TEST_TAG = "dual_screen_continuous_reader_open"
internal const val CONTINUOUS_READER_SAVE_TEST_TAG = "dual_screen_continuous_reader_save"
internal const val CONTINUOUS_READER_ZOOM_OUT_TEST_TAG = "dual_screen_continuous_reader_zoom_out"
internal const val CONTINUOUS_READER_ZOOM_RESET_TEST_TAG = "dual_screen_continuous_reader_zoom_reset"
internal const val CONTINUOUS_READER_ZOOM_IN_TEST_TAG = "dual_screen_continuous_reader_zoom_in"

private const val MIN_ZOOM = 1f
private const val MIDDLE_ZOOM = 1.5f
private const val MAX_ZOOM = 2f
private const val PERCENT_MULTIPLIER = 100
private val zoomLevels = listOf(MIN_ZOOM, MIDDLE_ZOOM, MAX_ZOOM)

@Composable
internal fun DualScreenContinuousReader(
    attachment: AttachmentViewInfo,
    onOpenExternally: () -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val sessionFactory = remember(context) {
        DualScreenContinuousReaderSessionFactory(context)
    }
    val loadState by produceState<ContinuousReaderLoadState>(
        initialValue = ContinuousReaderLoadState.Loading,
        attachment.internalUri,
        attachment.size,
        attachment.mimeType,
    ) {
        var session: ContinuousReaderSession? = null
        try {
            when (val openResult = withContext(Dispatchers.IO) { sessionFactory.open(attachment) }) {
                ContinuousReaderOpenResult.Error -> value = ContinuousReaderLoadState.Error
                is ContinuousReaderOpenResult.Success -> {
                    session = openResult.session
                    value = ContinuousReaderLoadState.Ready(openResult.session)
                    awaitDispose { }
                }
            }
        } finally {
            session?.close()
        }
    }
    var zoom by rememberSaveable(attachment.internalUri.toString()) { mutableFloatStateOf(zoomLevels.first()) }

    Surface(modifier = modifier.fillMaxSize().testTag(CONTINUOUS_READER_TEST_TAG)) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBarWithBackButton(
                title = attachment.displayName,
                onBackClick = onClose,
            )
            ContinuousReaderBody(
                loadState = loadState,
                zoom = zoom,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            ContinuousReaderControls(
                zoom = zoom,
                onZoomOut = { zoom = previousZoom(zoom) },
                onZoomReset = { zoom = zoomLevels.first() },
                onZoomIn = { zoom = nextZoom(zoom) },
                onOpenExternally = onOpenExternally,
                onSave = onSave,
            )
        }
    }
}

@Composable
private fun ContinuousReaderBody(
    loadState: ContinuousReaderLoadState,
    zoom: Float,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        when (loadState) {
            ContinuousReaderLoadState.Loading -> {
                CircularProgressIndicator(modifier = Modifier.testTag(CONTINUOUS_READER_LOADING_TEST_TAG))
            }
            ContinuousReaderLoadState.Error -> {
                TextBodyLarge(
                    text = stringResource(R.string.dual_screen_continuous_reader_error),
                    modifier = Modifier.padding(MainTheme.spacings.triple).testTag(CONTINUOUS_READER_ERROR_TEST_TAG),
                )
            }
            is ContinuousReaderLoadState.Ready -> when (val session = loadState.session) {
                is ImageReaderSession -> ContinuousImageContent(bitmap = session.bitmap, zoom = zoom)
                is PdfReaderSession -> ContinuousPdfContent(session = session, zoom = zoom)
            }
        }
    }
}

@Composable
private fun ContinuousImageContent(
    bitmap: Bitmap,
    zoom: Float,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.dual_screen_continuous_reader_image_description)
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val scaledWidth = maxWidth * zoom
        Box(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(rememberScrollState())
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = description,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .width(scaledWidth)
                    .aspectRatio(bitmap.width.toFloat() / bitmap.height.toFloat()),
            )
        }
    }
}

@Composable
private fun ContinuousPdfContent(
    session: PdfReaderSession,
    zoom: Float,
    modifier: Modifier = Modifier,
) {
    val description = stringResource(R.string.dual_screen_continuous_reader_pdf_description)
    BoxWithConstraints(modifier = modifier.fillMaxSize().semantics { contentDescription = description }) {
        val scaledWidth = maxWidth * zoom
        Box(modifier = Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
            LazyColumn(
                modifier = Modifier.width(scaledWidth).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(MainTheme.spacings.double),
            ) {
                itemsIndexed(session.pageRenderSizes) { index, renderSize ->
                    PdfPage(
                        session = session,
                        pageIndex = index,
                        pageCount = session.pageRenderSizes.size,
                        renderSize = renderSize,
                    )
                }
            }
        }
    }
}

@Composable
private fun PdfPage(
    session: PdfReaderSession,
    pageIndex: Int,
    pageCount: Int,
    renderSize: RenderSize,
) {
    val pageState by produceState<PdfPageState>(PdfPageState.Loading, session, pageIndex) {
        var bitmap: Bitmap? = null
        try {
            when (val renderResult = withContext(Dispatchers.IO) { session.renderPage(pageIndex) }) {
                PdfPageRenderResult.Error -> value = PdfPageState.Error
                is PdfPageRenderResult.Success -> {
                    bitmap = renderResult.bitmap
                    value = PdfPageState.Ready(renderResult.bitmap)
                    awaitDispose { }
                }
            }
        } finally {
            bitmap?.takeUnless { it.isRecycled }?.recycle()
        }
    }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = MainTheme.spacings.double),
        verticalArrangement = Arrangement.spacedBy(MainTheme.spacings.default),
    ) {
        TextBodyMedium(
            text = stringResource(R.string.dual_screen_continuous_reader_page, pageIndex + 1, pageCount),
            color = MainTheme.colors.onSurfaceVariant,
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(renderSize.width.toFloat() / renderSize.height.toFloat()),
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                when (val state = pageState) {
                    PdfPageState.Loading -> CircularProgressIndicator()
                    PdfPageState.Error -> TextBodyMedium(
                        text = stringResource(R.string.dual_screen_continuous_reader_error),
                        modifier = Modifier.padding(24.dp),
                    )
                    is PdfPageState.Ready -> Image(
                        bitmap = state.bitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
internal fun ContinuousReaderControls(
    zoom: Float,
    onZoomOut: () -> Unit,
    onZoomReset: () -> Unit,
    onZoomIn: () -> Unit,
    onOpenExternally: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(MainTheme.spacings.default),
        horizontalArrangement = Arrangement.spacedBy(MainTheme.spacings.default),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ButtonText(
            text = stringResource(R.string.dual_screen_continuous_reader_zoom_out),
            onClick = onZoomOut,
            enabled = zoom > zoomLevels.first(),
            modifier = Modifier.testTag(CONTINUOUS_READER_ZOOM_OUT_TEST_TAG),
        )
        ButtonText(
            text = stringResource(
                R.string.dual_screen_continuous_reader_zoom_reset,
                (zoom * PERCENT_MULTIPLIER).roundToInt(),
            ),
            onClick = onZoomReset,
            modifier = Modifier.testTag(CONTINUOUS_READER_ZOOM_RESET_TEST_TAG),
        )
        ButtonText(
            text = stringResource(R.string.dual_screen_continuous_reader_zoom_in),
            onClick = onZoomIn,
            enabled = zoom < zoomLevels.last(),
            modifier = Modifier.testTag(CONTINUOUS_READER_ZOOM_IN_TEST_TAG),
        )
        ButtonOutlined(
            text = stringResource(R.string.dual_screen_attachment_open_external),
            onClick = onOpenExternally,
            modifier = Modifier.testTag(CONTINUOUS_READER_OPEN_TEST_TAG),
        )
        ButtonOutlined(
            text = stringResource(R.string.save_attachment_action),
            onClick = onSave,
            modifier = Modifier.testTag(CONTINUOUS_READER_SAVE_TEST_TAG),
        )
    }
}

private fun previousZoom(currentZoom: Float): Float = zoomLevels.lastOrNull { it < currentZoom } ?: zoomLevels.first()

private fun nextZoom(currentZoom: Float): Float = zoomLevels.firstOrNull { it > currentZoom } ?: zoomLevels.last()

private sealed interface ContinuousReaderLoadState {
    data object Loading : ContinuousReaderLoadState
    data object Error : ContinuousReaderLoadState
    data class Ready(val session: ContinuousReaderSession) : ContinuousReaderLoadState
}

private sealed interface PdfPageState {
    data object Loading : PdfPageState
    data object Error : PdfPageState
    data class Ready(val bitmap: Bitmap) : PdfPageState
}
