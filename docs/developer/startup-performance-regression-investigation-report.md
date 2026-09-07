# Android 邮箱启动性能回归调查与后续优化计划

本文记录 KEMI Mail 在 2026-08-20 对“邮件首页启动再次变慢”的复查结果，并整理此前已经完成的优化、
本轮确认的性能基线、没有奏效的实验，以及下一轮继续优化时应遵循的实施顺序和验收标准。

本文是项目内的阶段性工程报告，不替代
[Android 邮箱启动性能优化案例与操作手册](startup-performance-optimization-case-study.md)。前一份文档重点说明
2026-08-17 的完整优化方法和通用经验；本文重点回答三个问题：

1. 前一轮具体优化了什么，相关代码和提交在哪里；
2. 当前版本为什么又显得慢，已经确认到什么程度；
3. 下一轮应从哪里继续，怎样避免再次用不可靠的方案掩盖问题。

## 1. 阶段结论

截至 2026-08-20，本轮结论如下：

- 2026-08-17 的启动优化提交仍然存在于当前分支，关键修改没有被后续提交直接覆盖；
- 当前设备一度安装的是可调试 Debug APK，单次有效冷启动约为 11.5 秒，Debug 数据不能用来评价发布性能；
- 换回 release-like 的 Performance APK 后，9 个有效样本的中位数为 **1,945 ms**，P90 为
  **2,065 ms**；
- 该结果远好于最初的 14 秒级启动，但比前一轮记录的 1,185.5 ms 中位数和 1,284 ms P90 明显退化；
- 当前最值得继续调查的区段仍在 `Application.onCreate()`：消息监听器注册、Koin 首次对象图解析、
  Widget 初始化和通知恢复之间存在启动关键路径竞争；
- 把后台任务放入协程，或在任意 Activity 第一次 `onDraw()` 后立即运行，都不足以保证它们不会影响
  首屏完整显示；
- 本轮尝试的“首次绘制后立即启动 Widget 和通知恢复”方案没有收益，实测中位数反而变为
  **2,175 ms**，因此该实验已经撤回，没有作为修复保留。

当前仓库继续保留 2026-08-17 已验证的历史优化。本轮只新增调查与规划文档，不把未达到验收标准的实验代码
写成已完成优化。

## 2. 代码与提交基线

### 2.1 历史优化提交

前一轮启动专项对应的主要提交为：

```text
1d56632b71 fix: 优化邮箱冷启动性能
```

当前检查时的分支与提交为：

```text
branch: customer
HEAD: 331b2b6883a85ab88bf8f7a2ab3ecb745d017d3a
```

`1d56632b71` 是当前 `HEAD` 的祖先提交。检查 `1d56632b71..HEAD` 后，没有发现后续提交直接撤销
该专项修改过的启动核心文件。后续提交主要集中在双屏 UI、二维码辅助登录和账户设置流程等功能。

这意味着当前现象不能简单归因于“原修复被回滚”。更准确的描述是：历史优化仍然有效，但当前运行构建、
数据状态、首次对象解析顺序或新功能带来的运行时成本使启动重新越过了预警线。

### 2.2 历史提交涉及的文件范围

`1d56632b71` 共修改 20 个文件，主要分为以下几组：

|        方向         |                                   主要文件                                    |                    目的                     |
|-------------------|---------------------------------------------------------------------------|-------------------------------------------|
| Application Trace | `app-common/.../BaseApplication.kt`                                       | 给启动核心阶段增加可比较的 Trace 区段                    |
| Performance 构建    | `app-k9mail/build.gradle.kts`、`app-k9mail/README.md`                      | 增加 release-like、Debug 签名的 Performance APK |
| 启动基准路径            | `BaselineProfileGenerator.kt`、`StartupBenchmark.kt`                       | 保证基准最终进入真实邮件首页                            |
| Startup Profile   | `kemiRelease/.../startup-prof.txt`、`kemiPerformance/.../startup-prof.txt` | 优化启动类的 DEX 布局                             |
| Provider 静态 DI    | 两个 FileProvider 及其测试                                                      | 避免 `Application.onCreate()` 前提前解析 Koin 大图 |
| Legacy 设置路径       | `K9.kt`、`Preferences.kt`                                                  | 缩短设置存储获取路径并修复初始化顺序依赖                      |
| 通知依赖图             | `MessagingController.java`、`NotificationOperations.kt`、相关 Koin module     | 延迟解析通知控制器和策略                              |
| 主题依赖图             | `ThemeManager.kt`、legacy UI Koin module                                   | 只依赖主题真正需要的设置分片                            |
| 调查文档              | 原启动优化案例文档与 `docs/SUMMARY.md`                                              | 固化测量方法、证据和复用清单                            |

## 3. 前一轮已经完成的优化

### 3.1 建立 release-like 的 Performance APK

Debug APK 与发布包在 R8、资源压缩、DEX 布局、Profile、StrictMode 和调试探针方面都有差异。前一轮新增
`performance` build type，继承 release 优化，但继续使用 Debug 签名和 `.debug` application ID。

构建命令为：

```shell
./gradlew :app-k9mail:kemiPerformanceApk
```

输出文件为：

```text
app-k9mail/build/outputs/kemi/performance/KEMI-Mail-v<version>-performance-debug.apk
```

该设计允许覆盖安装并保留真实账户数据，同时避免用 Debug 包的启动时间误判生产体验。本轮再次证明，构建类型
是启动调查的第一项检查，而不是最后才补充的细节。

### 3.2 修正基准测试的真实业务终点

启动入口先经过路由 Activity，真正的业务终点是：

```text
com.fsck.k9.activity.MessageHomeActivity
```

前一轮修正了 Baseline Profile 和 Startup Benchmark，使其不会停留在路由页、权限页或引导页。有效样本必须：

- `am start -W` 返回 `TotalTime`；
- 启动前执行 `force-stop`；
- 最终 resumed Activity 是 `MessageHomeActivity`；
- 没有系统弹窗、权限页、崩溃页或账户配置页干扰。

本轮还发现原案例文档中的手工示例入口已经过期。当前可用的 launcher Activity 是：

```text
com.fsck.k9.debug/net.thunderbird.app.common.MainActivity
```

后续应以清单或 `cmd package resolve-activity` 的实际结果为准，不应复制旧 Activity 名称后直接统计。

### 3.3 给启动关键阶段增加 Trace

`BaseApplication` 和 `K9` 中保留了启动 Trace，包括：

```text
BaseApplication.attachBaseContext
BaseApplication.startKoin
BaseApplication.onCreate
BaseApplication.initializeCore
BaseApplication.initializeK9
BaseApplication.initializeLegacyCore
BaseApplication.initializeAppLanguage
BaseApplication.observeNotificationChannels
BaseApplication.initializeTheme
BaseApplication.initializeMessageListWidget
BaseApplication.registerMessagingListeners
BaseApplication.observeProcessLifecycle
K9.resolveSettingsStorage
K9.loadPreferences
```

这些区段让调查可以区分“容器注册慢”“首次解析对象图慢”“数据库慢”和“Activity 绘制慢”。后续优化不应删除
这些打点，也不应只依赖普通日志的入口、出口时间。

### 3.4 消除 Provider 类加载阶段的静态 DI

Android 会在 `Application.onCreate()` 前安装清单中的 `ContentProvider`。原实现中的
`AttachmentTempFileProvider` 和 `DecryptedFileProvider` 曾在静态字段初始化时执行 `DI.get()`，导致系统仅仅
加载 Provider 类就提前解析大段设置依赖图。

历史修复将依赖获取移动到真正需要时，并加入 `FileProviderClassInitializationTest`，确保在没有启动 Koin 的条件下
也能实例化 Provider。该测试是防止 Activity 前置回归的重要保护，不应删除或绕过。

### 3.5 缩短设置与主题依赖路径

历史 Trace 证明，某些小设置读取会通过大管理器间接创建完整依赖图。对应修复包括：

- `K9` 直接访问已经解析的设置存储，减少间接对象图；
- `Preferences` 显式保证账户初始化顺序，避免惰性化后暴露空状态；
- `ThemeManager` 只依赖 `DisplayCoreSettingsPreferenceManager`，不再为了读取主题解析完整
  `GeneralSettingsManager`。

这一方向仍然正确：启动消费者应依赖它真正需要的最小接口或设置分片。

### 3.6 延迟通知对象图

历史修复将 `MessagingController` 中的 `NotificationController` 和 `NotificationStrategy` 改为惰性解析，
`NotificationOperations` 只在真正执行通知操作时获取控制器；`Core.restoreNotifications()` 也在无账户时立即返回。

这解决了“构造 MessagingController 就创建完整通知链路”的问题。不过在存在真实账户时，通知恢复仍会执行，
仍可能与首页、Widget 或其他 Koin 单例争用数据库和锁。因此它是已完成的第一阶段优化，不代表该路径已经没有
继续优化空间。

### 3.7 扩展 Startup Profile

历史专项把真实启动路径需要的入口、Koin runtime、Koin module、合成 lambda 和启动 Provider 纳入
Startup Profile，并同步到 `kemiRelease` 和 `kemiPerformance`。

Profile 主要改善 DEX 布局，不能替代对象图收窄和 I/O 优化。后续代码增加新的启动类后，应重新验证 Profile 是否
仍覆盖真实路径，但不能使用宽泛通配符无上限扩大规则。

## 4. 本轮复测环境与数据

### 4.1 构建与设备状态

本轮使用同一台已连接的物理设备，保留应用现有账户数据。设备上的包名为：

```text
com.fsck.k9.debug
```

调查开始时安装包具有：

```text
flags=[ DEBUGGABLE HAS_CODE ALLOW_CLEAR_USER_DATA ]
APK size: about 56 MB
```

覆盖安装 Performance APK 后变为：

```text
flags=[ HAS_CODE ALLOW_CLEAR_USER_DATA ]
APK size: about 10 MB
```

Debug APK 的单次有效冷启动约为 11,496 ms。该数据可以解释开发环境中“又变得非常慢”的直接观感，但不能和
历史 release-like 结果做性能回归结论。

### 4.2 当前 Performance 基线

当前 `HEAD` 在任何本轮实验修改之前，Performance APK 共测量 10 次，其中 9 次满足有效样本条件：

```text
1996, 1933, 1906, 2010, 1945, 2010, 1940, invalid, 1937
```

无效样本只返回了 `WaitTime`，没有 `TotalTime`，因此没有用 0、等待值或猜测值代替。

按 9 个有效值计算：

|        指标        | 当前 Performance 基线 |
|------------------|------------------:|
| 有效样本             |            9 / 10 |
| 中位数              |          1,945 ms |
| P90，nearest-rank |          2,065 ms |
| 最小值              |          1,906 ms |
| 最大值              |          2,065 ms |

### 4.3 与历史结果的关系

历史文档记录的优化后结果为：

| 指标  | 2026-08-17 | 2026-08-20 当前基线 |
|-----|-----------:|----------------:|
| 中位数 | 1,185.5 ms |        1,945 ms |
| P90 |   1,284 ms |        2,065 ms |

当前中位数比历史记录高约 64%，P90 高约 61%。这说明当前确有继续优化价值，但两次数据并非严格的逐提交
Macrobenchmark 对照：设备温度、系统编译状态、账户数据库内容和后台系统负载都可能变化。下一轮应先固化自动化
采样，再对具体修改做同一时段的 A/B。

## 5. 本轮 Trace 发现

### 5.1 当前基线的 Application 关键区段

当前 Performance APK 的一份代表性 Trace 显示：

|                      区段                      |           耗时 |
|----------------------------------------------|-------------:|
| `BaseApplication.onCreate`                   | 约 1,278.6 ms |
| `BaseApplication.registerMessagingListeners` | 约 1,126.6 ms |
| 主线程 Koin 单例等待                                |   约 1,113 ms |
| 后台 `initializeMessageListWidget`             |   约 1,120 ms |

该样本中，后台任务正在解析与 Widget、通知和数据库相关的对象，主线程随后解析有交集的 Koin singleton，因而等待
同一个单例锁。锁持有期间还出现 SQLite 工作。这与历史文档强调的规律一致：

```text
放到后台协程 != 不会阻塞主线程
```

只要主后台任务共享 Koin 单例锁、数据库连接池或其他同步资源，后台初始化仍可能直接拉长 Application 阶段。

### 5.2 消息监听器注册仍是首要切入点

`MessagingListenerProvider` 当前只提供 `UnreadWidgetUpdateListener`，但注册过程还需要首次获取
`MessagingController`。一次对象访问会跨过 Widget、控制器、设置和存储等多层依赖，导致 Trace 中看似简单的
`forEach + addListener()` 成为秒级区段。

下一轮不能只把整个区段搬到另一个线程。应先把它拆分成至少三个 Trace：

```text
resolveMessagingListenerProvider
resolveMessagingController
registerMessagingListener
```

如果耗时集中在某个 `get()`，继续沿构造函数依赖逐层细分；如果是锁等待，则必须同时记录 owner 线程正在创建的
具体 singleton 和执行的 I/O。

### 5.3 第一次绘制不等于完整显示

本轮实验把 Widget 初始化与通知恢复移动到“第一个 resumed Activity 的第一次 `onDraw()` 之后”。新版 Trace 显示：

- `initializeMessageListWidget` 在时间点约 `19985.754852` 开始；
- `MessageHomeActivity.reportFullyDrawn()` 在约 `19986.570424` 才发生；
- 两者之间仍有约 816 ms 的首页数据和绘制窗口。

因此，通用的第一次 `onDraw()` 回调太早。它既可能挂在路由 Activity，也可能发生在邮件列表真正准备好之前。
此时立即启动 Koin 和 SQLite 重任务，仍会与 TTFD 和首页可交互阶段竞争。

## 6. 本轮尝试但未保留的方案

### 6.1 只延迟通知恢复

第一步实验将 `Core.restoreNotifications()` 从 `Core.init()` 移到首屏绘制之后。Trace 随后显示，
`registerMessagingListeners` 仍约为 1,185 ms，锁竞争的主要持有者变成或暴露为
`initializeMessageListWidget`。

结论：只移动通知恢复不能解决共享对象图的首次解析竞争。

### 6.2 同时延迟 Widget 与通知恢复

第二步实验在 Application 中注册第一个 Activity 的绘制监听器，并在第一次绘制后启动 Widget 初始化和通知恢复。
10 次有效样本为：

```text
2155, 2192, 2201, 2161, 2208, 2189, 2158, 2190, 2130, 2134
```

统计结果：

|        指标        |     实验结果 |
|------------------|---------:|
| 中位数              | 2,175 ms |
| P90，nearest-rank | 2,201 ms |
| 最小值              | 2,130 ms |
| 最大值              | 2,208 ms |

该方案没有通过性能验收，而且会把非关键工作放入首绘与 `reportFullyDrawn()` 之间。相关代码已经撤回。

### 6.3 实验带来的工程结论

本轮失败实验仍提供了三个有用结论：

1. 不能把“协程”“后台线程”或“首帧后”当作性能保证；
2. 首页存在“第一次 View 绘制”和“邮件列表真正 ready 并完成绘制”两个不同里程碑；
3. 应先收窄依赖图并消除锁竞争，再决定哪些工作需要延后及如何排序。

## 7. 下一轮优化计划

### 7.1 P0：固化可重复基线

在继续改代码前，先增加或整理一个本地启动测量脚本，至少完成以下检查：

1. 构建 `kemiPerformanceApk`；
2. 覆盖安装并校验包不包含 `DEBUGGABLE`；
3. 动态解析当前 launcher Activity；
4. 每轮先 `force-stop`；
5. 收集 `TotalTime` 和 `WaitTime`；
6. 检查最终 resumed Activity 是 `MessageHomeActivity`；
7. 无效样本单独记录，不进入统计；
8. 输出原始值、中位数和 nearest-rank P90；
9. 首次安装后的第一次启动单列，不混入稳定样本；
10. 记录当前提交、APK 哈希、包更新时间和设备基本信息，但不记录账户地址、标题或 Token。

建议每组至少取得 10 个有效样本。同一优化必须在同一时段完成修改前后 A/B，避免拿几天前的数据直接归因。

### 7.2 P1：拆分消息监听器注册的首次解析成本

优先给 `BaseApplication.registerMessagingListeners` 内部增加细粒度 Trace，然后依证据选择以下一种或多种修复：

- 让注册器依赖更小的接口，而不是为了 `addListener()` 解析完整 `MessagingController`；
- 将 Listener 注册能力从控制器重对象中分离为轻量 registry；
- 让 `UnreadWidgetUpdateListener` 的重依赖在真正收到事件时再解析；
- 检查 `UnreadWidgetUpdater`、Widget config、账户 repository 和 folder repository 是否被过早创建；
- 检查 Koin singleton 工厂内是否包含 SQLite、文件读取或同步等待；
- 对共享单例创建顺序进行串行化，避免后台任务抢先持锁后主线程等待。

不能直接删除监听器注册。必须验证新邮件、已读状态变化、删除邮件、移动邮件等事件仍会更新未读 Widget。

### 7.3 P1：建立明确的“邮件首页完整显示”信号

`MessageHomeActivity` 已经在 `onMessageListReady()` 后等待下一次绘制并调用 `reportFullyDrawn()`。下一轮如果需要延后
非关键任务，应复用这个真实业务里程碑，而不是监听任意 Activity 的第一次 `onDraw()`。

建议设计一个小型启动协调接口：

- contract 放在双方都允许依赖的 `:api` 或合适共享模块；
- `MessageHomeActivity` 在邮件列表 ready 且对应绘制完成后发出一次性信号；
- `app-common` 负责接收信号并调度 Application 级任务；
- 非关键任务按顺序执行，避免同时解析 Koin 大图和访问同一数据库；
- 没有 UI 的后台进程入口必须有独立策略，不能永远等待一个不会出现的 Activity 信号。

实际实施前需检查 ADR-0009 的 API/internal 边界，不能让 legacy UI 反向依赖 `app-common` internal 实现。

### 7.4 P1：重新设计通知恢复时机和依赖

通知恢复应从两个方向继续优化：

1. **减少不必要执行**：除账户为空外，继续判断是否存在需要恢复的状态，避免每次启动无条件打开相关数据库；
2. **减小对象图**：通知恢复只注入查询和恢复所需的最小接口，不因一个恢复入口构造完整通知、设置和控制器链路。

如果通知恢复不是首屏正确性所必需，应在真实 `reportFullyDrawn()` 之后执行。它与 Widget 初始化不应默认并行；应通过
Trace 比较“通知后 Widget”和“Widget 后通知”的总成本、数据库竞争和功能时效，再确定顺序。

### 7.5 P2：让 Widget 初始化按需发生

继续检查 `MessageListWidgetManager.init()` 是否能先用轻量方式判断设备上是否存在对应 Widget：

- 没有 Widget 时直接返回，不解析 message list repository；
- 存在 Widget 时才注册 listener 或打开存储；
- Widget Provider 被系统直接唤起时仍能独立完成初始化；
- 未读 Widget 和邮件列表 Widget 的初始化不要重复创建相同依赖。

该优化必须用真实设备上的“无 Widget”和“有 Widget”两组数据验证，不能只验证空配置分支。

### 7.6 P2：复核 Startup Profile 与近期功能变更

近期提交增加了二维码登录、双屏交互和更多 UI 类。它们未直接修改历史启动核心文件，但可能改变 R8 结果、主 DEX
布局或启动时实际加载的类。

下一轮在代码路径稳定后应：

- 重新生成真实邮件首页 Baseline/Startup Profile；
- 对比主 DEX 大小与关键类位置；
- 检查新规则是否来自正确的 KEMI 邮件首页路径；
- 使用同一 Performance 构建 A/B 验证收益；
- 限制无关页面类进入启动 Profile。

### 7.7 P2：增加自动回归保护

建议将以下指标保存为设备实验室或本地定期基线：

- TTID；
- TTFD；
- `am start -W` 中位数与 P90；
- `BaseApplication.onCreate` 和各子区段耗时；
- 主线程最长锁等待及 owner；
- Performance APK 是否可调试；
- 关键启动类的 DEX 分布。

普通单元测试可以保护“Provider 类加载不触发 DI”“调度动作只执行一次”等结构约束，但不能替代实机启动基准。

## 8. 下一轮建议实施顺序

为了让每一步都能独立归因，建议按以下顺序执行：

1. 固化启动测量脚本和当前 10 次基线；
2. 给消息监听器注册内部增加 Trace，不改变行为；
3. 只优化最慢的首次依赖解析路径并完成 A/B；
4. 复测通知恢复与 Widget 初始化的锁 owner/waiter；
5. 建立明确的首页 fully-drawn 协调信号；
6. 将非首屏任务按证据移到 fully-drawn 之后并串行复测；
7. 验证后台进程、通知和 Widget 行为；
8. 更新 Startup Profile，再做一轮独立 A/B；
9. 达到门槛后运行完整质量检查并更新本文数据。

每一步只改变一个主要变量。若一次同时移动任务、改 Koin 图、更新 Profile 和修改 UI，就无法判断收益或回退来自哪里。

## 9. 验收标准

### 9.1 性能门槛

沿用前一份文档建议的本地回归预警线：

|         指标          |                        下一轮目标 |
|---------------------|-----------------------------:|
| Performance APK 中位数 |                 不高于 1,500 ms |
| Performance APK P90 |                 不高于 2,000 ms |
| 有效样本                |                    每组至少 10 个 |
| 最终页面                | 100% 为 `MessageHomeActivity` |

正式产品 SLA 仍需覆盖不同设备档位、账户数量、数据库大小和系统版本。上述门槛只是当前固定设备上的工程预警线。

### 9.2 功能门槛

启动变快不能以破坏后台能力为代价。至少需要验证：

- 有账户与无账户启动；
- 普通邮件首页、通知深链和系统恢复启动；
- 新邮件通知恢复与通知清除；
- 未读 Widget 和邮件列表 Widget 更新；
- 后台同步进程在没有 Activity 时正常工作；
- 账户新增、删除后组件启停正确；
- 数据库升级拦截流程；
- 单屏与双屏邮件首页；
- 应用切后台再返回不会重复注册 Listener 或重复启动恢复任务。

### 9.3 工程质量门槛

Bug 修复完成后至少运行：

```shell
./gradlew test lint detekt spotlessCheck
./gradlew :app-k9mail:kemiPerformanceApk
```

如果没有可用模拟器或测试设备，无法执行的 `connectedAndroidTest` 必须在交付说明中明确列出，不能写成已通过。

## 10. 风险与约束

### 10.1 不要用 Debug 包作为性能验收包

Debug 包仍适合功能调试和 Trace 探索，但它的绝对启动时间不能和 release-like 基线混用。每次测试前都应检查安装包
flags，而不是根据文件名猜测构建类型。

### 10.2 不要用固定动画或延迟掩盖回归

长 Logo、固定最短展示时间或在主线程等待不会减少真实 TTID/TTFD。系统 Splash 可以改善点击反馈连续性，但只能在
真实性能稳定后作为体验优化。

### 10.3 不要把重任务简单搬到另一个线程

后台任务仍可持有 Koin 单例锁、SQLite 连接或文件锁。任何迁移都需要同时观察主线程 waiter、后台 owner 和数据库
调用，而不是只看代码是否使用了 `Dispatchers.IO`。

### 10.4 不要破坏后台入口

Application 可能由 Widget、Receiver、Worker 或 Service 拉起，不一定会出现邮件首页。所有“等首屏后执行”的方案都
必须为无 UI 进程定义行为，否则可能造成通知、Widget 或同步静默失效。

### 10.5 保护隐私

Trace、脚本和文档不得记录邮件地址、邮件标题、正文、Token、账户数据库内容或其他个人数据。性能报告只保留耗时、
组件名、线程状态和经过脱敏的设备信息。

## 11. 本轮实际验证记录

本轮已经实际完成：

- 读取并核对历史优化提交 `1d56632b71`；
- 确认历史提交是当前 `HEAD` 的祖先；
- 检查历史启动核心文件没有被后续提交直接撤销；
- 构建 `:app-k9mail:kemiPerformanceApk`，构建成功；
- 覆盖安装 Performance APK，并确认包不含 `DEBUGGABLE`；
- 完成当前基线 10 次采样，保留 9 个有效样本；
- 对当前基线和两步实验分别抓取 atrace；
- 运行本轮实验相关单元测试与模块 Spotless 检查；
- 根据实机数据撤回没有收益的实验代码；
- 按撤回后的仓库代码重新构建并安装 Performance APK，3 次复核为 `1,910 / 1,938 / 1,957 ms`，
  最终页面均为 `MessageHomeActivity`；
- 新报告的单文件 Spotless 检查通过。

本轮尝试运行全项目 `spotlessCheck`，但被原有
`docs/developer/startup-performance-optimization-case-study.md` 的既存 Flexmark 格式差异阻断；本轮没有修改该旧文档。
因为最终没有保留代码修复，只交付文档，所以没有继续执行完整 `test lint detekt`，也没有执行
`connectedAndroidTest`。下一轮正式保留代码修改时，必须按第 9.3 节补齐验证。

## 12. 下一轮开始时的检查清单

### 测量前

- [ ] 当前提交、工作区和 APK 哈希已记录；
- [ ] 使用 `kemiPerformance`，包 flags 不含 `DEBUGGABLE`；
- [ ] 设备、账户数据和 launcher 入口固定；
- [ ] 最终 Activity 校验为 `MessageHomeActivity`；
- [ ] 无效样本不会进入统计；
- [ ] 首次安装启动与稳定样本分开。

### 修改时

- [ ] 一次只改变一个主要变量；
- [ ] 先细分 Trace，再修改对象图；
- [ ] 同时检查 Koin waiter 和 owner；
- [ ] 检查 SQLite、文件和 Binder 工作是否发生在锁内；
- [ ] 使用真实 fully-drawn 信号，不使用任意 Activity 的第一次绘制；
- [ ] 为无 UI 后台进程保留正确行为；
- [ ] 新增最小回归测试，不吞异常或伪造空结果。

### 验收时

- [ ] 修改前后各至少 10 个有效 Performance 样本；
- [ ] 中位数、P90 和原始值完整记录；
- [ ] 通知、Widget、同步、深链和双屏功能已验证；
- [ ] `test lint detekt spotlessCheck` 已通过；
- [ ] Performance APK 已重新构建和实机复测；
- [ ] 未执行项及原因已明确记录。

## 13. 最终说明

前一轮已经把最初 14 秒级的严重白屏问题压缩到约 1.2 秒，并建立了 Performance 构建、真实入口、Trace、
Provider 防回归测试和 Startup Profile 等基础设施。当前版本在同类 release-like 测量下约为 1.95 秒，说明基础优化
仍然有效，但 Application 内的首次对象解析和后台共享资源竞争仍有明显空间。

下一轮最重要的不是再增加一个协程或延迟，而是把 `registerMessagingListeners` 的秒级区段拆开，找出具体重对象和
锁 owner；随后用邮件列表真正 fully drawn 的业务信号调度非关键任务，并验证没有破坏后台启动场景。只有同设备、
同数据、同构建的 A/B 达到门槛后，相关代码才能作为新的启动优化保留。
