@file:Suppress("CompositionLocalAllowlist")

package app.k9mail.core.ui.compose.designsystem.atom

import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.k9mail.core.ui.compose.common.window.isSpannedDualScreenCanvas
import kotlin.math.min
import kotlin.math.roundToInt
import androidx.compose.material3.Surface as Material3Surface

/**
 * Provides a same-canvas overlay for anchored menus on the automatic dual-screen projection.
 *
 * Material dropdowns use a separate Android window. That window is attached to the Activity display and therefore
 * appears on the lower display even when its anchor is rendered by the upper-display projection.
 */
@Composable
fun InCanvasDropdownMenuHost(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier = modifier) {
        val viewportHeight = LocalConfiguration.current.screenHeightDp.dp
        val sourceView = LocalView.current
        var sourceViewHeight by remember(sourceView) { mutableIntStateOf(sourceView.height) }
        val density = LocalDensity.current
        val sourceViewHeightDp = with(density) { sourceViewHeight.toDp() }
        val usesSpannedCanvas = isSpannedDualScreenCanvas(maxHeight, viewportHeight) ||
            isSpannedDualScreenCanvas(sourceViewHeightDp, viewportHeight)
        val hostState = remember { InCanvasDropdownMenuHostState() }

        DisposableEffect(sourceView) {
            val listener = View.OnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
                sourceViewHeight = view.height
            }
            sourceView.addOnLayoutChangeListener(listener)
            sourceViewHeight = sourceView.height
            onDispose { sourceView.removeOnLayoutChangeListener(listener) }
        }

        LaunchedEffect(usesSpannedCanvas) {
            if (!usesSpannedCanvas) hostState.dismiss()
        }

        CompositionLocalProvider(
            LocalInCanvasDropdownMenuHost provides hostState.takeIf { usesSpannedCanvas },
        ) {
            content()
        }

        hostState.entry?.takeIf { usesSpannedCanvas }?.let { entry ->
            BackHandler {
                hostState.dismiss(entry.owner)
                entry.onDismissRequest()
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(entry.owner) {
                        detectTapGestures {
                            hostState.dismiss(entry.owner)
                            entry.onDismissRequest()
                        }
                    },
            )

            InCanvasDropdownMenuLayout(
                anchorBounds = entry.anchorBounds,
                viewportHeight = constraints.maxHeight / DUAL_SCREEN_VIEWPORT_COUNT,
                content = entry.content,
            )
        }
    }
}

@Composable
internal fun inCanvasAwareDropdownMenuModifier(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
): Modifier {
    val hostState = LocalInCanvasDropdownMenuHost.current ?: return Modifier
    val owner = remember { Any() }
    var anchorBounds by remember { mutableStateOf(IntRect.Zero) }
    val currentContent = rememberUpdatedState(content)
    val currentDismissRequest = rememberUpdatedState(onDismissRequest)

    LaunchedEffect(hostState, expanded, anchorBounds) {
        if (expanded && anchorBounds.width > 0 && anchorBounds.height > 0) {
            hostState.show(
                owner = owner,
                anchorBounds = anchorBounds,
                onDismissRequest = { currentDismissRequest.value() },
                content = {
                    Material3Surface(
                        modifier = modifier,
                        shape = MaterialTheme.shapes.extraSmall,
                        tonalElevation = DROPDOWN_MENU_ELEVATION,
                        shadowElevation = DROPDOWN_MENU_ELEVATION,
                    ) {
                        Column(
                            modifier = Modifier
                                .widthIn(min = DROPDOWN_MENU_MIN_WIDTH, max = DROPDOWN_MENU_MAX_WIDTH)
                                .padding(vertical = DROPDOWN_MENU_VERTICAL_PADDING),
                        ) {
                            currentContent.value()
                        }
                    }
                },
            )
        } else {
            hostState.dismiss(owner)
        }
    }

    DisposableEffect(hostState, owner) {
        onDispose { hostState.dismiss(owner) }
    }

    return Modifier.onGloballyPositioned { coordinates ->
        anchorBounds = coordinates.boundsInRoot().toIntRect()
    }
}

private class InCanvasDropdownMenuHostState {
    var entry by mutableStateOf<InCanvasDropdownMenuEntry?>(null)
        private set

    fun show(
        owner: Any,
        anchorBounds: IntRect,
        onDismissRequest: () -> Unit,
        content: @Composable () -> Unit,
    ) {
        entry = InCanvasDropdownMenuEntry(owner, anchorBounds, onDismissRequest, content)
    }

    fun dismiss(owner: Any? = null) {
        if (owner == null || entry?.owner === owner) entry = null
    }
}

private class InCanvasDropdownMenuEntry(
    val owner: Any,
    val anchorBounds: IntRect,
    val onDismissRequest: () -> Unit,
    val content: @Composable () -> Unit,
)

private val LocalInCanvasDropdownMenuHost = compositionLocalOf<InCanvasDropdownMenuHostState?> { null }

@Composable
private fun InCanvasDropdownMenuLayout(
    anchorBounds: IntRect,
    viewportHeight: Int,
    content: @Composable () -> Unit,
) {
    Layout(
        content = content,
        modifier = Modifier.fillMaxSize(),
    ) { measurables, constraints ->
        val popup = measurables.single().measure(
            constraints.copy(
                minWidth = 0,
                minHeight = 0,
                maxHeight = min(viewportHeight, constraints.maxHeight),
            ),
        )
        val position = calculateInCanvasDropdownPosition(
            anchorBounds = anchorBounds,
            popupSize = IntSize(popup.width, popup.height),
            rootSize = IntSize(constraints.maxWidth, constraints.maxHeight),
            viewportHeight = viewportHeight,
        )

        layout(constraints.maxWidth, constraints.maxHeight) {
            popup.place(position)
        }
    }
}

internal fun calculateInCanvasDropdownPosition(
    anchorBounds: IntRect,
    popupSize: IntSize,
    rootSize: IntSize,
    viewportHeight: Int,
): IntOffset {
    require(popupSize.width >= 0 && popupSize.height >= 0)
    require(rootSize.width > 0 && rootSize.height > 0 && viewportHeight > 0)

    val viewportIndex = (anchorBounds.center.y / viewportHeight)
        .coerceIn(0, (rootSize.height - 1) / viewportHeight)
    val viewportTop = viewportIndex * viewportHeight
    val viewportBottom = min(viewportTop + viewportHeight, rootSize.height)
    val maxX = (rootSize.width - popupSize.width).coerceAtLeast(0)
    val x = (anchorBounds.right - popupSize.width).coerceIn(0, maxX)
    val y = when {
        anchorBounds.bottom + popupSize.height <= viewportBottom -> anchorBounds.bottom
        anchorBounds.top - popupSize.height >= viewportTop -> anchorBounds.top - popupSize.height
        else -> viewportTop
    }

    return IntOffset(x, y)
}

private fun Rect.toIntRect(): IntRect {
    return IntRect(
        left = left.roundToInt(),
        top = top.roundToInt(),
        right = right.roundToInt(),
        bottom = bottom.roundToInt(),
    )
}

private const val DUAL_SCREEN_VIEWPORT_COUNT = 2
private val DROPDOWN_MENU_MIN_WIDTH = 112.dp
private val DROPDOWN_MENU_MAX_WIDTH = 280.dp
private val DROPDOWN_MENU_VERTICAL_PADDING = 8.dp
private val DROPDOWN_MENU_ELEVATION = 3.dp
