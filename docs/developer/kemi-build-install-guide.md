# KEMI Mail 构建、安装与验收指南

本文档说明如何从本仓库构建 KEMI Mail Android APK、安装到双屏平板、验证运行状态，以及如何准备正式签名发布包。
适用范围仅为 `app-k9mail` 的 `kemi` product flavor；凭据中继服务有独立的部署流程，不包含在本文档中。

## 1. 构建类型与选择

项目提供三个标准构建入口。交付 APK 时应使用这些入口，不要把 `assemble*` 产生的中间产物当作标准交付物。

| 用途 | Gradle 任务 | application ID | 签名 | 是否可覆盖正式版 |
| --- | --- | --- | --- | --- |
| 日常功能测试 | `:app-k9mail:kemiDebugApk` | `com.fsck.k9.debug` | Android Debug key | 否，可与正式版并存 |
| 启动性能测试 | `:app-k9mail:kemiPerformanceApk` | `com.fsck.k9.debug` | Android Debug key | 否，可覆盖已有 Debug 版 |
| 正式发布 | `:app-k9mail:kemiReleaseApk` | `com.fsck.k9` | KEMI 正式签名 | 是，但版本号和签名必须匹配要求 |

对应的标准产物为：

```text
app-k9mail/build/outputs/kemi/debug/KEMI-Mail-v<version>-debug.apk
app-k9mail/build/outputs/kemi/performance/KEMI-Mail-v<version>-performance-debug.apk
app-k9mail/build/outputs/kemi/release/KEMI-Mail-v<version>.apk
```

Debug APK 文件名必须保留 `-debug` 后缀，不得改名伪装成正式包。

## 2. 环境准备

### 2.1 必需软件

- JDK 21；
- Android SDK、Platform Tools 和 Build Tools；
- Git；
- 仓库自带的 Gradle Wrapper，不需要单独安装 Gradle。

先进入仓库根目录：

```shell
cd /path/to/kimi-email
```

配置当前终端使用的 JDK 和 Android SDK。路径应按实际安装位置填写：

```shell
export JAVA_HOME=/path/to/jdk-21
export ANDROID_HOME=/path/to/Android/sdk
export PATH="$ANDROID_HOME/platform-tools:$PATH"
```

本项目应优先固定使用 JDK 21。使用更高版本 JDK 可能触发 Android Gradle Plugin 或 `jlink` 兼容问题。

### 2.2 环境检查

```shell
java -version
./gradlew --version
adb version
adb devices -l
```

检查结果应满足：

- `java -version` 和 Gradle JVM 都显示 JDK 21；
- `adb devices -l` 能看到目标平板，状态为 `device`；
- 若状态为 `unauthorized`，应解锁平板并接受 USB 调试授权；
- 若连接多台设备，后续所有 ADB 命令都必须使用 `-s <serial>` 指定目标。

## 3. 构建前检查

### 3.1 确认源码状态

```shell
git status --short
git branch --show-current
git rev-parse HEAD
```

构建记录中应保存分支名和 commit SHA。若工作区不干净，还应记录未提交修改；正式发布原则上只能从已审核、可复现的提交构建。

### 3.2 确认版本号

KEMI 版本号定义在 `app-k9mail/build.gradle.kts`：

```kotlin
val kemiVersionCode = 39040
val kemiVersionName = "20.1"
```

其中：

- `kemiVersionCode` 是 Android 用于判断升级顺序的整数；
- `kemiVersionName` 是界面展示的版本名；
- 正式升级包的 `versionCode` 必须严格高于平板已安装版本；
- 正式发布的新版本号必须写入源码并提交，确保以后可以从 Git 重建；
- 不得使用 `adb install -d` 强制降级正式邮箱应用，以免数据库或账号数据不兼容。

查看平板已安装正式版版本：

```shell
adb -s <serial> shell dumpsys package com.fsck.k9 \
  | grep -E 'versionCode=|versionName=|lastUpdateTime='
```

## 4. 代码验证

### 4.1 常规 KEMI Debug 验证

```shell
./gradlew \
  :app-k9mail:testKemiDebugUnitTest \
  :app-k9mail:lintKemiDebug \
  detekt \
  spotlessCheck
```

跨模块修改还应运行受影响模块的测试和检查。例如，账号设置功能发生修改时可运行：

```shell
./gradlew \
  :feature:account:setup:testDebugUnitTest \
  :feature:account:setup:lintDebug \
  :feature:account:setup:detekt \
  :feature:account:setup:spotlessCheck \
  :core:common:spotlessCheck \
  :app-k9mail:spotlessCheck
```

### 4.2 格式修复

若 `spotlessCheck` 失败，可运行：

```shell
./gradlew spotlessApply
```

之后必须检查 `git diff`，确认格式化没有改动无关文件，再重新运行测试和 `spotlessCheck`。

### 4.3 真机测试限制

`connectedAndroidTest` 需要已连接的平板或模拟器：

```shell
./gradlew connectedAndroidTest
```

无法执行时，应在构建或发布记录中写明原因。涉及双屏布局、扫码或局域网通信的功能不能只依赖单元测试，必须补充真机验收。

## 5. 构建 Debug 测试包

### 5.1 执行标准构建

```shell
./gradlew :app-k9mail:kemiDebugApk
```

标准产物：

```text
app-k9mail/build/outputs/kemi/debug/KEMI-Mail-v<version>-debug.apk
```

Gradle 内部也会生成：

```text
app-k9mail/build/outputs/apk/kemi/debug/app-k9mail-kemi-debug.apk
```

日常交付和存档优先使用前一个经过标准任务重命名的 APK。

### 5.2 检查 APK 元数据与签名

将 Build Tools 路径调整为本机实际版本：

```shell
APK_PATH="app-k9mail/build/outputs/kemi/debug/KEMI-Mail-v<version>-debug.apk"
BUILD_TOOLS_DIR="$ANDROID_HOME/build-tools/36.0.0"

"$BUILD_TOOLS_DIR/aapt" dump badging "$APK_PATH" | head -1
"$BUILD_TOOLS_DIR/apksigner" verify --print-certs "$APK_PATH"
shasum -a 256 "$APK_PATH"
```

Linux 可以用 `sha256sum` 代替 `shasum -a 256`。Debug 包应显示：

- package 为 `com.fsck.k9.debug`；
- 版本号与 `app-k9mail/build.gradle.kts` 一致；
- APK 验签成功；
- SHA-256 被记录到本次测试或交付记录。

## 6. 安装到双屏平板

### 6.1 确认设备

```shell
adb devices -l
```

以下命令中的 `<serial>` 应替换为目标平板序列号，`<version>` 应替换为实际版本：

```shell
DEVICE_SERIAL="<serial>"
APK_PATH="app-k9mail/build/outputs/kemi/debug/KEMI-Mail-v<version>-debug.apk"
```

### 6.2 安装或更新 Debug 版

```shell
adb -s "$DEVICE_SERIAL" install -r "$APK_PATH"
```

成功时输出包含 `Success`。`-r` 表示覆盖已有的 `com.fsck.k9.debug` 并保留其应用数据。

Debug 版和正式版使用不同 application ID，因此：

- Debug 版不会覆盖正式版 `com.fsck.k9`；
- 两个应用图标可以同时存在；
- Debug 版的账号和配置与正式版相互独立；
- 不要为了安装测试包而卸载正式版。

### 6.3 启动应用

```shell
adb -s "$DEVICE_SERIAL" shell am force-stop com.fsck.k9.debug
adb -s "$DEVICE_SERIAL" shell am start -W \
  -n com.fsck.k9.debug/net.thunderbird.app.common.MainActivity
```

### 6.4 验证安装和前台状态

```shell
adb -s "$DEVICE_SERIAL" shell dumpsys package com.fsck.k9.debug \
  | grep -E 'versionCode=|versionName=|firstInstallTime=|lastUpdateTime='

adb -s "$DEVICE_SERIAL" shell pidof com.fsck.k9.debug

adb -s "$DEVICE_SERIAL" shell dumpsys activity activities \
  | grep -E 'mResumedActivity|topResumedActivity|com.fsck.k9.debug'
```

只有同时满足以下条件，才能认为部署成功：

- ADB 安装返回 `Success`；
- `dumpsys package` 显示预期版本；
- `pidof` 返回进程 ID；
- 前台 Activity 属于 `com.fsck.k9.debug`；
- 平板界面可以正常操作。

## 7. 局域网扫码功能验收

测试 KEMI 邮箱授权码局域网直连时：

1. 手机和平板连接同一个可信 Wi-Fi；
2. 路由器不能启用访客网络隔离、AP isolation 或客户端隔离；
3. 在 Debug 版中进入添加邮箱流程；
4. 选择“在手机上输入邮箱授权码”；
5. 手机使用系统相机或浏览器扫码，无需安装 App 或证书；
6. 手机页面必须显示“未加密的局域网 HTTP”安全提示；
7. 只输入邮箱服务商生成的专用授权码，不能输入邮箱主密码；
8. 提交后平板应自动收到授权码并继续登录；
9. 同一个二维码再次提交应失败；
10. 二维码超过五分钟、用户取消或离开流程后应失效。

若手机打不开页面，依次检查：

- 两台设备是否确实位于同一 IPv4 局域网；
- 平板 Wi-Fi 是否已断开或切换；
- 路由器是否禁止设备间互访；
- 二维码是否已超过五分钟；
- KEMI Mail 是否仍停留在扫码等待页面。

## 8. 构建正式发布包

### 8.1 正式签名配置

正式包需要仓库根目录下的：

```text
.signing/k9.release.signing.properties
```

属性格式如下，值必须从批准的秘密存储中取得：

```properties
k9.release.storeFile=/absolute/path/to/kemi-release.keystore
k9.release.storePassword=<secret>
k9.release.keyAlias=<alias>
k9.release.keyPassword=<secret>
```

`.signing/` 已被 Git 忽略。签名属性、密码和 keystore 不得提交到 Git，也不得复制到构建日志、文档或聊天记录。

### 8.2 构建命令

```shell
./gradlew :app-k9mail:kemiReleaseApk
```

标准产物：

```text
app-k9mail/build/outputs/kemi/release/KEMI-Mail-v<version>.apk
```

若缺少正式签名配置，任务应失败并提示使用 Debug 构建。不得使用 Debug 签名包代替正式发布包。

### 8.3 发布前核验

```shell
APK_PATH="app-k9mail/build/outputs/kemi/release/KEMI-Mail-v<version>.apk"
BUILD_TOOLS_DIR="$ANDROID_HOME/build-tools/36.0.0"

"$BUILD_TOOLS_DIR/aapt" dump badging "$APK_PATH" | head -1
"$BUILD_TOOLS_DIR/apksigner" verify --print-certs "$APK_PATH"
shasum -a 256 "$APK_PATH"
```

必须确认：

- package 为 `com.fsck.k9`；
- `versionCode` 严格高于已安装和已发布版本；
- `versionName` 与发布说明一致；
- 签名证书 SHA-256 与已安装正式版和历史正式包一致；
- APK SHA-256 已存入发布证据；
- 所有安全门禁、回归矩阵和真机验收已经完成。

正式发布还应遵守：

- [KEMI Android 自升级实施与运维手册](./kemi-android-self-update-guide.md)
- [KEMI Security Release Gates](../release/kemi-security-release-gates.md)
- [KEMI Regression Matrix](../release/kemi-regression-matrix.md)
- [KEMI Release Evidence Template](../release/kemi-release-evidence-template.md)

### 8.4 正式版覆盖安装验证

只有签名一致且版本号更高时才能执行：

```shell
adb -s "$DEVICE_SERIAL" install -r "$APK_PATH"
```

禁止使用以下方式绕过错误：

- 不得卸载正式版后重装，因为可能清除邮箱账号和本地邮件；
- 不得使用 `adb install -d` 强制降级；
- 不得使用不同签名重新安装相同 application ID；
- 不得清除应用数据来掩盖数据库升级问题。

## 9. 常见故障

### `Unable to locate a Java Runtime`

原因：终端没有找到 JDK，或 `JAVA_HOME` 未设置。

处理：明确设置 JDK 21 后重新运行 `java -version` 和 `./gradlew --version`。

### Android SDK location not found

原因：`ANDROID_HOME` 未设置，且仓库没有有效的 `local.properties`。

处理：设置 `ANDROID_HOME`，或在不包含秘密的本地 `local.properties` 中配置 `sdk.dir`。

### `INSTALL_FAILED_UPDATE_INCOMPATIBLE`

原因：待安装 APK 与已安装应用包名相同，但签名不同。

处理：停止安装并核对 APK 类型和签名。不要卸载正式邮箱应用。日常测试应安装 `com.fsck.k9.debug`。

### `INSTALL_FAILED_VERSION_DOWNGRADE`

原因：APK 的 `versionCode` 低于已安装版本。

处理：正式升级应正确提高源码中的版本号并重新构建；不要对正式版使用 `-d`。若只是功能测试，使用可并行安装的 Debug 包。

### `more than one device/emulator`

原因：ADB 同时检测到多个目标。

处理：运行 `adb devices -l`，并为每条命令增加 `-s <serial>`。

### 安装成功但应用未打开

处理：使用本文第 6.3 节的显式 Component 启动命令，并用 `dumpsys activity activities` 检查前台 Activity。

### Gradle 或 `jlink` 与 Java 版本不兼容

处理：切换回 JDK 21，停止旧 Gradle daemon 后重试：

```shell
./gradlew --stop
./gradlew :app-k9mail:kemiDebugApk
```

通常不需要执行 `clean`。只有确认增量构建缓存异常时才运行最小范围清理，避免无谓延长构建时间。

## 10. 构建归档记录模板

每次向测试设备或发布渠道交付 APK 时，建议保存以下信息：

```text
构建时间：
构建人员：
Git 分支：
Git commit SHA：
工作区是否干净：
KEMI versionName / versionCode：
构建任务：
验证任务及结果：
APK 文件名：
APK application ID：
APK SHA-256：
签名证书 SHA-256（正式包必填）：
目标设备型号：
目标设备序列号（对外记录应脱敏）：
安装结果：
真机验收结果：
未执行项目及原因：
```

APK、测试报告和发布证据应存入批准的制品系统，不要提交到 Git。源码、版本号和构建文档应通过正常代码审查提交。
