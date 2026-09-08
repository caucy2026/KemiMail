package com.kemi.windows

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

internal class UpdateInstaller(private val dataDirectory: Path) {
    fun newDirectory(): Path = Files.createDirectories(dataDirectory.resolve("updates").resolve(UUID.randomUUID().toString()))

    fun launch(update: MailUpdate, installer: Path, smokeDirectory: Path? = null) {
        val executable = System.getProperty("jpackage.app-path")?.let(Path::of)
            ?: throw UpdateFailure("请从已安装的 KEMI邮箱程序执行更新")
        requireUpdate(Files.isRegularFile(executable) && Files.isDirectory(executable.parent.resolve("app")) &&
            Files.isDirectory(executable.parent.resolve("runtime")),"无法确认当前安装目录")
        val directory = installer.parent
        val script = directory.resolve("install.ps1")
        UpdateInstaller::class.java.getResourceAsStream("/update-runner.ps1")!!.use { Files.copy(it,script) }
        val manifest = buildJsonObject {
            put("installer",installer.toAbsolutePath().toString()); put("target",executable.toString())
            put("pid",ProcessHandle.current().pid()); put("version",update.name)
            put("sha256",update.sha256); put("size",update.size)
            put("receipt",directory.resolve("started.txt").toString())
            put("smoke",smokeDirectory?.toString().orEmpty())
        }
        val manifestPath = directory.resolve("install.json")
        Files.writeString(manifestPath,manifest.toString())
        val powershell = Path.of(System.getenv("SystemRoot"),"System32","WindowsPowerShell","v1.0","powershell.exe")
        val process = ProcessBuilder(powershell.toString(),"-NoProfile","-NonInteractive","-WindowStyle","Hidden",
            "-ExecutionPolicy","Bypass","-File",script.toString(),"-ManifestPath",manifestPath.toString())
            .redirectOutput(directory.resolve("runner.log").toFile()).redirectErrorStream(true).start()
        // Wait for explicit readiness before closing the application; the runner then waits for this process.
        val ready = directory.resolve("ready.txt")
        val deadline = System.nanoTime() + 10_000_000_000L
        while (!Files.exists(ready) && process.isAlive && System.nanoTime() < deadline) Thread.sleep(50)
        if (!Files.exists(ready)) {
            process.destroy()
            throw UpdateFailure("更新安装器未能启动，当前程序将继续运行")
        }
    }
}

internal fun writeUpdateReceipt(path: Path,dataDirectory: Path) {
    val receipt = path.toAbsolutePath().normalize()
    requireUpdate(receipt.startsWith(dataDirectory.resolve("updates").toAbsolutePath().normalize()) &&
        receipt.fileName.toString() == "started.txt" && Files.isDirectory(receipt.parent),"升级回执路径无效")
    Files.writeString(receipt,MailRelease.name)
}

/** Explicit acceptance mode: only synthetic DPAPI data in a fresh caller-selected directory, no UI or mailbox access. */
internal fun runUpdateSmoke(directory: Path,finish: Boolean,receipt: Path? = null) = runBlocking {
    val vault = AccountVault(directory,WindowsProtector())
    if (!finish) {
        require(!Files.exists(directory)) { "Upgrade smoke test needs a fresh directory" }
        Files.createDirectories(directory)
        val account = Account(id = "17c790c0-a6d7-4b94-a9f8-421860fbfc58",name = "Upgrade fixture",
            email = "fixture@example.invalid",username = "fixture@example.invalid",password = "synthetic-upgrade-fixture",
            imapHost = "imap.example.invalid",smtpHost = "smtp.example.invalid")
        vault.save(listOf(account)); vault.saveDraft(account.id,ComposeDraft(subject = "Upgrade fixture",body = "Preserve draft"))
        val update = KemiUpdateClient().check() ?: error("No higher published Windows version")
        val installer = UpdateInstaller(directory)
        val downloaded = KemiUpdateClient().download(update,installer.newDirectory()) {}
        installer.launch(update,downloaded,directory)
    } else {
        val account = vault.load().single()
        check(account.password == "synthetic-upgrade-fixture")
        check(vault.loadDraft(account.id).body == "Preserve draft")
        check(KemiUpdateClient().check() == null)
        checkNotNull(receipt); writeUpdateReceipt(receipt,directory)
        Files.writeString(directory.resolve("upgrade-result.txt"),"PASS version=${MailRelease.name}; account/draft preserved; current version has no update")
    }
}
