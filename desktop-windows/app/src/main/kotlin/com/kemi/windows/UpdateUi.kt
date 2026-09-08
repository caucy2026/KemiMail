package com.kemi.windows

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import com.kemi.windows.designsystem.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.nio.file.Path

internal data class UpdateState(val visible: Boolean = false,val busy: Boolean = false,val update: MailUpdate? = null,
    val message: String = "",val progress: Int? = null,val packagePath: Path? = null,val error: Boolean = false)
internal class UpdateViewModel(private val client: KemiUpdateClient,private val installer: UpdateInstaller,
                               private val scope: CoroutineScope) {
    private val mutable = MutableStateFlow(UpdateState())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    fun check(manual: Boolean = true) {
        if (mutable.value.busy) return
        if (mutable.value.update != null) { mutable.update { it.copy(visible = true) }; return }
        mutable.value = UpdateState(visible = manual,busy = true,message = "正在检查更新…")
        job = scope.launch {
            try {
                val update = withContext(Dispatchers.IO) { client.check() }
                mutable.value = UpdateState(visible = manual || update != null,update = update,
                    message = if (update == null) "当前已是最新版本 ${MailRelease.name}" else "发现新版本 ${update.name}")
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { mutable.value = UpdateState(visible = manual,error = true,message = "检查更新失败，请检查网络后重试。邮箱仍可正常使用。") }
        }
    }
    fun dismiss() {
        if (mutable.value.update?.forced == true || mutable.value.busy) return
        mutable.update { it.copy(visible = false) }
    }
    fun cancel() {
        job?.cancel(); mutable.update { it.copy(busy = false,progress = null,message = "下载已取消，可稍后重试") }
    }
    fun download() {
        val update = mutable.value.update ?: return
        if (mutable.value.busy) return
        mutable.update { it.copy(busy = true,error = false,message = "正在下载安装包…",progress = 0) }
        job = scope.launch {
            try {
                val path = withContext(Dispatchers.IO) { client.download(update,installer.newDirectory()) { percent ->
                    mutable.update { it.copy(progress = percent) }
                } }
                mutable.update { it.copy(busy = false,packagePath = path,progress = null,message = "校验通过。安装前将保存草稿并关闭邮箱，完成后自动重新打开。") }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { mutable.update { it.copy(busy = false,progress = null,error = true,
                message = (error as? UpdateFailure)?.message ?: "下载失败，请检查网络后重试") } }
        }
    }
    fun install(onStarted: () -> Unit) {
        val snapshot = mutable.value
        val update = snapshot.update ?: return; val path = snapshot.packagePath ?: return
        mutable.update { it.copy(busy = true,message = "正在准备安装…") }
        scope.launch {
            try {
                withContext(Dispatchers.IO) { installer.launch(update,path) }
                onStarted()
            } catch (_: Exception) { mutable.update { it.copy(busy = false,error = true,message = "安装器未能启动，邮箱未退出。请关闭此提示后重新尝试。") } }
        }
    }
}

@Composable internal fun UpdateDialog(state: UpdateState,mailBusy: Boolean,controller: UpdateViewModel,onInstall: () -> Unit) {
    if (!state.visible) return
    DialogWindow(onCloseRequest = controller::dismiss,title = "KEMI邮箱 · 软件更新",icon = kemiMailPainter(),
        state = rememberDialogState(width = 500.dp,height = 430.dp)) {
        KemiTheme { Panel(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Label("软件更新",title = true)
                Label("当前版本 ${MailRelease.name}",muted = true,small = true)
                Label(state.message,error = state.error)
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                    state.update?.let {
                        if (it.forced) Label("此版本需要更新后继续使用",strong = true)
                        Label(it.notes.ifBlank { "功能改进与问题修复" },muted = true)
                    }
                }
                state.progress?.let { Busy(); Label("已下载 $it%",small = true,muted = true) }
                Row(Modifier.fillMaxWidth(),horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (state.busy && state.progress != null && state.update?.forced != true) Action("取消下载") { controller.cancel() }
                    if (!state.busy && state.update?.forced != true) Action("稍后",onClick = controller::dismiss)
                    Spacer(Modifier.weight(1f))
                    when {
                        state.packagePath != null -> Action("安装并重启",!state.busy && !mailBusy,primary = true,onClick = onInstall)
                        state.update != null -> Action("下载更新",!state.busy,primary = true,onClick = controller::download)
                        else -> Action("重新检查",!state.busy,onClick = { controller.check() })
                    }
                }
                if (mailBusy && state.packagePath != null) Label("请等待当前邮箱操作完成再安装",small = true,muted = true)
            }
        } }
    }
}
