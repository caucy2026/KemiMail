# KEMI邮箱 Windows 版

普通单屏 Windows 邮箱客户端，独立位于 `windows` 分支。使用 Kotlin、Compose Desktop、Koin 与协程 StateFlow，桌面设计组件复用仓库既有 K-9 主题颜色。Windows 专属入口在本目录单独构建，不加载 Android Gradle 插件，也不改动 Android 模块。

## Windows 自升级

当前正式版 **1.4.2 / 10402**，KEMI 后台应用 ID **64**，Windows x64，已设置仅自升级不展示、非强制更新。下载大小为 68,502,016 字节，SHA-256 为 `0f692b9053681c51b2a1f33045badf39b6c112bcee8303e6e6e33df83d7db0c1`。安装包源码提交为 `c2e475639c1368827fd7c359f342414f2f68b7a5`。

2026-09-08 验收：Mac/Windows 各 42 项测试通过；公开接口旧版本有更新、同版本及更高版本无更新，CDN 完整下载大小/哈希与构建产物一致；Windows 已安装 1.4.0 经真实接口自动升级到 1.4.2，合成 DPAPI 账号/草稿恢复正常、原有用户加密数据哈希不变、下载的 EXE 已清理。此验收不连接真实邮箱，不代表重新验证了真实收发业务。安装器未签名；安装事务故障回滚和启动后异常恢复未做故障注入验收。

1.4.0 起接入 KEMI 公开 Windows 更新接口。状态栏提供“检查更新”，启动后后台检查；下载后校验准确大小及 SHA-256，用户点击“安装并重启”时先保存草稿。网络失败不会影响邮箱功能。逻辑包名为 `com.fsck.k9`，必须传 `os=windows`，与 Android 应用独立发布。

版本以 `desktop-windows/version.properties` 为唯一来源。后台发布选择“仅自升级（不展示）”、非强制更新；Windows x64 安装包由 `desktop-windows/scripts/build-release.ps1` 原生构建，输出独立 `dist/<版本>/` 目录和 `release.json`。禁止复用已发布整数版本覆盖不同文件。

jpackage 升级 UUID 固定为 `a61e8dc4-bfa7-3458-89f8-669238465324`，与 JDK 24 对 KEMI/KEMI邮箱生成的原有标识一致。升级器在暂存目录独立运行，等待旧进程退出后调用 EXE/MSI 安装，保留当前安装目录；账号和草稿继续位于 `%LOCALAPPDATA%\KemiMail`。Windows Installer 提供安装事务失败回滚；启动后的业务故障不承诺自动回滚，失败会保留升级结果、日志和安装包供恢复。

安装成功后重新启动程序，检查新版本启动回执；成功会清理下载的安装包。升级记录保存在数据目录的 `updates/<随机标识>/result.txt`。首次从 1.3.x 使用升级功能，需要先安装一次含更新能力的版本。

`--update-smoke <全新临时目录>` 为显式有网络验收入口：仅创建合成 DPAPI 账号和草稿，调用真实公开接口下载更高版本，启动同一升级器，升级后检查合成数据保留及无更新。不会连接邮箱服务器，也不会使用真实账号数据。该入口会实际安装，必须在已授权的测试安装位置运行。

## 1.3.1 连接兼容修复

修复 1.3.0 将 IMAP ID 客户端标识本地化为中文后，部分服务器认证成功却拒绝后续 ID 命令、无法加载文件夹的问题。协议标识保持 ASCII `KemiMail`，界面和安装器仍显示“KEMI邮箱”。原有账号及密码不需要重新设置。

## 1.3.0 修复

- 程序、标题栏、任务栏、安装器及应用内统一为“KEMI邮箱”，使用 Android 同源启动图标。内部数据目录仍保留 `%LOCALAPPDATA%\KemiMail`，兼容原有账号、草稿与单实例锁。
- 安装运行时补齐 `jdk.charsets`，支持 GB2312、GBK、GB18030、Big5；附件名先进行 MIME 解码再做文件名安全处理。不支持的显式编码会提示错误，不再静默当成 UTF-8。
- IMAP 连接复用，读取分块由默认 16 KB 调整为 256 KB。正文读取只获取附件元数据，点击保存时才下载附件；仍使用 PEEK，不因阅读自动标记已读。
- 已打开邮件仅在内存缓存，最多 40 封 / 16 MiB，5 分钟过期；按账号、文件夹、UIDVALIDITY、UID 隔离，刷新、账号修改、移除和删除后失效。所有发送和修改操作不自动重试。
- 打包后自检覆盖中文字符集、GB2312 编码附件名、图标资源，避免仅完整 JDK 上的单元测试通过而裁剪后的运行时缺模块。

## 1.2.0 界面更新

新增阿里企业邮箱预设，使用与 Android 相同的 IMAP `imap.qiye.aliyun.com:993`、SMTP `smtp.qiye.aliyun.com:465`（TLS），支持企业自有邮箱域名，用户名为完整邮箱地址。添加账号时点击“阿里企业邮箱”，填写邮箱地址与客户端安全密码，再验证并保存。已有账号不会自动修改。参数依据[阿里官方说明](https://help.aliyun.com/zh/document_detail/36576.html)，需管理员开放 IMAP 和第三方客户端登录权限。

邮箱预设采用双列卡片和明确选中状态；邮件列表增加“全部 / 未读 / 星标”分段筛选，可与搜索组合使用，仅筛选已加载邮件。

采用轻量桌面邮件风格：浅灰侧栏、柔和选中态、统一线性图标工具栏、分层邮件列表和宽松阅读区；账号设置与撰写窗口使用同一组紧凑表单组件。图标操作提供中文悬停提示和无障碍名称。保留 Windows 原生窗口控制与原有账号、草稿格式。

通过 `:app:renderPreview` 生成收件箱、空状态、账号设置、撰写窗口、1000×640 小窗口和 150% 缩放截图。预览仅使用合成数据，正式启动不注入演示账号或邮件。当前 Windows 安装包版本见上方自升级发布记录。

## 功能

- 多账号 IMAP/SMTP，阿里企业邮箱/QQ/163/126 配置快捷填充和自定义服务器。
- 强制 TLS 或 STARTTLS、证书与主机名验证，账号连接测试不会发送邮件。
- 文件夹、最新邮件列表、分批加载至 2000 封、已加载主题和发件人搜索。
- 文本阅读、Unicode MIME、附件保存、已读/未读、星标、安全移动至已删除。
- 撰写、抄送、回复、附件发送；发送前人工确认。
- Windows 当前用户 DPAPI 加密账号和本地草稿；单实例防止并发覆盖。

首版使用邮箱密码或服务商授权码登录，不提供 OAuth、Exchange 专有协议、POP3、离线邮件缓存、后台自动收信或 HTML 网页渲染。仅支持 Windows 10/11 x64。不同收发服务器需要不同登录凭证的账号暂不支持。邮件阅读不会自动标为已读，可点击标记按钮。文件夹列表取前 500 个。单封接收邮件与发送附件总大小上限为 20 MB，发送实际限制仍取决于服务商。

正文与附件在进程内存中处理，不自动落盘；附件只保存到用户选择的位置且不会自动打开。账号和草稿位于 `%LOCALAPPDATA%\KemiMail`，不能直接复制给其他 Windows 用户使用。草稿包含附件文件路径，恢复时必须保证原附件仍在。请通过“保存草稿”或正常关闭撰写窗口保存；强制结束进程可能丢失尚未保存的编辑。

已发送副本会尝试写入服务器 Sent 文件夹；若服务商本身自动存副本，可能出现两份。副本保存失败不会重发邮件。SMTP 结果不确定时先检查服务商网页版，避免重复发送。删除仅使用服务器 MOVE 能力移入 Trash，不提供永久删除或全文件夹 expunge。

## 构建

使用仓库根目录已有 Gradle 9.5 wrapper，JDK 21 或更高版本，源码字节码目标为 Java 21。

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-24'
.\gradlew.bat -p desktop-windows :app:test :app:createDistributable --console=plain
# 如已安装 WiX，可额外生成安装 EXE：
.\gradlew.bat -p desktop-windows :app:packageExe --console=plain
```

应用目录为 `desktop-windows/app/build/compose/binaries/1.4.2/main/app/KEMI邮箱`，直接运行其中的 `KEMI邮箱.exe`。整个目录包含运行环境，不能只复制 EXE。没有 Java 的电脑也可以运行完整应用目录。安装包和应用目录目前均未进行 Authenticode 签名。

`--self-test <temporary-directory>` 是显式无网络诊断入口，使用合成账号验证 Windows DPAPI、草稿、MIME 和打包运行环境，不读取用户真实数据。

macOS 可以运行单元测试：

```shell
JAVA_HOME=/path/to/jdk21 ./gradlew -p desktop-windows :app:test
```

依赖按平台锁定在 `gradle-macos.lockfile` 与 `gradle-windows.lockfile`。仅在审查新增依赖后使用 `--write-locks`；Mac 可加 `-PwindowsTarget` 生成 Windows 依赖锁。邮件协议使用 Eclipse Angus，Windows 加密 API 使用 JNA，HTML 转纯文本使用 jsoup，不自研协议或密码算法。GreenMail 仅属于测试依赖，不能进入应用运行时。

## 分支同步与验证

Mac 源码目录为 `KemiMail-Windows` worktree，Windows 工作目录为 `E:\KemiMail`，分支均为 `windows`。源码通过已审查提交交换，在 Windows 原生测试和构建。

```shell
git -c push.followTags=false push github refs/heads/windows:refs/heads/windows
```

不要将 GitLab `dev` 历史合并或推到公开分支，也不要使用 `--all`、`--mirror` 或跟随标签推送。

用户验收：打开程序；添加已启用 IMAP/SMTP 的测试邮箱；刷新并阅读；保存附件；测试已读与星标；撰写给自己并确认发送；关闭重开验证账号和草稿恢复。开发测试使用合成数据，不代表真实服务商和用户桌面交互已经验收。
