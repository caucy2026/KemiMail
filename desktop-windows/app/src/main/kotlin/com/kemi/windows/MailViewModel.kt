package com.kemi.windows

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import jakarta.mail.AuthenticationFailedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

// The UI emits events; only this state owner starts side effects or changes screen state.
data class MailState(
    val accounts: List<Account> = emptyList(), val account: Account? = null,
    val folders: List<MailFolder> = emptyList(), val folder: MailFolder? = null,
    val messages: List<MailSummary> = emptyList(), val detail: MailDetail? = null,
    val limit: Int = 100, val busy: Boolean = false, val status: String = "正在加载本地账号…",
    val error: Boolean = false, val storageReady: Boolean = false,
    val editingAccount: Account? = null, val accountDialog: Boolean = false,
    val draft: ComposeDraft? = null, val draftAccount: Account? = null,
)
sealed interface MailEvent {
    data object Refresh : MailEvent
    data object More : MailEvent
    data class SelectAccount(val account: Account) : MailEvent
    data class SelectFolder(val folder: MailFolder) : MailEvent
    data class Read(val mail: MailSummary) : MailEvent
    data class EditAccount(val account: Account?) : MailEvent
    data object CancelAccount : MailEvent
    data class SaveAccount(val account: Account) : MailEvent
    data class RemoveAccount(val account: Account) : MailEvent
    data class Flag(val seen: Boolean? = null, val starred: Boolean? = null) : MailEvent
    data object Trash : MailEvent
    data class Compose(val reply: Boolean = false) : MailEvent
    data class EditDraft(val draft: ComposeDraft) : MailEvent
    data object SaveDraft : MailEvent
    data object CloseDraft : MailEvent
    data object DiscardDraft : MailEvent
    data object Send : MailEvent
    data class SaveAttachment(val attachment: Attachment, val path: Path) : MailEvent
}

class MailViewModel(private val gateway: MailGateway, private val vault: AccountVault,
                    private val scope: CoroutineScope) {
    private val mutableState = MutableStateFlow(MailState())
    val state = mutableState.asStateFlow()
    init { work("正在加载本地账号…") {
        val accounts = withContext(Dispatchers.IO) { vault.load() }
        mutableState.update { it.copy(accounts = accounts, account = accounts.firstOrNull(), storageReady = true,
            status = if (accounts.isEmpty()) "欢迎使用 KEMI 邮箱，请添加账号" else "账号已加载，点击刷新收取邮件") }
    } }
    private fun work(message: String, block: suspend () -> Unit) {
        if (state.value.busy) return
        mutableState.update { it.copy(busy = true, status = message, error = false) }
        scope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutableState.update { it.copy(error = true, status = userError(e)) } }
            finally { mutableState.update { it.copy(busy = false) } }
        }
    }
    fun dispatch(event: MailEvent) {
        if (state.value.busy) return
        val current = state.value
        when (event) {
            MailEvent.Refresh -> current.account?.let { work("正在同步邮件…") { refresh(it) } }
            MailEvent.More -> current.account?.let { a -> current.folder?.let { f -> work("正在加载更早邮件…") {
                val limit = (current.limit + 100).coerceAtMost(2000)
                val messages = gateway.list(a,f.path,limit)
                mutableState.update { it.copy(messages = messages, limit = limit, status = "已加载 ${messages.size} 封邮件") }
            } } }
            is MailEvent.SelectAccount -> work("正在加载邮箱…") {
                mutableState.update { it.copy(account = event.account, folders = emptyList(), folder = null,
                    messages = emptyList(), detail = null, limit = 100) }
                refresh(event.account)
            }
            is MailEvent.SelectFolder -> current.account?.let { a -> work("正在加载文件夹…") {
                mutableState.update { it.copy(folder = event.folder, messages = emptyList(), detail = null, limit = 100) }
                val messages = gateway.list(a,event.folder.path,100)
                mutableState.update { it.copy(messages = messages, status = "已加载 ${messages.size} 封邮件") }
            } }
            is MailEvent.Read -> current.account?.let { a -> current.folder?.let { f -> work("正在读取邮件…") {
                mutableState.update { it.copy(detail = null) }
                val detail = gateway.read(a,f.path,event.mail)
                mutableState.update { it.copy(detail = detail, status = "邮件已打开 · 远程内容已禁用") }
            } } }
            is MailEvent.EditAccount -> if (current.storageReady) mutableState.update {
                it.copy(accountDialog = true, editingAccount = event.account)
            }
            MailEvent.CancelAccount -> mutableState.update { it.copy(accountDialog = false, editingAccount = null) }
            is MailEvent.SaveAccount -> if (current.storageReady) work("正在验证 IMAP 和 SMTP 连接…") {
                event.account.validate(); gateway.check(event.account)
                val accounts = current.accounts.filterNot { it.id == event.account.id } + event.account
                withContext(Dispatchers.IO) { vault.save(accounts) }
                mutableState.update { it.copy(accounts = accounts, account = event.account, accountDialog = false,
                    editingAccount = null, folders = emptyList(), folder = null, messages = emptyList(), detail = null) }
                refresh(event.account)
            }
            is MailEvent.RemoveAccount -> work("正在移除本地账号…") {
                val accounts = current.accounts.filterNot { it.id == event.account.id }
                withContext(Dispatchers.IO) { vault.save(accounts); vault.deleteDraft(event.account.id) }
                mutableState.update { it.copy(accounts = accounts, account = accounts.firstOrNull(), folders = emptyList(),
                    folder = null, messages = emptyList(), detail = null, accountDialog = false,
                    status = "已移除本地账号，服务器上的邮件保持不变") }
            }
            is MailEvent.Flag -> current.account?.let { a -> current.folder?.let { f -> current.detail?.let { d ->
                work("正在更新邮件标记…") {
                    gateway.flag(a,f.path,d.summary,event.seen,event.starred)
                    val updated = d.summary.copy(seen = event.seen ?: d.summary.seen, starred = event.starred ?: d.summary.starred)
                    mutableState.update { it.copy(detail = d.copy(summary = updated),
                        messages = it.messages.map { m -> if (m.uid == updated.uid) updated else m }, status = "邮件标记已更新") }
                }
            } } }
            MailEvent.Trash -> current.account?.let { a -> current.folder?.let { f -> current.detail?.let { d ->
                work("正在移至已删除…") {
                    val trash = current.folders.firstOrNull { it.trash } ?: throw MailFailure("未找到已删除文件夹，请在网页版操作")
                    if (f.path == trash.path) throw MailFailure("邮件已在已删除文件夹中，不提供永久删除操作")
                    gateway.trash(a,f.path,d.summary,trash.path)
                    mutableState.update { it.copy(detail = null, messages = it.messages.filterNot { m -> m.uid == d.summary.uid },
                        status = "邮件已移至已删除文件夹") }
                }
            } } }
            is MailEvent.Compose -> current.account?.let { a -> work("正在打开本地草稿…") {
                val existing = withContext(Dispatchers.IO) { vault.loadDraft(a.id) }
                val hasDraft = existing.to.isNotBlank() || existing.body.isNotBlank() || existing.subject.isNotBlank() || existing.files.isNotEmpty()
                val draft = if (!hasDraft && event.reply && current.detail != null) {
                    val d = current.detail
                    ComposeDraft(to = d.replyTo, subject = if (d.summary.subject.startsWith("Re:",true)) d.summary.subject else "Re: ${d.summary.subject}",
                        body = "\n\n---------- 原邮件 ----------\n${d.summary.sender}\n${d.body.take(20000)}", replyId = d.messageId)
                } else existing
                mutableState.update { it.copy(draft = draft, draftAccount = a,
                    status = if (hasDraft) "已恢复未发送的本地草稿" else "关闭撰写窗口会加密保存草稿") }
            } }
            is MailEvent.EditDraft -> mutableState.update { it.copy(draft = event.draft) }
            MailEvent.SaveDraft, MailEvent.CloseDraft -> work("正在保存草稿…") {
                saveDraft()
                mutableState.update { it.copy(draft = if (event == MailEvent.CloseDraft) null else it.draft,
                    status = "本地草稿已加密保存") }
            }
            MailEvent.DiscardDraft -> work("正在丢弃草稿…") {
                current.draftAccount?.let { withContext(Dispatchers.IO) { vault.deleteDraft(it.id) } }
                mutableState.update { it.copy(draft = null, draftAccount = null, status = "本地草稿已丢弃") }
            }
            MailEvent.Send -> current.draftAccount?.let { a -> current.draft?.let { draft -> work("正在发送，请勿重复点击…") {
                draft.validate(); saveDraft()
                val result = gateway.send(a,draft)
                // Clear the editor immediately after acceptance, even if local cleanup fails.
                mutableState.update { it.copy(draft = null, draftAccount = null, status = if (result.savedToSent)
                    "邮件已发送，副本已保存到已发送文件夹" else "邮件已发送，但未保存服务器副本；请勿重复发送") }
                try { withContext(Dispatchers.IO) { vault.deleteDraft(a.id) } }
                catch (_: Exception) { mutableState.update { it.copy(error = true,
                    status = "邮件已发送，但本地草稿未能清理。下次恢复草稿时请勿重复发送。") } }
            } } }
            is MailEvent.SaveAttachment -> work("正在保存附件…") {
                withContext(Dispatchers.IO) {
                    val temp = Files.createTempFile(event.path.toAbsolutePath().parent,"kemimail-", ".tmp")
                    try { Files.write(temp,event.attachment.bytes)
                        Files.move(temp,event.path,StandardCopyOption.REPLACE_EXISTING)
                    } finally { Files.deleteIfExists(temp) }
                }
                mutableState.update { it.copy(status = "附件已保存") }
            }
        }
    }
    private suspend fun refresh(a: Account) {
        val folders = gateway.folders(a)
        val folder = folders.firstOrNull { it.path == state.value.folder?.path } ?: folders.firstOrNull()
        mutableState.update { it.copy(folders = folders, folder = folder, detail = null, messages = emptyList()) }
        val messages = folder?.let { gateway.list(a,it.path,state.value.limit) } ?: emptyList()
        mutableState.update { it.copy(messages = messages, status = "同步完成 · 已加载 ${messages.size} 封邮件") }
    }
    private suspend fun saveDraft() {
        val s = state.value
        if (s.draftAccount != null && s.draft != null) withContext(Dispatchers.IO) { vault.saveDraft(s.draftAccount.id,s.draft) }
    }
    fun close(onClosed: () -> Unit) {
        if (state.value.busy) return
        work("正在保存并退出…") { saveDraft(); onClosed() }
    }
}
internal fun userError(error: Exception): String = when (error) {
    is MailFailure -> error.userMessage
    is AuthenticationFailedException -> "登录失败。请检查用户名、邮箱授权码，以及服务商是否已启用 IMAP/SMTP。"
    is IllegalArgumentException -> error.message?.takeIf { it.length < 180 && !it.contains('@') } ?: "输入信息不正确，请检查后重试"
    else -> "操作失败。请检查网络、服务器配置或本地文件权限后重试。服务器证书错误不会被绕过。"
}
