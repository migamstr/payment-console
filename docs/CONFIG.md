# 配置项说明

两个模块的配置都在各自的 `src/main/resources/application.yml` 里。
大部分可以用环境变量覆盖（Spring Boot 的 relaxed binding）。

---

## console（控制台）

### 服务

| 配置 | 默认 | 说明 |
|---|---|---|
| `server.port` | `8080` | 监听端口 |
| `server.servlet.session.cookie.name` | `LABSESSION` | 会话 Cookie 名 |

### 数据源

| 配置 | 默认 | 说明 |
|---|---|---|
| `spring.datasource.url` | `jdbc:mysql://mysql:3306/payment_lab…` | JDBC 连接串 |
| `spring.datasource.username` | `lab` | 数据库账号 |
| `spring.datasource.password` | `lab_pass_only` | 数据库口令 |
| `spring.datasource.druid.initial-size` | `2` | 连接池初始连接数 |
| `spring.datasource.druid.min-idle` | `2` | 最小空闲 |
| `spring.datasource.druid.max-active` | `10` | 最大连接 |
| `spring.datasource.druid.stat-view-servlet.enabled` | `true` | 是否开启 Druid 监控页 |
| `spring.datasource.druid.stat-view-servlet.url-pattern` | `/druid/*` | 监控页路径 |
| `spring.datasource.druid.stat-view-servlet.login-username` | `admin` | 监控页账号 |
| `spring.datasource.druid.stat-view-servlet.login-password` | `admin` | 监控页口令 |

> ⚠️ **生产环境**：Druid 监控页要么关闭（`enabled: false`），要么改强口令 + 限制来源 IP。
> 默认的 `admin/admin` 等于把 SQL 执行记录和连接信息挂在公网上。

### JWT

| 配置 | 默认 | 说明 |
|---|---|---|
| `lab.jwt.secret` | `abcdefghijklmnopqrstuvwxyz` | 签名密钥 |
| `lab.jwt.expire-minutes` | `720` | token 有效期（分钟） |

> ⚠️ **生产环境必须换掉 `lab.jwt.secret`**。26 字节的弱密钥可以被离线爆破，
> 一旦密钥泄露，任何人都能自签任意用户的 token。
>
> 建议：32 字节以上的随机串，从环境变量注入（`LAB_JWT_SECRET`），不要写在配置文件里。

**实现说明**：`JwtUtil` 是手工实现的 HS256（用 `javax.crypto.Mac` + `SecretKeySpec`），
没有走 jjwt。原因是 jjwt 0.11+ 会强制检查密钥长度（要求 ≥256 bit），弱密钥会直接抛
`WeakKeyException` —— 那样默认配置根本跑不起来。手工实现没有这个限制，行为和旧版框架一致。

### iframe 登录

| 配置 | 默认 | 说明 |
|---|---|---|
| `lab.iframe.ip-allowlist` | `127.0.0.1,172.16.0.0/12,198.51.100.0/24` | 允许的来源 IP / CIDR，**逗号分隔** |
| `lab.iframe.timestamp-window-seconds` | `300` | 签名时间戳的有效窗口 |

> ⚠️ **格式要求**：`ip-allowlist` 必须写成**逗号分隔的字符串**，不能写成 YAML 数组。
> Spring 的 `@Value` 注入 `List<String>` 时只认逗号分隔的形式，YAML 数组会暴露成
> `…[0]`、`…[1]`，占位符解析会失败。
>
> ⚠️ **生产环境**：只保留真实的业务出口段。默认值里的 `127.0.0.1` 和 `172.16.0.0/12`
> 是为了本地调试方便，上线前必须删掉。
>
> **判定逻辑**：`IframeIpWhitelist` 取的是 `request.getRemoteAddr()`，即真实 socket 源 IP。
> 它**不读** `X-Forwarded-For`、`X-Real-IP` 或任何转发头 —— 这是刻意的设计，
> 目的是防止通过伪造请求头绕过白名单。如果前面有多层代理，需要保证 openresty 使用的是
> 连接级 IP，或者在这层做真实的来源判定。

### Actuator

| 配置 | 默认 | 说明 |
|---|---|---|
| `management.endpoints.web.exposure.include` | `*` | 暴露的端点 |
| `management.endpoint.health.show-details` | `always` | health 是否显示细节 |

> ⚠️ **生产环境必须收紧**。`include: "*"` 会把 `/actuator/env`、`/actuator/heapdump`
> 这些敏感端点全暴露出去，`env` 里能看到数据库口令和 JWT 密钥。
>
> 建议：`include: health,info`，并且给 actuator 单独加鉴权。

---

## gateway（聚合网关）

### 服务

| 配置 | 默认 | 说明 |
|---|---|---|
| `server.port` | `8081` | 监听端口 |

### 网关行为

| 配置 | 默认 | 说明 |
|---|---|---|
| `lab.gateway.no-auth` | `true` | 是否跳过鉴权 |
| `lab.gateway.unchecked-channels` | `63-72,63-100` | 不校验收款地址归属的通道，格式 `channelId-merchantId`，**逗号分隔** |

> ⚠️ **`no-auth: true` 是开发期开关，生产必须改成 `false`。**
> 打开时 `/api/plat/payment/**` 全部匿名可达，包括出款接口。
>
> ⚠️ **`unchecked-channels` 要谨慎使用**。名单里的通道不会校验 `bankAccount` 是否属于
> 本站会员钱包，等于允许把款打到任意地址。这个能力只应该给少数确实需要"任意地址出款"
> 的通道开，并且要有额外的风控（限额、白名单收款地址、人工复核）。

### Shiro

| 配置 | 默认 | 说明 |
|---|---|---|
| `lab.shiro.random-cipher-key` | `true` | rememberMe 的 cipherKey 是否启动随机生成 |

**关于加密模式（重要）**

Shiro 1.4.2 的 `AesCipherService` 默认是 **AES/GCM**，不是 CBC。各版本对照：

| Shiro 版本 | 默认模式 | 密文长度 |
|---|---|---|
| 1.2.4 | `AES/CBC/PKCS5Padding` | 明文 + pad + 16 IV |
| **1.4.2** | **`AES/GCM`** | 明文 + 16 tag + 16 IV |
| 1.7.1 | `AES/GCM/NoPadding` | 同上 |

**含义**：只要目标用的是 shiro ≥ 1.4.2，`rememberMe` 就必须按 GCM 处理。
用 CBC 加密的 payload 100% 解不开，**无论密钥对不对**。

**诊断方法**（排查"密钥不对"还是"模式不对"）：

```
带 Cookie: rememberMe=<任意值>  →  响应必回 Set-Cookie: rememberMe=deleteMe; Max-Age=0
不带 Cookie                     →  不回
```

**响应里没有 `deleteMe` 才说明密钥正确。** 如果换了密钥但一直有 deleteMe，
先确认加密模式对不对，再继续试密钥。

---

## 环境变量覆盖

Spring Boot 支持用环境变量覆盖配置，命名规则是把 `.` 换成 `_`、`-` 换成 `_`、全大写。

常用几个：

```bash
export LAB_JWT_SECRET="<32字节随机串>"
export LAB_IFRAME_IP_ALLOWLIST="10.0.0.0/8,203.0.113.0/24"
export LAB_GATEWAY_NO_AUTH="false"
export SPRING_DATASOURCE_PASSWORD="<强口令>"
export MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE="health,info"
```

Docker Compose 里直接写在 `environment:` 段即可。

---

## 配置检查清单（上线前逐项过）

- [ ] `lab.jwt.secret` 已换成 32 字节以上随机串
- [ ] `spring.datasource.password` 已换成强口令
- [ ] `lab.iframe.ip-allowlist` 已删掉 `127.0.0.1` 和 `172.16.0.0/12`
- [ ] `management.endpoints.web.exposure.include` 已收紧到 `health,info`
- [ ] Druid 监控页已关闭或改强口令 + 限制来源
- [ ] `lab.gateway.no-auth` 已改成 `false`
- [ ] `lab.shiro.random-cipher-key` 保持 `true`
- [ ] `/prod-api/captchaImage` 已与登录逻辑对齐（目前是 404）
- [ ] `/prod-api/iframeLogin/ipcheck` 已加内网限制或关闭
- [ ] `/v2/api-docs` 已加内网限制或关闭
- [ ] 支付配置接口的 `mchKey` 已改为掩码显示
- [ ] 会员详情接口已做字段级权限控制
- [ ] `/agent/ipgroup/add` 已限制为超管 + 审计
