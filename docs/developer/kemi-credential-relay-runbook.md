# KEMI 凭据中继部署与故障排查示例

本文只说明通用部署与验证流程，不包含内部宿主机、SSH 别名、证书位置或生产发布记录。
`relay.example.com` 是占位域名，不能直接用于部署。环境专用脚本和配置保留在本地
`services/kemi-credential-relay/deploy/`，其中的 `.sh`、`.conf` 文件不纳入 Git。
新克隆仓库需要管理员在批准的私有配置存储中获取或重新准备这些文件。

## 1. 适用范围

本文适用于加密凭据中继服务。KEMI 当前的局域网授权码传输与此中继是不同路径，
实际选择由客户端配置决定。中继实现和已运行的服务不会因本次文档清理而改变。

相关源码：

- [服务说明](../../services/kemi-credential-relay/README.md)
- [Node 服务](../../services/kemi-credential-relay/server.js)
- [协议测试](../../services/kemi-credential-relay/test/server.test.js)
- [Android Repository](../../feature/account/setup/src/main/kotlin/app/k9mail/feature/account/setup/ui/credential/RemoteCredentialTransferRepository.kt)
- [客户端入口](../../app-k9mail/src/main/kotlin/app/k9mail/K9KoinModule.kt)

## 2. 安全边界

平板生成短期会话、独立的读写令牌和 P-256 密钥对。手机使用 P-256 ECDH、
HKDF-SHA-256 和 AES-256-GCM 加密服务商生成的邮件应用授权码。服务器只保存密文、
令牌哈希和过期时间，不接收明文授权码或平板私钥。

- 会话默认五分钟过期，每个会话最多接受一次上传；重启会清除内存会话。
- 二维码 fragment 不发送给服务器；读令牌只保留在平板端。
- 不记录真实邮箱、授权码、令牌、二维码 fragment、请求正文或带会话标识的路径。
- 服务没有数据库、管理员 API、分析或第三方页面资源。
- 公网入口必须使用受信任的 HTTPS，并限制连接数、请求速率和请求体大小。
- Node 端口不能直接暴露到公网。反向代理必须覆盖转发来源头，拒绝无关路由。

## 3. 本地检查

使用 Node.js 22.13 或更高版本，在仓库根目录执行：

```sh
cd services/kemi-credential-relay
node --check server.js
node --check web/assist.js
npm test
KEMI_CREDENTIAL_RELAY_HOST=127.0.0.1 KEMI_CREDENTIAL_RELAY_PORT=8789 npm start
```

另一个终端检查：

```sh
curl --fail --silent --show-error http://127.0.0.1:8789/kemi-assist/health
```

预期返回 `{"version":1,"ok":true}`。这只验证本机服务，不代表公网或 Android 验收通过。

## 4. 部署准备

管理员需要在私有配置中明确以下内容：

- 后端和 HTTPS 网关的精确主机、服务账号、监听地址与隔离网络；
- 不可变 release 目录、当前版本链接及上一版本的恢复位置；
- 进程管理器的启动命令、运行账号、日志轮转与自动重启策略；
- 网关证书来源、续期方式、只读挂载位置以及域名；
- 防火墙、代理限流和只允许访问 `/kemi-assist` 的路由规则；
- 客户端中继地址与部署入口的一致性。

签名私钥、TLS 私钥、Token 和实际密码使用秘密管理器或批准的加密存储。
忽略文件不是备份，不能把未跟踪文件当作已经完成凭据恢复建设。

上线前先验证配置语法、服务测试、证书链和回滚路径。使用最小范围的服务操作，
不因为单个应用失败而重启宿主机或其他业务。不要直接修改正在运行的 release 文件。

## 5. 逐层验证

按以下顺序定位，不能仅以容器 `Up` 或单个 HTTP 200 作为验收结果：

1. 后端进程存在、端口监听、本地 health 返回正常。
2. HTTPS 网关能经隔离网络连接后端。
3. 目标域名 DNS、端口、证书链、SAN 和有效期正确。
4. 手机页面和 health 可访问，无关路径返回 404。
5. 使用随机测试会话验证创建、等待、一次上传、下载及删除。
6. 用目标 Android 构建检查入口、设备时间、协议交互和实际用户流程。

替换示例域名后检查公网入口：

```sh
curl --fail --silent --show-error https://relay.example.com/kemi-assist/health
curl --silent --show-error --output /dev/null --write-out '%{http_code}\n' \
  https://relay.example.com/kemi-assist/assist
```

协议正常结果：

| 操作 |                     路径                      | 状态码 |
|----|---------------------------------------------|-----|
| 创建 | `POST /kemi-assist/v1/sessions`             | 201 |
| 等待 | `GET /kemi-assist/v1/sessions/{id}/payload` | 204 |
| 上传 | `PUT /kemi-assist/v1/sessions/{id}/payload` | 204 |
| 下载 | `GET /kemi-assist/v1/sessions/{id}/payload` | 200 |
| 删除 | `DELETE /kemi-assist/v1/sessions/{id}`      | 204 |

协议测试只使用随机临时令牌和合成载荷，检查下载内容一致性，并在完成后删除会话。
不要使用真实邮箱授权码做自动化冒烟测试，不要输出令牌或载荷。

## 6. 故障与恢复

|       现象        |           检查方向            |
|-----------------|---------------------------|
| 没有监听            | 进程管理器、启动配置、Node 版本与脱敏错误日志 |
| 后端正常但 HTTPS 502 | 网关到后端的网络、名称解析与后端重启窗口      |
| TLS 失败          | 证书链、域名、设备时间、证书有效期         |
| 401             | 当前会话读写令牌是否匹配；不要记录实际值      |
| 404             | 会话过期、删除、服务重启或请求路径错误       |
| 409             | 会话标识冲突或重复上传               |
| 413 / 415       | 请求尺寸或 Content-Type 不符合协议  |
| 429 / 503       | 限流或内存会话容量上限               |
| 页面可打开但 App 失败   | 构建入口、传输模式、设备时间、DNS 与 TLS  |

恢复前记录当前 release 与进程状态；根据定位结果重启对应服务或回退到已验证版本。
重启会使未完成会话失效，用户需要重新生成二维码。恢复后重复逐层验证和协议测试。
本公开示例不声明任何生产部署已完成或通过验收。
