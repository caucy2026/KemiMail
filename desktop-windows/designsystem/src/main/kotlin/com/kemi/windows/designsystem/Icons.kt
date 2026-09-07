package com.kemi.windows.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale

enum class MailIcon { Mail, Inbox, Send, Star, Trash, Edit, Search, Refresh, Settings, Plus, Reply, Attach, Folder, Lock, Check }

/** Resolution-independent outline icons, drawn with a consistent 1.6 point stroke. */
@Composable fun Glyph(icon: MailIcon,modifier: Modifier,color: Color = MailColors.secondary) {
    Canvas(modifier) {
        scale(size.width/24f,size.height/24f,pivot = Offset.Zero) {
            val stroke = Stroke(1.6f,cap = StrokeCap.Round)
            fun line(a: Float,b: Float,c: Float,d: Float) = drawLine(color,Offset(a,b),Offset(c,d),strokeWidth = 1.6f,cap = StrokeCap.Round)
            fun path(vararg points: Float,close: Boolean = false) {
                val p = Path().apply { moveTo(points[0],points[1]); for (i in 2 until points.size step 2) lineTo(points[i],points[i+1]); if (close) close() }
                drawPath(p,color,style = stroke)
            }
            when (icon) {
                MailIcon.Mail -> { drawRoundRect(color,Offset(3f,5f),Size(18f,14f),CornerRadius(2f),style = stroke); path(3f,6f,12f,13f,21f,6f) }
                MailIcon.Inbox -> { path(5f,4f,19f,4f,22f,15f,22f,20f,2f,20f,2f,15f,5f,4f); path(2f,14f,8f,14f,10f,17f,14f,17f,16f,14f,22f,14f) }
                MailIcon.Send -> { path(3f,11f,21f,3f,15f,21f,11f,14f,3f,11f); line(11f,14f,21f,3f) }
                MailIcon.Star -> path(12f,3f,15f,9f,22f,10f,17f,15f,18f,22f,12f,18f,6f,22f,7f,15f,2f,10f,9f,9f,12f,3f)
                MailIcon.Trash -> { path(5f,7f,6f,21f,18f,21f,19f,7f); line(3f,6f,21f,6f); path(9f,6f,9f,3f,15f,3f,15f,6f); line(10f,10f,10f,17f); line(14f,10f,14f,17f) }
                MailIcon.Edit -> { path(12f,4f,4f,4f,4f,21f,20f,21f,20f,13f); path(10f,14f,11f,10f,19f,2f,22f,5f,14f,13f,10f,14f); line(17f,4f,20f,7f) }
                MailIcon.Search -> { drawCircle(color,6.5f,Offset(10f,10f),style = stroke); line(15f,15f,21f,21f) }
                MailIcon.Refresh -> { drawArc(color,45f,290f,false,Offset(4f,4f),Size(16f,16f),style = stroke); path(20f,3f,20f,9f,14f,9f) }
                MailIcon.Settings -> { drawCircle(color,7f,Offset(12f,12f),style = stroke); drawCircle(color,2.5f,Offset(12f,12f),style = stroke)
                    line(12f,2f,12f,5f); line(12f,19f,12f,22f); line(2f,12f,5f,12f); line(19f,12f,22f,12f)
                    line(5f,5f,7f,7f); line(17f,17f,19f,19f); line(5f,19f,7f,17f); line(17f,7f,19f,5f) }
                MailIcon.Plus -> { line(12f,5f,12f,19f); line(5f,12f,19f,12f) }
                MailIcon.Reply -> { path(9f,5f,3f,11f,9f,17f); drawPath(Path().apply { moveTo(3f,11f); cubicTo(18f,8f,20f,11f,21f,20f) },color,style = stroke) }
                MailIcon.Attach -> drawPath(Path().apply { moveTo(8f,12f); lineTo(15f,5f); cubicTo(19f,1f,24f,6f,20f,10f); lineTo(10f,20f)
                    cubicTo(4f,26f,-2f,18f,4f,13f); lineTo(14f,3f); cubicTo(16f,1f,19f,4f,17f,6f); lineTo(8f,15f) },color,style = stroke)
                MailIcon.Folder -> path(3f,5f,10f,5f,12f,8f,21f,8f,21f,20f,3f,20f,3f,5f)
                MailIcon.Lock -> { drawRoundRect(color,Offset(5f,10f),Size(14f,11f),CornerRadius(2f),style = stroke)
                    drawArc(color,180f,180f,false,Offset(8f,3f),Size(8f,12f),style = stroke); line(12f,14f,12f,17f) }
                MailIcon.Check -> path(4f,12f,9f,17f,20f,6f)
            }
        }
    }
}
