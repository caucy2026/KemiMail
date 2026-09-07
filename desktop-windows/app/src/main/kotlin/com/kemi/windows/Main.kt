package com.kemi.windows

import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.*
import org.koin.core.context.startKoin
import org.koin.dsl.module
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import javax.swing.JOptionPane

fun main(args: Array<String>) {
    if (args.firstOrNull() == "--render-preview") { renderPreview(Path.of(args[1])); return }
    if (args.firstOrNull() == "--self-test") { runSelfTest(Path.of(args.getOrElse(1) { "self-test" })); return }
    if (!System.getProperty("os.name").startsWith("Windows")) {
        error("The production application stores credentials with Windows DPAPI and runs on Windows only.")
    }
    val directory = Path.of(System.getenv("LOCALAPPDATA"), "KemiMail")
    Files.createDirectories(directory)
    val channel = FileChannel.open(directory.resolve("instance.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE)
    val lock = channel.tryLock()
    if (lock == null) { channel.close(); JOptionPane.showMessageDialog(null,"KEMI 邮箱已经运行，请切换到已打开的窗口。"); return }
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val koin = startKoin { modules(module {
        single<SecretProtector> { WindowsProtector() }
        single { AccountVault(directory,get()) }
        single<MailGateway> { AngusMailGateway() }
        single { MailViewModel(get(),get(),scope) }
    }) }.koin
    try {
        application {
            val viewModel = remember { koin.get<MailViewModel>() }
            val state by viewModel.state.collectAsState()
            Window(onCloseRequest = { viewModel.close { exitApplication() } }, title = "KEMI 邮箱 · Windows",
                state = rememberWindowState(width = 1320.dp,height = 820.dp)) {
                window.minimumSize = java.awt.Dimension(1000,640)
                MailScreen(state,viewModel::dispatch)
            }
        }
    } finally { scope.cancel(); lock.release(); channel.close() }
}
