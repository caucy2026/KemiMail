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
    if (args.firstOrNull() == "--update-smoke") { runUpdateSmoke(Path.of(args[1]),false); return }
    if (args.firstOrNull() == "--update-smoke-result") { runUpdateSmoke(Path.of(args[1]),true,Path.of(args[3])); return }
    if (args.firstOrNull() == "--render-preview") { renderPreview(Path.of(args[1])); return }
    if (args.firstOrNull() == "--self-test") { runSelfTest(Path.of(args.getOrElse(1) { "self-test" })); return }
    if (!System.getProperty("os.name").startsWith("Windows")) {
        error("The production application stores credentials with Windows DPAPI and runs on Windows only.")
    }
    // Keep the existing data directory and lock so upgrades preserve accounts/drafts and exclude old instances.
    JOptionPane.getRootFrame().iconImage = Thread.currentThread().contextClassLoader.getResourceAsStream("kemi-mail.png")
        ?.use { javax.imageio.ImageIO.read(it) }
    val directory = Path.of(System.getenv("LOCALAPPDATA"), "KemiMail")
    Files.createDirectories(directory)
    val channel = FileChannel.open(directory.resolve("instance.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE)
    val lock = channel.tryLock()
    if (lock == null) { channel.close(); JOptionPane.showMessageDialog(null,"KEMI邮箱已经运行，请切换到已打开的窗口。","KEMI邮箱",JOptionPane.INFORMATION_MESSAGE); return }
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val koin = startKoin { modules(module {
        single<SecretProtector> { WindowsProtector() }
        single { AccountVault(directory,get()) }
        single<MailGateway> { AngusMailGateway() }
        single { MailViewModel(get(),get(),scope) }
        single { KemiUpdateClient() }
        single { UpdateInstaller(directory) }
        single { UpdateViewModel(get(),get(),scope) }
    }) }.koin
    try {
        application {
            val viewModel = remember { koin.get<MailViewModel>() }
            val state by viewModel.state.collectAsState()
            val updateController = remember { koin.get<UpdateViewModel>() }
            val updateState by updateController.state.collectAsState()
            LaunchedEffect(Unit) { delay(5000); updateController.check(manual = false) }
            LaunchedEffect(state.storageReady) {
                if (state.storageReady && args.firstOrNull() == "--updated" && args.size == 2) {
                    withContext(Dispatchers.IO) { writeUpdateReceipt(Path.of(args[1]),directory) }
                }
            }
            Window(onCloseRequest = { viewModel.close { exitApplication() } }, title = "KEMI邮箱", icon = com.kemi.windows.designsystem.kemiMailPainter(),
                state = rememberWindowState(width = 1320.dp,height = 820.dp)) {
                window.minimumSize = java.awt.Dimension(1000,640)
                MailScreen(state,viewModel::dispatch) { updateController.check() }
                UpdateDialog(updateState,state.busy,updateController) {
                    viewModel.close { updateController.install { exitApplication() } }
                }
            }
        }
    } finally { scope.cancel(); lock.release(); channel.close() }
}
