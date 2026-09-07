package app.k9mail.core.ui.compose.designsystem.atom.image

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlin.math.min

@Composable
fun QrCodeImage(
    data: String,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val matrix = remember(data) {
        QRCodeWriter().encode(
            data,
            BarcodeFormat.QR_CODE,
            QR_CODE_SIZE,
            QR_CODE_SIZE,
            mapOf(EncodeHintType.MARGIN to QUIET_ZONE_MODULES),
        )
    }
    val semanticsModifier = if (contentDescription == null) {
        Modifier
    } else {
        Modifier.semantics { this.contentDescription = contentDescription }
    }

    Canvas(
        modifier = modifier
            .aspectRatio(1f)
            .background(Color.White)
            .then(semanticsModifier),
    ) {
        drawRect(Color.White)
        val moduleSize = min(size.width / matrix.width, size.height / matrix.height)
        val horizontalOffset = (size.width - moduleSize * matrix.width) / 2f
        val verticalOffset = (size.height - moduleSize * matrix.height) / 2f

        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (matrix[x, y]) {
                    drawRect(
                        color = Color.Black,
                        topLeft = Offset(horizontalOffset + x * moduleSize, verticalOffset + y * moduleSize),
                        size = Size(moduleSize, moduleSize),
                    )
                }
            }
        }
    }
}

private const val QR_CODE_SIZE = 256
private const val QUIET_ZONE_MODULES = 2
