package com.kemi.windows

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Density
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.ExperimentalComposeUiApi
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

@OptIn(ExperimentalComposeUiApi::class)
internal fun renderPreview(directory: Path) {
    // Compose measurement and its effects must share the UI thread, including offscreen verification.
    if (!javax.swing.SwingUtilities.isEventDispatchThread()) {
        javax.swing.SwingUtilities.invokeAndWait { renderPreview(directory) }
        return
    }
    Files.createDirectories(directory)
    val a = Account(name = "演示邮箱", email = "demo@example.invalid")
    val inbox = MailFolder("INBOX","收件箱")
    val summary = MailSummary(1,1,"欢迎使用 KEMI邮箱","KEMI 产品团队",Instant.parse("2026-09-07T02:00:00Z"),false,true)
    val sample = MailState(accounts = listOf(a),account = a,folders = listOf(inbox,MailFolder("Sent","已发送",sent = true),MailFolder("Trash","已删除",trash = true)),
        folder = inbox,messages = listOf(summary,summary.copy(uid = 2,subject = "本周工作安排",sender = "项目协作组",starred = false),
            summary.copy(uid = 3,subject = "设计评审反馈与下一步安排",sender = "设计团队",starred = false,seen = true),
            summary.copy(uid = 4,subject = "会议纪要 · 产品沟通",sender = "周晓",starred = false,seen = true),
            summary.copy(uid = 5,subject = "您的文件已准备就绪",sender = "文档协作",starred = false,seen = true)),
        detail = MailDetail(summary,"demo@example.invalid","team@example.invalid",null,
            "这是专用于界面验证的合成邮件。\n\n在普通 Windows 屏幕上，左侧选择账号和文件夹，中间浏览邮件，右侧阅读完整内容。\n\n你可以撰写、回复、保存附件，也可以给邮件添加星标。账号与草稿由当前 Windows 用户加密保存。\n\n真实程序首次启动不会出现这些演示邮件。",emptyList()),
        storageReady = true,status = "界面验证 · 合成数据 · 未连接邮箱服务器")
    fun render(name: String,width: Int,height: Int,density: Float = 1f,content: @Composable () -> Unit) {
        val scene = ImageComposeScene(width = width,height = height,density = Density(density),content = content)
        try {
            scene.render().close()
            scene.render().use { image -> image.encodeToData()?.use { Files.write(directory.resolve("$name.png"),it.bytes) } ?: error("Render failed") }
        } finally { scene.close() }
    }
    render("inbox",1320,820) { MailScreen(sample,{}) {} }
    render("compact",1000,640) { MailScreen(sample,{}) {} }
    render("scaled-150",1980,1230,1.5f) { MailScreen(sample,{}) {} }
    render("welcome",1320,820) { MailScreen(MailState(storageReady = true,status = "欢迎使用 KEMI邮箱，请添加账号"),{}) {} }
    render("account",700,760) { AccountForm(sample.copy(editingAccount = MailProvider.ALIBABA.applyTo(a)),{}) }
    render("account-servers",700,760) { AccountForm(sample.copy(editingAccount = MailProvider.ALIBABA.applyTo(a)),{},androidx.compose.foundation.ScrollState(Int.MAX_VALUE)) }
    render("compose",850,760) { ComposeForm(sample.copy(draftAccount = a,draft = ComposeDraft(to = "team@example.invalid",
        subject = "关于本周的工作安排",body = "你好，\n\n本周的工作安排已整理完成，请查阅并告知你的建议。\n\n谢谢！"))) {} }
}
