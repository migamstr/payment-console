# 安全扫描风险清单：payment-console

来源：Devin 安全扫描（normal effort，2026-11-25），已按 `SECURITY_MODEL.md` 中的威胁模型重新排定优先级。
共 20 条未关闭风险：严重 12、高 2、中 3、低 4。扫描器另有 6 条重复项已自动关闭，未列入。

说明：扫描器默认假设边缘入口暴露在公网；按威胁模型，边缘仅内网/VPN 可达，但内网无账号用户和受邀商户/代理仍在攻击者范围内，因此优先级基本保持不变。
本文件不包含任何密钥或密码的具体值。修复后请轮换所有已暴露的凭据。

| # | 优先级 | 组件 | 风险 |
|---|---|---|---|
| 1 | 严重 | Gateway | 代付接口无需认证，可把商户余额打到任意地址 |
| 2 | 严重 | Gateway | 金额传 "NaN" 绕过余额和正数校验，破坏商户余额 |
| 3 | 严重 | Gateway | 客户端自定义 orderNo 可覆盖他人订单，订单详情无归属校验 |
| 4 | 严重 | Console Auth | 所有控制台账号（含代理账号）都被当作超级管理员 |
| 5 | 严重 | Console API | 无角色/归属校验：任何控制台用户可读取全部商户密钥和会员 KYC |
| 6 | 严重 | Console API | 任何控制台用户可修改任意代理佣金比例并结算佣金 |
| 7 | 严重 | Console Auth | JWT 签名密钥硬编码且公开，令牌永不过期 |
| 8 | 严重 | Console Auth | iframeLogin 接受硬编码 admin/admin，签名和 IP 校验形同虚设 |
| 9 | 严重 | Console Auth | 登录无限次猜测，密码以无盐 MD5 存储 |
| 10 | 严重 | Console API | 无需登录即可下载 .env、数据库备份和 git 配置 |
| 11 | 严重 | Console API | 资源下载接口路径穿越，可读取服务器任意文件 |
| 12 | 高 | Console API | 充值回调无认证、无签名，任何人可把充值标记为已到账 |
| 13 | 高 | Gateway | 网关商户、通道、余额接口无认证 |
| 14 | 中 | Gateway | 余额扣减非原子操作，并发请求可透支 |
| 15 | 中 | Gateway | 网关 Druid 监控台无需登录 |
| 16 | 中 | Deployment Config | 启动时把生产凭据和通道密钥写入 /tmp/lab-secrets |
| 17 | 低 | Console Auth | Spring Security 放行全部 /prod-api/** 和 Druid 路径（根因配置） |
| 18 | 低 | Gateway | 使用 Apache Shiro 1.4.2，存在已知认证绕过漏洞 |
| 19 | 低 | Gateway | Shiro rememberMe 密钥打印到日志，且有公开的备用密钥 |
| 20 | 低 | Console API | 列表接口全表加载，分页参数不校验 |

---

## 严重

### 1. 代付接口无需认证，可把商户余额打到任意地址
- **位置：** `gateway/src/main/java/com/lab/gateway/controller/WithdrawController.java:28-36, 64-152`，`gateway/src/main/java/com/lab/gateway/config/ShiroConfig.java:91-98`，`nginx/default.conf:32-37`
- **问题：** Shiro 把 `/api/**` 和 `/**` 设为 `anon`，`lab.gateway.no-auth` 默认 true。`createThirdProxyWithdrawOrder` 直接信任请求里的 `merchantId`、`memberId`、`bankAccount`；收款地址校验很弱，部分通道直接跳过；余额不足时报错信息会泄露商户余额。
- **影响：** 内网任何人无需账号即可提走任意商户余额（关键资产：代付与商户余额）。
- **修复建议：** 所有 `/api/**` 强制商户认证（商户密钥签名或 Shiro `authc`），删除 `no-auth` 开关；`merchantId` 从认证身份取得；所有通道都校验收款地址属于该会员；报错不返回余额。

### 2. 金额传 "NaN" 绕过余额和正数校验
- **位置：** `WithdrawController.java:92-122, 190-196`
- **问题：** NaN 与任何数比较都为 false，余额和正数校验均被绕过，扣减后商户余额变成 NaN，此后余额校验永远通过。
- **影响：** 可从该商户发起任意金额代付。
- **修复建议：** 用 `BigDecimal` 解析金额，拒绝非有限值；先校验 `amount > 0` 和通道上下限，再校验余额（含手续费）。

### 3. 客户端自定义 orderNo 可覆盖订单，订单详情无归属校验
- **位置：** `WithdrawController.java:134-160`
- **问题：** 相同 orderNo 会直接覆盖已有订单；默认 orderNo 为 `LAB` + 秒级时间戳，易猜；`/order/detail` 无认证，返回会员 ID、收款账号和金额。
- **修复建议：** 按商户对 orderNo 做唯一约束（`putIfAbsent` 或数据库唯一键）；`/order/detail` 限定为当前认证商户。

### 4. 所有控制台账号（含代理账号）都被当作超级管理员
- **位置：** `console/src/main/java/com/lab/console/security/JwtAuthFilter.java:40-50`，`SystemController.java:108-114`，`RiskController.java:100-109`，`console/src/main/resources/data.sql:4-9`
- **问题：** 验证令牌后固定授予 `ROLE_ADMIN`。受邀商户/代理登录后即可操作所有租户的数据和管理功能。
- **优先级说明：** 扫描器评为高。威胁模型确认受邀商户/代理持有账号，且可触达渠道密钥、KYC、代付等关键资产，因此上调为严重。
- **修复建议：** 验证令牌时从数据库加载角色和商户/代理归属，写入 Spring Security 上下文；管理接口（system、risk、sysConfig、支付配置、会员 PII）加 `@PreAuthorize` 角色校验；商户和代理数据按调用者 ID 过滤。

### 5. 无角色/归属校验：任何控制台用户可读取全部商户密钥和会员 KYC
- **位置：** `console/src/main/java/com/lab/console/controller/BusinessController.java:29-80, 93-138, 207-210`，`JwtAuthFilter.java:41-46`
- **问题：** `unauth()` 只检查令牌签名，商户列表返回明文 `mch_key`，会员详情返回完整身份信息。
- **修复建议：** 加载调用者角色和归属；商户列表、系统配置、IP 组、会员 PII 仅限运营角色；商户查询限定为本商户；列表响应中永不返回 `mch_key`。

### 6. 任何控制台用户可修改任意代理佣金比例并结算佣金
- **位置：** `console/src/main/java/com/lab/console/controller/AgentController.java:107-178, 181-183`
- **修复建议：** 代理新增/编辑和佣金结算仅限运营角色；代理查询限定为调用者自己的下级树。

### 7. JWT 签名密钥硬编码且公开，令牌永不过期
- **位置：** `console/src/main/java/com/lab/console/security/JwtUtil.java:42-49, 52-69, 76-85`，`console/src/main/resources/application.yml:43-46`，`LeakController.java:42-52`
- **问题：** 密钥写死在配置里，还会通过 `/.env` 泄露；`parseUsername` 不检查 `exp`，也不校验服务端会话，任何人都能伪造管理员令牌。
- **修复建议：** 从密钥管理服务或环境变量加载至少 32 字节的随机密钥，并轮换现有密钥；改用维护中的 JWT 库（jjwt 0.11+ 或 nimbus-jose-jwt）校验密钥长度和 `exp`；令牌绑定服务端会话 ID，支持登出和吊销。

### 8. iframeLogin 接受硬编码 admin/admin，签名和 IP 校验形同虚设
- **位置：** `console/src/main/java/com/lab/console/controller/IframeLoginController.java:56-95, 98-105`，`application.yml:46-48`，`nginx/default.conf:46-55`
- **问题：** HMAC 密钥是客户端自己传的 `fingerprint`；白名单包含 `172.16.0.0/12`，正好覆盖 docker 网络中的边缘容器地址，而控制台读取的是 `getRemoteAddr()`，所以经过边缘的请求全部放行。
- **修复建议：** 删除硬编码凭据，改用 `sys_user` 和 bcrypt 认证；HMAC 改用服务端密钥，或直接去掉；白名单只保留真实合作方地址，并读取可信代理传来的原始客户端 IP；删除 `/iframeLogin/ipcheck`。

### 9. 登录无限次猜测，密码以无盐 MD5 存储
- **位置：** `console/src/main/java/com/lab/console/controller/AuthController.java:72-103`
- **问题：** 种子管理员密码的哈希就是常见弱口令的 MD5；登录没有次数限制。
- **修复建议：** 每次尝试后作废验证码；按账号和 IP 限流；迁移到已有的 `BCryptPasswordEncoder`；修改所有种子账号密码。

### 10. 无需登录即可下载 .env、数据库备份和 git 配置
- **位置：** `console/src/main/java/com/lab/console/controller/LeakController.java:18-66, 79-118`，`SecurityConfig.java` 中对应的 permitAll
- **修复建议：** 删除该 Controller 及相关 permitAll 规则；轮换它暴露过的所有值（JWT 密钥、数据库/Redis 密码、商户密钥、管理员密码、git token）；在边缘额外拦截点文件和 `*.sql`。

### 11. 资源下载接口路径穿越，可读取服务器任意文件
- **位置：** `BusinessController.java:172-205`，`console/src/main/java/com/lab/console/config/LabSeed.java:59-70`
- **问题：** `/prod-api/common/download/resource` 只做字符串替换，未做规范化路径校验，可读取 `/tmp/lab-secrets` 中的生产凭据等任意文件。
- **修复建议：** 只允许从指定目录下载，用 `Path.resolve(name).normalize()` 解析后，确认路径仍在该目录内；删除 secrets 目录和原始路径候选逻辑。

## 高

### 12. 充值回调无认证、无签名，任何人可把充值标记为已到账
- **位置：** `console/src/main/java/com/lab/console/controller/DepositController.java:97-98, 131-152`
- **问题：** `/prod-api/finance/deposit/callback` 在 POST permitAll 范围内，不校验签名，`actualAmount` 由客户端指定。
- **优先级说明：** 用户把充值入账列为关键资产，但扫描器指出资金影响取决于代码里看不到的下游对账，因此不上调。
- **修复建议：** 用通道 `mchKey` 校验回调签名；忽略客户端金额，或要求与订单金额一致；更新语句加 `WHERE order_no=? AND status=0` 并检查影响行数；在边缘限制回调来源 IP。

### 13. 网关商户、通道、余额接口无认证
- **位置：** `gateway/src/main/java/com/lab/gateway/controller/MerchantController.java:54-90`，`gateway/src/main/resources/application.yml:28-35`，`ShiroConfig.java:86-99`
- **问题：** 无需账号即可列出全部商户、余额和通道的校验开关，是代付攻击链的第一步。
- **修复建议：** 删除 `no-auth` 开关，所有 `/api/**` 要求令牌或商户签名；响应按调用者的运营方和商户过滤。

## 中

### 14. 余额扣减非原子操作，并发请求可透支
- **位置：** `WithdrawController.java:93-122`
- **修复建议：** 原子扣减：`UPDATE ... SET balance = balance - ? WHERE merchant_id = ? AND balance >= ?`，检查影响行数（含手续费）。

### 15. 网关 Druid 监控台无需登录
- **位置：** `gateway/src/main/resources/application.yml:16-20`，`nginx/default.conf:25-30`
- **问题：** 暴露 SQL 文本、数据源 URL 和查询统计，不含数据库密码。
- **修复建议：** 部署环境关闭 `stat-view-servlet`，或从密钥读取强口令并限制 `allow`；从边缘移除 `/druid/` 路由。

### 16. 启动时把生产凭据和通道密钥写入 /tmp/lab-secrets
- **位置：** `LabSeed.java:25-50`，`BusinessController.java:182-185`
- **问题：** 直接读取需要本机或容器权限；远程可通过第 11 条路径穿越读取。
- **修复建议：** 删除 LabSeed，轮换相关值，运行时从密钥管理服务加载。

## 低

### 17. Spring Security 放行全部 /prod-api/** 和 Druid 路径（根因配置）
- **位置：** `console/src/main/java/com/lab/console/config/SecurityConfig.java:61-80`
- **修复建议：** 删除 `/prod-api/**`、actuator、Druid 的 permitAll，只放行 login、captcha、iframeLogin，其余一律要求认证，并用 `hasRole` 做角色校验。

### 18. 使用 Apache Shiro 1.4.2，存在已知认证绕过漏洞（如 CVE-2020-1957）
- **位置：** `gateway/pom.xml:33-37`
- **说明：** 目前所有路径都是 `anon`，所以暂无额外影响；加上 `authc` 后就会生效。
- **修复建议：** 升级 `shiro-spring` 到最新 1.13.x 或 2.x，并规划迁出已停止开源维护的 Spring Boot 2.7。

### 19. Shiro rememberMe 密钥打印到日志，且有公开的备用密钥
- **位置：** `ShiroConfig.java:46-73`
- **说明：** 默认使用随机密钥，公开备用密钥未启用。
- **修复建议：** 不再打印密钥，删除备用密钥，升级 Shiro；网关不需要 rememberMe 就关闭它。

### 20. 列表接口全表加载，分页参数不校验
- **位置：** `AgentController.java:193-203`
- **修复建议：** pageSize 限制在 1-100，pageNum 至少为 1，并在 SQL 中使用 LIMIT/OFFSET。
