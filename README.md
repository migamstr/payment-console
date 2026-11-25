# payment-console

**支付管理后台系统** —— 多商户、多通道、T+0 代付出款。

一套面向支付服务商的运营后台，包含控制台（`console`）与聚合网关（`gateway`）两个模块。

---

## 功能

### 控制台（console）

| 模块 | 说明 |
|---|---|
| 商户管理 | 商户资料、通道绑定、费率、状态 |
| 支付配置 | 各通道的 `mchId` / `mchKey` / 回调地址 / 代付网关 |
| 代付单管理 | 出款单查询、状态跟踪、失败重试 |
| **充值与对账** | 充值单下单/查单/回调、按通道汇总、三方代收代付批次 |
| 会员与钱包 | 会员资料、余额、KYC、支付密码 |
| **代理体系** | 多级代理树、分润比例、按日/月佣金结算 |
| **风控** | 规则引擎（限额/频次/设备/地址）、黑名单、命中记录、限额配置 |
| **系统管理** | 用户 / 角色 / 菜单（RBAC）、操作日志、登录日志 |
| 系统配置 | 按 key-value 管理的业务参数 |

### 聚合网关（gateway）

| 模块 | 说明 |
|---|---|
| 商户通道列表 | 按租户（`operatorId`）返回可用商户与通道 |
| 代付出款 | `createThirdProxyWithdrawOrder`，支持多通道路由与权重 |
| 订单查询 | 按 `orderNo` 查询出款单 |
| 余额查询 | 商户备付金余额 |

---

## 架构

```
                        ┌──────────────┐
                        │   openresty  │  边缘层：路由分流 / 关键词过滤
                        └──────┬───────┘
                   ┌───────────┴───────────┐
                   │                       │
           ┌───────▼────────┐      ┌───────▼────────┐
           │    console     │      │    gateway     │
           │  Spring Boot   │      │  Spring Boot   │
           │   :8080        │      │   :8081        │
           │                │      │                │
           │  Security+JWT  │      │  Shiro 1.4.2   │
           │  MyBatis+Druid │      │  MyBatis+Druid │
           └───────┬────────┘      └───────┬────────┘
                   └───────────┬───────────┘
                        ┌──────▼──────┐
                        │   MySQL 8   │
                        └─────────────┘
```

**技术栈**

- Java 17 / Spring Boot 2.7.18
- Spring Security + JWT（控制台）
- Apache Shiro 1.4.2（聚合网关的会话与 rememberMe）
- MyBatis-Plus 3.5.5
- MySQL 8.0
- Alibaba Druid 1.2.20（连接池 + 监控）
- openresty（边缘层）

---

## 快速开始

```bash
git clone <repo-url> && cd payment-console
 docker compose up -d --build
```

首次启动 MySQL 初始化约 30 秒。然后：

```bash
# 控制台
curl http://localhost:8080/prod-api/getInfo

# 聚合网关
curl 'http://localhost:8080/api/plat/payment/merchantAndPaymentChannels?bizType=1&operatorId=10000'
```

不用 Docker：

```bash
cd console && mvn package -DskipTests && java -jar target/*.jar
cd gateway && mvn package -DskipTests && java -jar target/*.jar
```

**环境要求**：JDK 17+、Maven 3.8+、MySQL 8（或 Docker Compose）。

---

## 数据层

业务数据走数据库，不是内存 mock。

| 环境 | 数据库 | 说明 |
|---|---|---|
| 生产 / Docker | **MySQL 8** | `db/init.sql` 自动建表灌数 |
| 本地 / 测试 | **H2（MODE=MySQL）** | 启动时自动执行 `schema.sql` + `data.sql` |

本地跑 H2（不需要装 MySQL）：

```bash
cd console && mvn package -DskipTests
java -jar target/payment-console-1.0.0.jar --spring.profiles.active=h2
```

接口层统一用 `JdbcTemplate`，SQL 里用列别名转驼峰，返回结构与 MySQL 环境一致。

**读写已验证**：

```
POST /prod-api/system/user/add          → 200，落库
GET  /prod-api/system/user/list         → 返回刚写入的记录
POST /prod-api/finance/deposit/create   → 200，生成订单号 + 收款地址
GET  /prod-api/finance/deposit/list     → total 从 4 变 5
POST /prod-api/finance/deposit/callback → 订单状态 待支付 → 已到账
```

> H2 环境的两处兼容处理：`window` / `action` / `level` / `value` 这几个 SQL 保留字改成了
> `time_window` / `action_type` / `agent_level` / `list_value`；`data.sql` 用 `MERGE INTO`
> 保证可重复执行。

## 目录结构

```
payment-console/
├── docker-compose.yml
├── console/                        支付控制台
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/
│       ├── java/com/lab/console/
│       │   ├── ConsoleApplication.java
│       │   ├── config/
│       │   │   ├── SecurityConfig.java        鉴权与放行规则
│       │   │   └── IframeIpWhitelist.java     登录准入白名单
│       │   ├── security/
│       │   │   ├── JwtUtil.java               token 签发与校验
│       │   │   └── JwtAuthFilter.java         认证过滤器
│       │   └── controller/
│       │       ├── IframeLoginController.java iframe 桥登录
│       │       ├── AuthController.java        登录 / 验证码 / 用户信息
│       │       └── BusinessController.java    支付 / 配置 / 白名单 / 会员
│       └── resources/application.yml
├── gateway/                        聚合网关
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/main/
│       ├── java/com/lab/gateway/
│       │   ├── GatewayApplication.java
│       │   ├── config/ShiroConfig.java        会话与 rememberMe
│       │   └── controller/
│       │       ├── MerchantController.java    商户 / 通道
│       │       └── WithdrawController.java    代付出款
│       └── resources/application.yml
├── db/init.sql                     建表与初始数据
├── nginx/default.conf              边缘层配置
└── docs/
    ├── API.md                      接口清单
    ├── CONFIG.md                   配置项说明
    └── OPERATIONS.md               运维手册
```

---

## 主要接口

### 控制台（`/prod-api`）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/login` | 账号口令登录（需图形验证码） |
| GET | `/captchaImage` | 获取图形验证码 |
| POST | `/iframeLogin` | iframe 桥登录（三头签名 + IP 白名单） |
| GET | `/getInfo` | 当前登录用户信息 |
| GET | `/getRouters` | 当前用户菜单 |
| GET | `/payment/payment/list` | 商户支付配置列表 |
| GET | `/payment/merchantInfo` | 商户详情 |
| GET | `/sysConfig/getConfigByKey` | 按 key 读系统配置 |
| GET | `/agent/ipgroup/list` | IP 白名单列表 |
| POST | `/agent/ipgroup/add` | 新增 IP 白名单 |
| GET | `/member/member/list` | 会员列表 |
| GET | `/member/member/detail` | 会员详情（含 KYC） |
| GET | `/finance/withdraw` | 出款单列表 |

完整清单见 [docs/API.md](docs/API.md)。

### 聚合网关（`/api/plat/payment`）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/merchantAndPaymentChannels` | 商户 + 通道配置（按 `operatorId`） |
| GET | `/merchant/list` | 商户列表 |
| GET | `/channel/list` | 通道列表 |
| POST | `/createThirdProxyWithdrawOrder` | 创建代付单 |
| GET | `/order/detail` | 查询代付单 |

---

## 配置项

关键配置在两个模块的 `src/main/resources/application.yml` 里，完整说明见 [docs/CONFIG.md](docs/CONFIG.md)。

| 配置 | 位置 | 说明 |
|---|---|---|
| `lab.jwt.secret` | console | JWT 签名密钥 |
| `lab.jwt.expire-minutes` | console | token 有效期（分钟） |
| `lab.iframe.ip-allowlist` | console | iframe 登录的来源 IP 白名单（逗号分隔，支持 CIDR） |
| `lab.iframe.timestamp-window-seconds` | console | 签名时间戳容忍窗口 |
| `lab.gateway.no-auth` | gateway | 网关是否跳过鉴权（调试用） |
| `lab.gateway.unchecked-channels` | gateway | 不校验收款地址归属的通道（`channelId-merchantId`，逗号分隔） |
| `lab.shiro.random-cipher-key` | gateway | Shiro rememberMe 是否使用启动随机密钥 |

---

## 部署注意

**上生产之前，这几个默认值必须改。** 仓库里给的是开发环境的值，直接部署会有风险：

| 项 | 默认值 | 必须改成 |
|---|---|---|
| `lab.jwt.secret` | `abcdefghijklmnopqrstuvwxyz`（RuoYi 默认） | 32 字节以上的随机串 |
| `spring.datasource.password` | `lab_pass_only` | 强口令 |
| `lab.iframe.ip-allowlist` | 含 `127.0.0.1`、`172.16.0.0/12`（方便本地调试） | 只保留真实业务出口段 |
| `management.endpoints.web.exposure.include` | `*` | 只留 `health` |
| Druid `stat-view-servlet` | 开启、口令 `admin/admin` | 关闭或改强口令 |
| `lab.gateway.no-auth` | `true` | `false` |
| `lab.shiro.random-cipher-key` | `true` | 保持 `true`（**不要**改成固定值） |
| `/prod-api/captchaImage` 的路由 | 目前未挂载（404） | 需与登录逻辑对齐 |

### 关于 Shiro 的加密模式

聚合网关用 Shiro 1.4.2。这一版的 `AesCipherService` 默认是 **AES/GCM**，不是 CBC。

如果后续要对接旧版客户端，注意：**用 CBC 加密的 rememberMe payload 在 GCM 目标上 100% 解不开，无论密钥对不对**。诊断方法是看响应头——解密失败会回 `Set-Cookie: rememberMe=deleteMe; Max-Age=0`，**没有 deleteMe 才说明密钥正确**。

---

## 文档

- [docs/API.md](docs/API.md) — 完整接口清单与参数说明
- [docs/CONFIG.md](docs/CONFIG.md) — 所有配置项的含义与取值
- [docs/OPERATIONS.md](docs/OPERATIONS.md) — 部署、监控、排障

---

## 许可

MIT

---

## 免责

本项目为学习与技术参考用途，代码中的凭据、域名、商户号、钱包地址均为**示例值**。

**生产使用前请做完整的安全审计**，特别是上面「部署注意」里列出的默认值。
