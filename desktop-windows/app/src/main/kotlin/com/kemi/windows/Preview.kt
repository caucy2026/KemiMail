package com.kemi.windows

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.ExperimentalComposeUiApi
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

@OptIn(ExperimentalComposeUiApi::class)
internal fun renderPreview(directory: Path) {
    Files.createDirectories(directory)
    val a = Account(name = "演示邮箱", email = "demo@example.invalid")
    val inbox = MailFolder("INBOX","收件箱")
    val summary = MailSummary(1,1,"欢迎使用 KEMI Windows 邮箱","KEMI 产品团队",Instant.parse("2026-09-07T02:00:00Z"),false,true)
    val sample = MailState(accounts = listOf(a),account = a,folders = listOf(inbox,MailFolder("Sent","已发送"),MailFolder("Trash","已删除")),
        folder = inbox,messages = listOf(summary,summary.copy(uid = 2,subject = "本周工作安排",sender = "项目协作组",starred = false)),
        detail = MailDetail(summary,"demo@example.invalid","team@example.invalid",null,
            "这是专用于界面验证的合成邮件。\n\n在普通 Windows 屏幕上，左侧选择账号和文件夹，中间浏览邮件，右侧阅读完整内容。\n\n你可以撰写、回复、保存附件，也可以给邮件添加星标。账号与草稿由当前 Windows 用户加密保存。\n\n真实程序首次启动不会出现这些演示邮件。",emptyList()),
        storageReady = true,status = "界面验证 · 合成数据 · 未连接邮箱服务器")
    for ((name,state) in listOf("inbox" to sample,"welcome" to MailState(storageReady = true,status = "欢迎使用 KEMI 邮箱，请添加账号"))) {
        val scene = ImageComposeScene(width = 1320,height = 820) { MailScreen(state) {} }
        try {
            scene.render().close()
            scene.render().use { image -> image.encodeToData()?.use { Files.write(directory.resolve("$name.png"),it.bytes) } ?: error("Render failed") }
        } finally { scene.close() }
    }
}
