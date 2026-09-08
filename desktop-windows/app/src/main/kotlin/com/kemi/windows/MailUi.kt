package com.kemi.windows

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import com.kemi.windows.designsystem.*
import java.awt.FileDialog
import java.awt.Frame
import java.nio.file.Path
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dateFormat = DateTimeFormatter.ofPattern("MM-dd HH:mm").withZone(ZoneId.systemDefault())
private val fullDateFormat = DateTimeFormatter.ofPattern("yyyy年M月d日  HH:mm").withZone(ZoneId.systemDefault())

@Composable fun MailScreen(state: MailState,dispatch: (MailEvent) -> Unit) {
    var query by remember { mutableStateOf("") }
    var deleteConfirm by remember { mutableStateOf(false) }
    KemiTheme { Panel(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.weight(1f)) {
                Sidebar(state) { event ->
                    if (event is MailEvent.SelectAccount || event is MailEvent.SelectFolder) query = ""
                    dispatch(event)
                }
                ColumnDivider()
                MailList(state,query,{ query = it },dispatch)
                ColumnDivider()
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    ReaderToolbar(state,dispatch) { deleteConfirm = true }
                    Divider()
                    if (state.busy) Busy() else Spacer(Modifier.height(2.dp))
                    state.detail?.let { Reader(it,state,dispatch) } ?: run {
                        if (state.loadingMail != null) Column(Modifier.fillMaxSize().padding(30.dp),verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            Label(state.loadingMail.subject,title = true,maxLines = 3)
                            Label("正在读取正文…",muted = true)
                            Label("附件将在点击保存时下载",small = true,muted = true)
                        } else EmptyReader(state,dispatch)
                    }
                }
            }
            Divider()
            Row(Modifier.fillMaxWidth().background(MailColors.wash).padding(horizontal = 16.dp,vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Label(state.status,Modifier.weight(1f),muted = !state.error,error = state.error,small = true,maxLines = 2)
                Label("KEMI邮箱  1.3.1",muted = true,small = true)
            }
        }
    }
        if (deleteConfirm) Confirm("移至已删除","将这封邮件移到服务器的已删除文件夹？",{ deleteConfirm = false }) {
            deleteConfirm = false; dispatch(MailEvent.Trash)
        }
        if (state.accountDialog) DialogWindow(onCloseRequest = { if (!state.busy) dispatch(MailEvent.CancelAccount) },
            title = "KEMI邮箱 · 账号设置",icon = kemiMailPainter(),state = rememberDialogState(width = 700.dp,height = 760.dp)) {
            window.minimumSize = java.awt.Dimension(600,560)
            AccountForm(state,dispatch)
        }
        if (state.draft != null) DialogWindow(onCloseRequest = { if (!state.busy) dispatch(MailEvent.CloseDraft) },
            title = "KEMI邮箱 · 撰写邮件",icon = kemiMailPainter(),state = rememberDialogState(width = 850.dp,height = 760.dp)) {
            window.minimumSize = java.awt.Dimension(680,540)
            ComposeForm(state,dispatch)
        }
    }
}

@Composable private fun Sidebar(state: MailState,dispatch: (MailEvent) -> Unit) {
    Panel(Modifier.width(218.dp).fillMaxHeight(),tinted = true) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.height(70.dp).padding(horizontal = 20.dp),verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BrandMark(); Label("KEMI邮箱",strong = true)
            }
            Column(Modifier.padding(horizontal = 13.dp)) {
                Label("账号",Modifier.padding(9.dp,8.dp),small = true,muted = true,strong = true)
                Column(Modifier.heightIn(max = 190.dp).verticalScroll(rememberScrollState()),verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    state.accounts.forEach { a -> AccountRow(a.name.ifBlank { a.email },a.email,state.account?.id == a.id,!state.busy) {
                        dispatch(MailEvent.SelectAccount(a))
                    } }
                }
                if (state.accounts.isEmpty()) Label("尚未添加邮箱",Modifier.padding(10.dp,6.dp),small = true,muted = true)
                Spacer(Modifier.height(19.dp))
                Label("邮箱",Modifier.padding(9.dp,8.dp),small = true,muted = true,strong = true)
            }
            LazyColumn(Modifier.weight(1f).padding(horizontal = 13.dp),verticalArrangement = Arrangement.spacedBy(3.dp)) {
                items(state.folders,key = { it.path }) { f ->
                    val icon = when { f.path.equals("INBOX",true) -> MailIcon.Inbox; f.sent -> MailIcon.Send
                        f.trash -> MailIcon.Trash; f.label == "草稿" -> MailIcon.Edit; else -> MailIcon.Folder }
                    NavigationRow(f.label,icon,state.folder?.path == f.path,!state.busy) { dispatch(MailEvent.SelectFolder(f)) }
                }
            }
            Column(Modifier.padding(13.dp),verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Divider()
                NavigationRow("添加账号",MailIcon.Plus,false,!state.busy && state.storageReady) { dispatch(MailEvent.EditAccount(null)) }
                state.account?.let { a -> NavigationRow("账号设置",MailIcon.Settings,false,!state.busy) { dispatch(MailEvent.EditAccount(a)) } }
            }
        }
    }
}

@Composable private fun MailList(state: MailState,query: String,onQuery: (String) -> Unit,dispatch: (MailEvent) -> Unit) {
    var filter by remember(state.account?.id,state.folder?.path) { mutableStateOf(MailFilter.ALL) }
    // A stable list width leaves the reading pane useful even at the supported 1000px minimum.
    Column(Modifier.width(310.dp).fillMaxHeight()) {
        Row(Modifier.fillMaxWidth().height(70.dp).padding(horizontal = 20.dp),verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Label(state.folder?.label ?: "邮件",title = true)
                Label("已加载 ${state.messages.size} 封",muted = true,small = true)
            }
            IconAction(MailIcon.Refresh,"刷新邮件",!state.busy && state.account != null) { dispatch(MailEvent.Refresh) }
        }
        Box(Modifier.padding(start = 16.dp,end = 16.dp,bottom = 15.dp)) { SearchField(query,onQuery) }
        Box(Modifier.padding(start = 16.dp,end = 16.dp,bottom = 12.dp)) {
            SegmentedChoice(MailFilter.entries.map { it.label },filter.ordinal) { filter = MailFilter.entries[it] }
        }
        Divider()
        val matches = filterMessages(state.messages,query,filter)
        Box(Modifier.weight(1f)) {
            val listState = key(state.account?.id,state.folder?.path) { androidx.compose.foundation.lazy.rememberLazyListState() }
            LazyColumn(Modifier.fillMaxSize().padding(horizontal = 8.dp),state = listState,contentPadding = PaddingValues(vertical = 8.dp)) {
                items(matches,key = { "${it.validity}:${it.uid}" }) { m ->
                    MessageRow(m.sender,m.subject,m.date?.let { dateFormat.format(it) } ?: "",m.seen,m.starred,
                        (state.loadingMail?.uid ?: state.detail?.summary?.uid) == m.uid,!state.busy) { dispatch(MailEvent.Read(m)) }
                    Box(Modifier.padding(start = 22.dp,end = 13.dp)) { Divider() }
                }
                if (matches.isEmpty()) item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp,horizontal = 22.dp),horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Glyph(if (query.isBlank()) MailIcon.Inbox else MailIcon.Search,Modifier.size(28.dp),MailColors.line)
                        Label(if (query.isBlank() && filter == MailFilter.ALL) "这里还没有邮件" else "没有匹配的邮件",muted = true)
                        Label(if (state.account == null) "添加账号后，刷新即可收取邮件" else "尝试刷新、切换筛选或修改搜索词",muted = true,small = true)
                    }
                }
            }
            VerticalScrollbar(rememberScrollbarAdapter(listState),Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(6.dp))
        }
        if (state.messages.size >= state.limit && state.limit < 2000) Box(Modifier.padding(12.dp)) {
            Action("加载更早邮件",!state.busy,modifier = Modifier.fillMaxWidth()) { dispatch(MailEvent.More) }
        }
    }
}

@Composable private fun ReaderToolbar(state: MailState,dispatch: (MailEvent) -> Unit,onDelete: () -> Unit) {
    val detail = state.detail
    val enabled = !state.busy && detail != null
    Row(Modifier.fillMaxWidth().height(70.dp).padding(horizontal = 18.dp),verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        IconAction(MailIcon.Reply,"回复",enabled) { dispatch(MailEvent.Compose(reply = true)) }
        IconAction(MailIcon.Mail,if (detail?.summary?.seen == true) "设为未读" else "设为已读",enabled) {
            detail?.let { dispatch(MailEvent.Flag(seen = !it.summary.seen)) }
        }
        IconAction(MailIcon.Star,if (detail?.summary?.starred == true) "取消星标" else "添加星标",enabled,
            selected = detail?.summary?.starred == true) { detail?.let { dispatch(MailEvent.Flag(starred = !it.summary.starred)) } }
        Box(Modifier.padding(horizontal = 5.dp).height(18.dp).width(1.dp).background(MailColors.line))
        IconAction(MailIcon.Trash,"移至已删除",enabled,onClick = onDelete)
        Spacer(Modifier.weight(1f))
        Action("写邮件",!state.busy && state.account != null,primary = true,icon = MailIcon.Edit) { dispatch(MailEvent.Compose()) }
    }
}

@Composable private fun EmptyReader(state: MailState,dispatch: (MailEvent) -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp),verticalArrangement = Arrangement.Center,horizontalAlignment = Alignment.CenterHorizontally) {
        BrandMark(large = true)
        Spacer(Modifier.height(24.dp))
        Label(if (state.accounts.isEmpty()) "欢迎使用 KEMI邮箱" else "每封邮件，清晰呈现",title = true)
        Spacer(Modifier.height(10.dp))
        Label(if (state.accounts.isEmpty()) "连接你的邮箱，开始从容处理邮件。" else "从左侧列表选择一封邮件，开始阅读。",muted = true)
        if (state.accounts.isEmpty()) {
            Spacer(Modifier.height(24.dp))
            Action("添加邮箱",!state.busy && state.storageReady,true,icon = MailIcon.Plus) { dispatch(MailEvent.EditAccount(null)) }
        }
    }
}

@Composable private fun Reader(detail: MailDetail,state: MailState,dispatch: (MailEvent) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 30.dp,vertical = 26.dp),verticalArrangement = Arrangement.spacedBy(20.dp)) {
        SelectionContainer { Label(detail.summary.subject,title = true,maxLines = 3) }
        Row(verticalAlignment = Alignment.Top,horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Avatar(detail.summary.sender,large = true)
            SelectionContainer { Column(Modifier.weight(1f),verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Label(detail.summary.sender,strong = true,maxLines = 2)
                Label("收件人：${detail.to}",muted = true,small = true,maxLines = 2)
                Label(detail.summary.date?.let { fullDateFormat.format(it) } ?: "",muted = true,small = true)
            } }
        }
        Divider()
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val scroll = key(detail.summary.uid,detail.summary.validity) { rememberScrollState() }
            SelectionContainer {
                Label(detail.body,Modifier.fillMaxSize().verticalScroll(scroll).padding(end = 14.dp))
            }
            VerticalScrollbar(rememberScrollbarAdapter(scroll),Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(6.dp))
        }
        if (detail.attachments.isNotEmpty()) {
            Divider()
            Column(Modifier.heightIn(max = 150.dp).verticalScroll(rememberScrollState()),verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Label("${detail.attachments.size} 个附件",muted = true,small = true,strong = true)
                detail.attachments.forEach { attachment ->
                    Action("${attachment.name}  ·  ${if (attachment.encodedSize >= 0) "约 ${attachment.encodedSize / 1024} KB" else "附件"}",!state.busy,icon = MailIcon.Attach) {
                        val dialog = FileDialog(null as Frame?,"保存附件",FileDialog.SAVE)
                        try { dialog.file = attachment.name; dialog.isVisible = true
                            if (dialog.file != null) dispatch(MailEvent.SaveAttachment(attachment,Path.of(dialog.directory,dialog.file)))
                        } finally { dialog.dispose() }
                    }
                }
            }
        }
    }
}

/** The same form is used in the native dialog and offscreen visual verification. */
@Composable internal fun AccountForm(state: MailState,dispatch: (MailEvent) -> Unit,formScroll: ScrollState = rememberScrollState()) {
    var account by remember(state.editingAccount?.id) { mutableStateOf(state.editingAccount ?: Account()) }
    var imapPort by remember { mutableStateOf(account.imapPort.toString()) }
    var smtpPort by remember { mutableStateOf(account.smtpPort.toString()) }
    var remove by remember { mutableStateOf(false) }
    val enabled = !state.busy
    KemiTheme { Panel(Modifier.fillMaxSize(),tinted = true) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.padding(24.dp),verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                BrandMark()
                Column { Label("连接你的邮箱",title = true); Label("使用邮箱授权码，安全连接收信与发信服务",small = true,muted = true) }
            }
            Box(Modifier.weight(1f)) {
                val scroll = formScroll
                Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 24.dp),verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Card(Modifier.fillMaxWidth()) {
                        Label("邮箱账号",strong = true)
                        Label("选择邮箱服务商，自动填入服务器设置",small = true,muted = true)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            MailProvider.entries.chunked(2).forEach { providers ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    providers.forEach { provider ->
                                        ProviderChoice(provider.label,provider.subtitle,
                                            provider.matches(account,imapPort,smtpPort),enabled,Modifier.weight(1f)) {
                                            account = provider.applyTo(account)
                                            imapPort = account.imapPort.toString(); smtpPort = account.smtpPort.toString()
                                        }
                                    }
                                }
                            }
                        }
                        if (MailProvider.ALIBABA.matches(account,imapPort,smtpPort)) {
                            Label("支持企业自有域名。请由管理员开启 IMAP 和第三方客户端登录权限，并使用客户端安全密码。",small = true,muted = true)
                        } else Label("也可在下方手动填写其他邮箱的服务器。",small = true,muted = true)
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Field(account.name,{ account = account.copy(name = it) },"显示名称",Modifier.weight(1f),enabled = enabled)
                            Field(account.email,{ val old = account.email; account = account.copy(email = it,
                                username = if (account.username.isBlank() || account.username == old) it else account.username) },
                                "邮箱地址",Modifier.weight(1.5f),enabled = enabled)
                        }
                        Field(account.username,{ account = account.copy(username = it) },"登录用户名（收信与发信共用）",Modifier.fillMaxWidth(),enabled = enabled)
                        Field(account.password,{ account = account.copy(password = it) },"密码 / 邮箱授权码",Modifier.fillMaxWidth(),password = true,enabled = enabled)
                        Label("请先在服务商网页启用 IMAP/SMTP。不支持仅允许 OAuth 登录的账号。",small = true,muted = true)
                    }
                    Card(Modifier.fillMaxWidth()) {
                        Label("收信服务器 · IMAP",strong = true)
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Field(account.imapHost,{ account = account.copy(imapHost = it.trim()) },"服务器域名",Modifier.weight(1f),enabled = enabled)
                            Field(imapPort,{ imapPort = it.filter(Char::isDigit).take(5) },"端口",Modifier.width(100.dp),enabled = enabled)
                        }
                        SecuritySelection(account.imapSecurity,enabled) { security -> account = account.copy(imapSecurity = security)
                            imapPort = if (security == Security.TLS) "993" else "143" }
                    }
                    Card(Modifier.fillMaxWidth()) {
                        Label("发信服务器 · SMTP",strong = true)
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Field(account.smtpHost,{ account = account.copy(smtpHost = it.trim()) },"服务器域名",Modifier.weight(1f),enabled = enabled)
                            Field(smtpPort,{ smtpPort = it.filter(Char::isDigit).take(5) },"端口",Modifier.width(100.dp),enabled = enabled)
                        }
                        SecuritySelection(account.smtpSecurity,enabled) { security -> account = account.copy(smtpSecurity = security)
                            smtpPort = if (security == Security.TLS) "465" else "587" }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                VerticalScrollbar(rememberScrollbarAdapter(scroll),Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(6.dp))
            }
            Column(Modifier.padding(24.dp,14.dp),verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (state.error || state.busy) Label(state.status,error = state.error,small = true,maxLines = 3)
                Row(verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Glyph(MailIcon.Lock,Modifier.size(14.dp)); Label("账号凭证仅在本机加密保存",muted = true,small = true)
                    Spacer(Modifier.weight(1f))
                    if (state.editingAccount != null) IconAction(MailIcon.Trash,"移除本地账号",enabled) { remove = true }
                    Action("取消",enabled) { dispatch(MailEvent.CancelAccount) }
                    Action("验证并保存",enabled,true) { dispatch(MailEvent.SaveAccount(account.copy(email = account.email.trim(),
                        username = account.username.trim(),imapPort = imapPort.toIntOrNull() ?: 0,smtpPort = smtpPort.toIntOrNull() ?: 0))) }
                }
            }
        }
        if (remove) Confirm("移除本地账号","将移除该账号和本地草稿，服务器邮件不会删除。",{ remove = false }) {
            remove = false; dispatch(MailEvent.RemoveAccount(account))
        }
    } }
}

@Composable private fun SecuritySelection(security: Security,enabled: Boolean,onChange: (Security) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically,horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Label("连接安全",small = true,muted = true)
        Security.entries.forEach { item -> Action(item.name,enabled,icon = if (security == item) MailIcon.Check else null) { onChange(item) } }
    }
}

@Composable internal fun ComposeForm(state: MailState,dispatch: (MailEvent) -> Unit) {
    val draft = state.draft ?: return
    var confirmSend by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val enabled = !state.busy
    fun update(value: ComposeDraft) { dispatch(MailEvent.EditDraft(value)) }
    KemiTheme { Panel(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().background(MailColors.wash).padding(24.dp,18.dp),verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Label("新邮件",title = true); Label("发件人：${state.draftAccount?.email}",muted = true,small = true) }
                Action("发送",enabled,true,icon = MailIcon.Send) { confirmSend = true }
            }
            Divider()
            Column(Modifier.weight(1f).padding(24.dp),verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Field(draft.to,{ update(draft.copy(to = it)) },"收件人（多个地址用逗号分隔）",Modifier.weight(1.5f),enabled = enabled)
                    Field(draft.cc,{ update(draft.copy(cc = it)) },"抄送",Modifier.weight(1f),enabled = enabled)
                }
                Field(draft.subject,{ update(draft.copy(subject = it)) },"主题",Modifier.fillMaxWidth(),enabled = enabled)
                Field(draft.body,{ update(draft.copy(body = it)) },"正文",Modifier.fillMaxWidth().weight(1f),multiline = true,enabled = enabled)
                if (draft.files.isNotEmpty()) Column(Modifier.heightIn(max = 100.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    draft.files.forEach { file -> Action("移除附件：${file.fileName}",enabled,icon = MailIcon.Attach) { update(draft.copy(files = draft.files - file)) } }
                }
                if (state.error || state.busy) Label(state.status,error = state.error,small = true,maxLines = 3)
            }
            Divider()
            Row(Modifier.fillMaxWidth().background(MailColors.wash).padding(20.dp,12.dp),verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Action("添加附件",enabled,icon = MailIcon.Attach) {
                    val dialog = FileDialog(null as Frame?,"添加附件",FileDialog.LOAD)
                    try { dialog.isMultipleMode = true; dialog.isVisible = true
                        update(draft.copy(files = (draft.files + dialog.files.map { it.toPath() }).distinct()))
                    } finally { dialog.dispose() }
                }
                IconAction(MailIcon.Trash,"丢弃草稿",enabled) { confirmDiscard = true }
                Spacer(Modifier.weight(1f))
                Action("保存草稿",enabled) { dispatch(MailEvent.SaveDraft) }
                Action("保存并关闭",enabled) { dispatch(MailEvent.CloseDraft) }
            }
        }
        if (confirmSend) Confirm("发送邮件","确认将此邮件发送给填写的收件人和抄送人？",{ confirmSend = false }) {
            confirmSend = false; dispatch(MailEvent.Send)
        }
        if (confirmDiscard) Confirm("丢弃草稿","删除这封尚未发送的本地草稿？",{ confirmDiscard = false }) {
            confirmDiscard = false; dispatch(MailEvent.DiscardDraft)
        }
    } }
}
