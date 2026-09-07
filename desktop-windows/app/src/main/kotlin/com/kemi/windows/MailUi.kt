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

@Composable fun MailScreen(state: MailState, dispatch: (MailEvent) -> Unit) {
    var query by remember { mutableStateOf("") }
    var deleteConfirm by remember { mutableStateOf(false) }
    KemiTheme {
        Panel(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(18.dp,12.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Label("KEMI 邮箱", title = true)
                    Label("Windows · 1.0.0", muted = true)
                    Spacer(Modifier.weight(1f))
                    Action("写邮件", !state.busy && state.account != null, primary = true) { dispatch(MailEvent.Compose()) }
                    Action("刷新", !state.busy && state.account != null) { dispatch(MailEvent.Refresh) }
                }
                Divider()
                if (state.busy) Busy() else Spacer(Modifier.height(4.dp))
                Row(Modifier.weight(1f)) {
                    Panel(Modifier.width(216.dp).fillMaxHeight(), tinted = true) {
                        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Label("我的账号", muted = true)
                            Column(Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState())) {
                                state.accounts.forEach { a -> Choice(a.name.ifBlank { a.email }, state.account?.id == a.id,
                                    !state.busy, supporting = a.email) { query = ""; dispatch(MailEvent.SelectAccount(a)) } }
                            }
                            Action("＋ 添加账号", !state.busy && state.storageReady, modifier = Modifier.fillMaxWidth()) {
                                dispatch(MailEvent.EditAccount(null))
                            }
                            Divider()
                            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                items(state.folders, key = { it.path }) { f ->
                                    Choice(f.label, state.folder?.path == f.path, !state.busy) {
                                        query = ""; dispatch(MailEvent.SelectFolder(f))
                                    }
                                }
                            }
                            state.account?.let { a -> Action("账号设置", !state.busy, modifier = Modifier.fillMaxWidth()) {
                                dispatch(MailEvent.EditAccount(a))
                            } }
                        }
                    }
                    Panel(Modifier.width(320.dp).fillMaxHeight()) {
                        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Label(state.folder?.label ?: "邮件列表", title = true)
                            Field(query,{ query = it },"搜索已加载邮件",Modifier.fillMaxWidth())
                            Label("${state.messages.size} 封 · 最新在前", muted = true)
                            Divider()
                            val matches = state.messages.filter {
                                query.isBlank() || it.subject.contains(query,true) || it.sender.contains(query,true)
                            }
                            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                items(matches, key = { "${it.validity}:${it.uid}" }) { m ->
                                    Choice((if (m.starred) "★ " else "") + (if (!m.seen) "● " else "") + m.subject,
                                        state.detail?.summary?.uid == m.uid, !state.busy,
                                        supporting = m.sender + "\n" + (m.date?.let { dateFormat.format(it) } ?: "")) {
                                        dispatch(MailEvent.Read(m))
                                    }
                                }
                                if (matches.isEmpty()) item { Label(if (query.isNotBlank()) "没有匹配邮件" else "暂无邮件，添加账号后点击刷新", muted = true) }
                            }
                            if (state.messages.size >= state.limit && state.limit < 2000) Action("加载更早邮件", !state.busy,
                                modifier = Modifier.fillMaxWidth()) { dispatch(MailEvent.More) }
                        }
                    }
                    Box(Modifier.width(1.dp).fillMaxHeight().background(androidx.compose.ui.graphics.Color(0xFFE7E8EE)))
                    Panel(Modifier.weight(1f).fillMaxHeight()) {
                        val detail = state.detail
                        if (detail == null) Column(Modifier.fillMaxSize().padding(32.dp),
                            verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                            Label(if (state.accounts.isEmpty()) "让邮件回到一个清晰的窗口" else "选择一封邮件开始阅读", title = true)
                            Spacer(Modifier.height(16.dp))
                            Label("账号、文件夹和邮件，全部在当前屏幕内管理。", muted = true)
                            Spacer(Modifier.height(8.dp))
                            Label("仅在本机保存加密账号与草稿，不加载邮件远程内容。", muted = true)
                            if (state.accounts.isEmpty()) {
                                Spacer(Modifier.height(24.dp))
                                Action("添加第一个邮箱", !state.busy && state.storageReady, true) { dispatch(MailEvent.EditAccount(null)) }
                            }
                        } else Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Action("回复", !state.busy) { dispatch(MailEvent.Compose(reply = true)) }
                                Action(if (detail.summary.seen) "设为未读" else "设为已读", !state.busy) {
                                    dispatch(MailEvent.Flag(seen = !detail.summary.seen))
                                }
                                Action(if (detail.summary.starred) "取消星标" else "星标", !state.busy) {
                                    dispatch(MailEvent.Flag(starred = !detail.summary.starred))
                                }
                                Action("删除", !state.busy) { deleteConfirm = true }
                            }
                            SelectionContainer { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Label(detail.summary.subject,title = true)
                                Label("发件人：${detail.summary.sender}", muted = true)
                                Label("收件人：${detail.to}", muted = true)
                                Label(detail.summary.date?.let { dateFormat.format(it) } ?: "", muted = true)
                            } }
                            Divider()
                            Box(Modifier.weight(1f).fillMaxWidth()) {
                                val scroll = rememberScrollState()
                                LaunchedEffect(detail.summary.uid,detail.summary.validity) { scroll.scrollTo(0) }
                                SelectionContainer { Label(detail.body,Modifier.fillMaxSize().verticalScroll(scroll).padding(end = 16.dp)) }
                                VerticalScrollbar(rememberScrollbarAdapter(scroll),Modifier.align(Alignment.CenterEnd).fillMaxHeight())
                            }
                            if (detail.attachments.isNotEmpty()) {
                                Divider()
                                Label("附件 · ${detail.attachments.size} 个", muted = true)
                                Column(Modifier.heightIn(max = 130.dp).verticalScroll(rememberScrollState())) {
                                    detail.attachments.forEach { attachment ->
                                        Action("保存 ${attachment.name} (${attachment.bytes.size / 1024} KB)", !state.busy) {
                                            val dialog = FileDialog(null as Frame?,"保存附件",FileDialog.SAVE)
                                            try {
                                                dialog.file = attachment.name; dialog.isVisible = true
                                                if (dialog.file != null) dispatch(MailEvent.SaveAttachment(attachment,Path.of(dialog.directory,dialog.file)))
                                            } finally { dialog.dispose() }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Divider()
                Label(state.status,Modifier.fillMaxWidth().padding(12.dp,8.dp),error = state.error,maxLines = 3)
            }
        }
        if (deleteConfirm) Confirm("移至已删除", "将这封邮件移到服务器的已删除文件夹？",{ deleteConfirm = false }) {
            deleteConfirm = false; dispatch(MailEvent.Trash)
        }
        if (state.accountDialog) AccountEditor(state,dispatch)
        if (state.draft != null) ComposeEditor(state,dispatch)
    }
}

@Composable private fun AccountEditor(state: MailState, dispatch: (MailEvent) -> Unit) {
    var account by remember(state.editingAccount?.id) { mutableStateOf(state.editingAccount ?: Account()) }
    var imapPort by remember { mutableStateOf(account.imapPort.toString()) }
    var smtpPort by remember { mutableStateOf(account.smtpPort.toString()) }
    var remove by remember { mutableStateOf(false) }
    val enabled = !state.busy
    DialogWindow(onCloseRequest = { if (enabled) dispatch(MailEvent.CancelAccount) },title = "邮箱账号设置",
        state = rememberDialogState(width = 680.dp,height = 780.dp)) {
        KemiTheme { Panel(Modifier.fillMaxSize()) { Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Label("连接你的邮箱", title = true)
            Label("先在服务商网页启用 IMAP/SMTP，再填写邮箱授权码。首版支持密码或授权码登录，不支持 OAuth。",muted = true)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("QQ" to "qq.com", "163" to "163.com", "126" to "126.com").forEach { (label,domain) ->
                        Action(label,enabled) { account = account.copy(imapHost = "imap.$domain",smtpHost = "smtp.$domain",
                            imapSecurity = Security.TLS,smtpSecurity = Security.TLS); imapPort = "993"; smtpPort = "465" }
                    }
                    Label("其他邮箱请手动填写", muted = true)
                }
                Field(account.name,{ account = account.copy(name = it) },"显示名称",Modifier.fillMaxWidth(),enabled = enabled)
                Field(account.email,{ val old = account.email; account = account.copy(email = it,
                    username = if (account.username.isBlank() || account.username == old) it else account.username) },
                    "邮箱地址",Modifier.fillMaxWidth(),enabled = enabled)
                Field(account.username,{ account = account.copy(username = it) },"登录用户名（收发服务器共用）",Modifier.fillMaxWidth(),enabled = enabled)
                Field(account.password,{ account = account.copy(password = it) },"密码 / 邮箱授权码",Modifier.fillMaxWidth(),password = true,enabled = enabled)
                Label("收信服务器 · IMAP", title = true)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field(account.imapHost,{ account = account.copy(imapHost = it.trim()) },"服务器域名",Modifier.weight(1f),enabled = enabled)
                    Field(imapPort,{ imapPort = it.filter(Char::isDigit).take(5) },"端口",Modifier.width(110.dp),enabled = enabled)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Action(if (account.imapSecurity == Security.TLS) "● TLS" else "TLS",enabled) { account = account.copy(imapSecurity = Security.TLS); imapPort = "993" }
                    Action(if (account.imapSecurity == Security.STARTTLS) "● STARTTLS" else "STARTTLS",enabled) { account = account.copy(imapSecurity = Security.STARTTLS); imapPort = "143" }
                }
                Label("发信服务器 · SMTP", title = true)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Field(account.smtpHost,{ account = account.copy(smtpHost = it.trim()) },"服务器域名",Modifier.weight(1f),enabled = enabled)
                    Field(smtpPort,{ smtpPort = it.filter(Char::isDigit).take(5) },"端口",Modifier.width(110.dp),enabled = enabled)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Action(if (account.smtpSecurity == Security.TLS) "● TLS" else "TLS",enabled) { account = account.copy(smtpSecurity = Security.TLS); smtpPort = "465" }
                    Action(if (account.smtpSecurity == Security.STARTTLS) "● STARTTLS" else "STARTTLS",enabled) { account = account.copy(smtpSecurity = Security.STARTTLS); smtpPort = "587" }
                }
                Label("账号与授权码仅由当前 Windows 用户加密保存；连接验证不会发送邮件。",muted = true)
            }
            Label(state.status,error = state.error,maxLines = 4)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Action("验证并保存",enabled,true) { dispatch(MailEvent.SaveAccount(account.copy(
                    email = account.email.trim(),username = account.username.trim(),imapPort = imapPort.toIntOrNull() ?: 0,smtpPort = smtpPort.toIntOrNull() ?: 0))) }
                Action("取消",enabled) { dispatch(MailEvent.CancelAccount) }
                if (state.editingAccount != null) Action("移除账号",enabled) { remove = true }
            }
            if (remove) Confirm("移除本地账号", "将移除该账号和本地草稿，服务器邮件不会删除。",{ remove = false }) {
                remove = false; dispatch(MailEvent.RemoveAccount(account))
            }
        } } }
    }
}

@Composable private fun ComposeEditor(state: MailState,dispatch: (MailEvent) -> Unit) {
    val draft = state.draft ?: return
    var confirmSend by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val enabled = !state.busy
    fun update(value: ComposeDraft) { dispatch(MailEvent.EditDraft(value)) }
    DialogWindow(onCloseRequest = { if (enabled) dispatch(MailEvent.CloseDraft) },title = "撰写邮件",
        state = rememberDialogState(width = 840.dp,height = 760.dp)) {
        KemiTheme { Panel(Modifier.fillMaxSize()) { Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Label("撰写邮件",title = true)
            Label("发件账号：${state.draftAccount?.email}", muted = true)
            Field(draft.to,{ update(draft.copy(to = it)) },"收件人（多个地址用逗号分隔）",Modifier.fillMaxWidth(),enabled = enabled)
            Field(draft.cc,{ update(draft.copy(cc = it)) },"抄送",Modifier.fillMaxWidth(),enabled = enabled)
            Field(draft.subject,{ update(draft.copy(subject = it)) },"主题",Modifier.fillMaxWidth(),enabled = enabled)
            Field(draft.body,{ update(draft.copy(body = it)) },"邮件正文",Modifier.fillMaxWidth().weight(1f),multiline = true,enabled = enabled)
            if (draft.files.isNotEmpty()) Column(Modifier.heightIn(max = 100.dp).verticalScroll(rememberScrollState())) {
                draft.files.forEach { file -> Action("移除附件：${file.fileName}",enabled) { update(draft.copy(files = draft.files - file)) } }
            }
            Label(state.status,error = state.error,maxLines = 3)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Action("发送",enabled,true) { confirmSend = true }
                Action("添加附件",enabled) {
                    val dialog = FileDialog(null as Frame?,"添加附件",FileDialog.LOAD)
                    try { dialog.isMultipleMode = true; dialog.isVisible = true
                        update(draft.copy(files = (draft.files + dialog.files.map { it.toPath() }).distinct()))
                    } finally { dialog.dispose() }
                }
                Action("保存草稿",enabled) { dispatch(MailEvent.SaveDraft) }
                Action("保存并关闭",enabled) { dispatch(MailEvent.CloseDraft) }
                Action("丢弃",enabled) { confirmDiscard = true }
            }
            if (confirmSend) Confirm("发送邮件", "确认将此邮件发送给填写的收件人和抄送人？",{ confirmSend = false }) {
                confirmSend = false; dispatch(MailEvent.Send)
            }
            if (confirmDiscard) Confirm("丢弃草稿", "删除这封尚未发送的本地草稿？",{ confirmDiscard = false }) {
                confirmDiscard = false; dispatch(MailEvent.DiscardDraft)
            }
        } } }
    }
}
