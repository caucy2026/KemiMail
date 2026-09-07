package com.kemi.windows.designsystem

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.thunderbird.core.ui.compose.theme2.k9mail.lightThemeColorScheme

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private val UiFont = if (System.getProperty("os.name").startsWith("Windows")) FontFamily("Microsoft YaHei UI") else FontFamily.SansSerif

object MailColors {
    val ink = Color(0xFF25272B)
    val secondary = Color(0xFF73777F)
    val line = Color(0xFFE6E7EA)
    val sidebar = Color(0xFFF4F5F7)
    val wash = Color(0xFFFAFBFC)
    val selection = Color(0xFFE6EFFC)
    val blue = lightThemeColorScheme.primary
}

// Desktop adapters extend the shared K-9 theme and keep all Material controls in the design system.
@Composable fun KemiTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = lightColorScheme(primary = MailColors.blue, onPrimary = Color.White,
        surface = Color.White, onSurface = MailColors.ink, onSurfaceVariant = MailColors.secondary,
        outline = MailColors.line, primaryContainer = MailColors.selection,
        error = lightThemeColorScheme.error), content = content)
}
@Composable fun Label(text: String, modifier: Modifier = Modifier, title: Boolean = false,
                      muted: Boolean = false, maxLines: Int = Int.MAX_VALUE, error: Boolean = false,
                      small: Boolean = false, strong: Boolean = false) {
    Text(text, modifier, fontFamily = UiFont,
        fontSize = when { title -> 23.sp; small -> 12.sp; else -> 14.sp },
        lineHeight = when { title -> 32.sp; small -> 18.sp; else -> 23.sp },
        fontWeight = if (title || strong) FontWeight.SemiBold else FontWeight.Normal,
        color = when { error -> MaterialTheme.colorScheme.error; muted -> MailColors.secondary; else -> MailColors.ink },
        maxLines = maxLines, overflow = TextOverflow.Ellipsis)
}
@Composable fun Action(text: String, enabled: Boolean = true, primary: Boolean = false,
                       modifier: Modifier = Modifier, icon: MailIcon? = null, onClick: () -> Unit) {
    Button(onClick, modifier.heightIn(min = 34.dp), enabled, shape = RoundedCornerShape(7.dp),
        contentPadding = PaddingValues(horizontal = 13.dp, vertical = 6.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (primary) MailColors.blue else Color(0xFFF0F1F3),
            contentColor = if (primary) Color.White else MailColors.ink,
            disabledContainerColor = Color(0xFFF2F3F5), disabledContentColor = Color(0xFFADB0B7)),
        elevation = ButtonDefaults.buttonElevation(0.dp,0.dp,0.dp,0.dp,0.dp)) {
        icon?.let { Glyph(it,Modifier.size(16.dp),if (primary) Color.White else MailColors.secondary); Spacer(Modifier.width(6.dp)) }
        Text(text,fontFamily = UiFont,fontSize = 13.sp,fontWeight = FontWeight.Medium, maxLines = 1)
    }
}
@OptIn(ExperimentalFoundationApi::class)
@Composable fun IconAction(icon: MailIcon, description: String, enabled: Boolean = true, selected: Boolean = false,
                           onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hover by interaction.collectIsHoveredAsState()
    TooltipArea(tooltip = {
        Surface(color = Color.White,shape = RoundedCornerShape(6.dp),shadowElevation = 4.dp,border = BorderStroke(1.dp,MailColors.line)) {
            Label(description,Modifier.padding(9.dp,5.dp),small = true)
        }
    }, delayMillis = 450) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(7.dp))
            .background(if (selected) MailColors.selection else if (hover && enabled) Color(0xFFEAECF0) else Color.Transparent)
            .hoverable(interaction).clickable(interactionSource = interaction,indication = null,enabled = enabled,role = androidx.compose.ui.semantics.Role.Button,onClick = onClick)
            .semantics { contentDescription = description },contentAlignment = Alignment.Center) {
            Glyph(icon,Modifier.size(19.dp),if (!enabled) Color(0xFFBFC2C8) else if (selected) MailColors.blue else Color(0xFF656A73))
        }
    }
}
@Composable fun Field(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier,
                      password: Boolean = false, multiline: Boolean = false, enabled: Boolean = true) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(modifier,verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Label(label,muted = true,small = true)
        BasicTextField(value,onChange,
            (if (multiline) Modifier.weight(1f) else Modifier.heightIn(min = 38.dp)).fillMaxWidth()
                .clip(RoundedCornerShape(7.dp)).background(if (enabled) Color.White else MailColors.wash)
                .border(1.dp,if (focused) MailColors.blue.copy(alpha = .65f) else MailColors.line,RoundedCornerShape(7.dp))
                .padding(horizontal = 11.dp,vertical = 8.dp).semantics { contentDescription = label },
            enabled = enabled,singleLine = !multiline,interactionSource = interaction,
            cursorBrush = SolidColor(MailColors.blue),textStyle = TextStyle(color = MailColors.ink,fontSize = 14.sp,
                fontFamily = UiFont,lineHeight = 24.sp),
            visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
            decorationBox = { inner -> Box(contentAlignment = if (multiline) Alignment.TopStart else Alignment.CenterStart) { inner() } })
    }
}
@Composable fun SearchField(value: String,onChange: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFF0F1F4)).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Glyph(MailIcon.Search,Modifier.size(16.dp),MailColors.secondary)
        BasicTextField(value,onChange,Modifier.weight(1f).semantics { contentDescription = "搜索已加载邮件" },singleLine = true,
            cursorBrush = SolidColor(MailColors.blue),textStyle = TextStyle(fontFamily = UiFont,fontSize = 13.sp,color = MailColors.ink),
            decorationBox = { inner -> Box { if (value.isEmpty()) Label("搜索已加载邮件",muted = true,small = true); inner() } })
    }
}
@Composable fun Panel(modifier: Modifier = Modifier,tinted: Boolean = false,content: @Composable () -> Unit) {
    Surface(modifier,color = if (tinted) MailColors.sidebar else Color.White,content = content)
}
@Composable fun Card(modifier: Modifier = Modifier,content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(Color.White).border(1.dp,MailColors.line,RoundedCornerShape(12.dp))
        .padding(18.dp),verticalArrangement = Arrangement.spacedBy(12.dp),content = content)
}
@Composable fun Avatar(name: String,large: Boolean = false) {
    Box(Modifier.size(if (large) 42.dp else 30.dp).clip(CircleShape).background(Color(0xFFE7EDF6)),contentAlignment = Alignment.Center) {
        Text(name.trim().firstOrNull()?.uppercase() ?: "M",fontFamily = UiFont,fontSize = if (large) 18.sp else 13.sp,
            fontWeight = FontWeight.Medium,color = Color(0xFF5D7599))
    }
}
@Composable fun BrandMark(large: Boolean = false) {
    Box(Modifier.size(if (large) 68.dp else 32.dp).clip(RoundedCornerShape(if (large) 18.dp else 9.dp))
        .background(MailColors.blue),contentAlignment = Alignment.Center) {
        Glyph(MailIcon.Mail,Modifier.size(if (large) 34.dp else 20.dp),Color.White)
    }
}
@Composable fun NavigationRow(text: String,icon: MailIcon,selected: Boolean,enabled: Boolean = true,onClick: () -> Unit) {
    Surface(onClick,Modifier.fillMaxWidth(),enabled,shape = RoundedCornerShape(7.dp),
        color = if (selected) Color(0xFFE0E5ED) else Color.Transparent) {
        Row(Modifier.padding(11.dp,9.dp),verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            Glyph(icon,Modifier.size(18.dp),MailColors.blue)
            Label(text,maxLines = 1,strong = selected)
        }
    }
}
@Composable fun AccountRow(name: String,email: String,selected: Boolean,enabled: Boolean,onClick: () -> Unit) {
    Surface(onClick,Modifier.fillMaxWidth(),enabled,shape = RoundedCornerShape(9.dp),color = if (selected) Color.White else Color.Transparent) {
        Row(Modifier.padding(9.dp,10.dp),verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Avatar(name)
            Column(Modifier.weight(1f)) { Label(name,maxLines = 1,strong = true); Label(email,small = true,muted = true,maxLines = 1) }
        }
    }
}
@Composable fun MessageRow(sender: String,subject: String,date: String,seen: Boolean,starred: Boolean,
                           selected: Boolean,enabled: Boolean,onClick: () -> Unit) {
    Surface(onClick,Modifier.fillMaxWidth(),enabled,shape = RoundedCornerShape(9.dp),
        color = if (selected) MailColors.selection else Color.Transparent) {
        Row(Modifier.padding(start = 8.dp,end = 13.dp,top = 14.dp,bottom = 14.dp),horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.padding(top = 8.dp).size(5.dp).clip(CircleShape).background(if (!seen) MailColors.blue else Color.Transparent))
            Column(Modifier.weight(1f),verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Label(sender,Modifier.weight(1f),strong = !seen,maxLines = 1)
                    Label(date,muted = true,small = true,maxLines = 1)
                }
                Label(subject,maxLines = 2,muted = seen)
                if (starred) Glyph(MailIcon.Star,Modifier.size(13.dp),Color(0xFFDAA331))
            }
        }
    }
}
@Composable fun Divider() { HorizontalDivider(color = MailColors.line,thickness = 1.dp) }
@Composable fun ColumnDivider() { Box(Modifier.width(1.dp).fillMaxHeight().background(MailColors.line)) }
@Composable fun Busy() { LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp),color = MailColors.blue) }
@Composable fun Confirm(title: String,text: String,onDismiss: () -> Unit,onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss,title = { Label(title,title = true) },text = { Label(text) },
        shape = RoundedCornerShape(16.dp),containerColor = Color.White,
        confirmButton = { Action("确认",primary = true,onClick = onConfirm) },dismissButton = { Action("取消",onClick = onDismiss) })
}

@Composable fun ProviderChoice(title: String,subtitle: String,selected: Boolean,enabled: Boolean,
                               modifier: Modifier = Modifier,onClick: () -> Unit) {
    Surface(onClick,modifier,enabled,shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp,if (selected) MailColors.blue.copy(alpha = .5f) else MailColors.line),
        color = if (selected) MailColors.selection else MailColors.wash) {
        Row(Modifier.padding(12.dp),verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Glyph(if (selected) MailIcon.Check else MailIcon.Mail,Modifier.size(20.dp),if (selected) MailColors.blue else MailColors.secondary)
            Column(Modifier.weight(1f)) { Label(title,strong = true,maxLines = 1); Label(subtitle,small = true,muted = true,maxLines = 1) }
        }
    }
}

@Composable fun SegmentedChoice(labels: List<String>,selected: Int,onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFFF0F1F4)).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        labels.forEachIndexed { index,label ->
            Surface({ onSelect(index) },Modifier.weight(1f),shape = RoundedCornerShape(6.dp),
                color = if (index == selected) Color.White else Color.Transparent,
                shadowElevation = if (index == selected) 1.dp else 0.dp) {
                Box(Modifier.padding(vertical = 5.dp),contentAlignment = Alignment.Center) { Label(label,small = true,strong = index == selected,muted = index != selected) }
            }
        }
    }
}
