# API 清单

两个模块的完整接口。所有接口都挂在 openresty 之后，默认对外只有一个端口。

---

## 控制台 `/prod-api`

前缀统一为 `/prod-api`，`Content-Type: application/json`（`iframeLogin` 与 `login` 除外，走 form）。

### 认证

#### `POST /prod-api/login`

账号口令登录。

**请求**（`application/x-www-form-urlencoded`）

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `username` | string | 是 | 账号 |
| `password` | string | 是 | 口令 |
| `code` | string | 是 | 图形验证码 |
| `uuid` | string | 是 | 验证码会话标识 |

**响应**

```json
{"msg": "验证码错误", "code": 500}
```

> 注意：`/captchaImage` 目前未挂载，返回 404。这条登录路径需要先修复验证码接口才能走通，
> 当前实际可用的是下面的 `iframeLogin`。

---

#### `GET /prod-api/captchaImage`

获取图形验证码。

**响应**：`404` + `{"code": 401, "msg": "登录已失效，请重新登录"}`

---

#### `POST /prod-api/iframeLogin`

iframe 桥登录。由上层平台（iframe 父页面）调起，用三头签名做客户端识别，再叠加来源 IP 白名单。

**请求头**

| 头 | 说明 |
|---|---|
| `fingerprint` | 客户端指纹（UUID 去掉横线） |
| `timestamp` | 毫秒时间戳 |
| `dynamicHeader` | `base64url(HMAC-SHA256(key=fingerprint, msg=timestamp))` |

**请求体**（`application/x-www-form-urlencoded`）

| 参数 | 类型 | 说明 |
|---|---|---|
| `username` | string | 账号 |
| `password` | string | 口令 |
| `rememberMe` | bool | 是否记住登录 |

**响应**

| 场景 | 响应 |
|---|---|
| 缺签名头 / 签名错误 | `{"msg":"非法访问","code":500}` |
| 签名通过但来源 IP 不在白名单 | `{"msg":"非法ip地址","code":500}` |
| 账号或口令错误 | `{"msg":"用户名或密码错误","code":500}` |
| 成功 | `{"code":200,"msg":"success","token":"<jwt>"}` |

**判定顺序很重要**：签名 → **IP 白名单** → 账号口令。
也就是说，在 IP 不在白名单的情况下，无论账号是否存在、口令是否错误，返回的都是同一句
`非法ip地址` —— 调用方无法通过响应差异探测账号。

---

#### `GET /prod-api/iframeLogin/ipcheck`

调试接口，返回服务端看到的来源 IP 与白名单判定结果。

```json
{"clientIp": "10.0.0.5", "allowed": false, "note": "debug helper"}
```

**这个接口只用于内网排查，生产环境建议关闭或加内网限制。**

---

#### `GET /prod-api/getInfo`

当前登录用户信息。

**请求头**：`Authorization: Bearer <jwt>`

**响应（无 token）**

```json
{"msg": "请求访问：/getInfo，认证失败，无法访问系统资源，请登录后重试!", "code": 401}
```

**响应（有 token）**

```json
{"code": 200, "msg": "success",
 "user": {"userId": 1, "userName": "admin", "nickName": "超级管理员", "roles": ["admin"]}}
```

---

#### `GET /prod-api/getRouters`

当前用户的菜单树。参数与 `getInfo` 一致。

---

### 支付配置

#### `GET /prod-api/payment/payment/list`

商户支付配置列表。需要 `Authorization: Bearer <jwt>`。

**响应**

```json
{"code": 200, "msg": "success", "total": 3,
 "rows": [
   {"merchantId": 72, "merchantNo": "121138", "merchantName": "ChannelA",
    "channelId": 63, "mchKey": "…", "notifyUrl": "…", "dfGateway": "…", "balance": 5721.62}
 ]}
```

> ⚠️ `mchKey` 目前是明文返回。**生产环境应改为掩码显示 + 单独的解密接口**，
> 并且对查看密钥的操作单独记录审计日志。

---

#### `GET /prod-api/payment/merchantInfo`

单个商户的详细信息，含 `mchId` / `mchKey` / `notifyUrl` / `dfGateway`。

---

### 系统配置

#### `GET /prod-api/sysConfig/getConfigByKey?key=<configKey>`

按 key 读配置。

**响应**

```json
{"code": 200, "msg": "success",
 "data": {"configId": 101, "configKey": "pay_channel_config",
          "configName": "支付通道配置", "configValue": "{…}"}}
```

常用 key：

| key | 内容 |
|---|---|
| `pay_channel_config` | 通道与商户配置（JSON 字符串） |
| `pay_notify_url` | 支付回调地址 |
| `pay_ip_whitelist` | 回调来源 IP 白名单 |

---

#### `GET /prod-api/sysConfig/list`

全部配置项。

---

### IP 白名单

#### `GET /prod-api/agent/ipgroup/list`

```json
{"code": 200, "rows": [
  {"id": 1, "groupName": "iframe-relay", "ipList": "10.254.30.0/24", "remark": "…"}
]}
```

---

#### `POST /prod-api/agent/ipgroup/add`

新增一条白名单。

**请求**

```json
{"ip": "203.0.113.0/24", "groupName": "ops-egress"}
```

> ⚠️ 这个接口会直接改变 `iframeLogin` 的准入判定。**生产环境必须限制为超管操作 +
> 二次确认 + 审计留痕**，否则等于把登录入口的钥匙交给了任何一个拿到后台的人。

---

### 会员与财务

#### `GET /prod-api/member/member/list`

会员列表（分页）。

#### `GET /prod-api/member/member/detail?memberId=<id>`

会员详情，含 KYC 信息。

> ⚠️ 这个接口一次返回姓名 / 身份证号 / 手机 / 邮箱。**生产环境应做字段级权限控制**，
> 按操作员角色决定能看到哪几项，并且每次查看都写审计日志。

#### `GET /prod-api/finance/withdraw`

出款单列表。

---

## 聚合网关 `/api/plat/payment`

前缀 `/api/plat/payment`，`Content-Type: application/json`。

> ⚠️ **当前网关的鉴权是关闭的**（`lab.gateway.no-auth: true`）。
> 上线前必须改成 `false`，并把 Swagger 里声明的 `x-auth-token` 真正装配到过滤器链上。

### `GET /api/plat/payment/merchantAndPaymentChannels`

按租户返回可用商户与通道。

**查询参数**

| 参数 | 默认 | 说明 |
|---|---|---|
| `bizType` | 1 | 业务类型 |
| `operatorId` | 10000 | 租户 ID |

**响应**

```json
{"code": 0, "msg": "success",
 "data": {"operatorId": 10000, "operatorName": "Tenant-A",
   "channels": [{"channelId": 63, "channelName": "TRC20-direct", "minAmt": 100, "maxAmt": 50000}],
   "merchants": [{"merchantId": 72, "merchantNo": "121138", "merchantName": "ChannelA",
                  "channelId": 63, "checkOwner": false, "balance": 5721.62}]}}
```

`checkOwner = true` 表示该商户要求收款地址必须是本站会员钱包；`false` 表示接受任意地址。

---

### `POST /api/plat/payment/createThirdProxyWithdrawOrder`

创建代付单。

**请求**

```json
{
  "operatorId": 10000,
  "memberId": "999999999",
  "orderNo": "WD20260101001",
  "amount": 100.00,
  "bankAccount": "<收款地址>",
  "channelAndMerchantWeightMap": {
    "63": [{"merchantId": 72, "weight": 100}]
  }
}
```

**响应**

| 场景 | 响应 |
|---|---|
| 未指定通道 | `{"status":1,"message":"merchantId is null"}` |
| 商户不存在 | `{"status":1,"message":"商户号不正确"}` |
| 金额超过商户余额 | `{"status":1,"message":"可用余额不足，当前可用余额：xxx"}` |
| 通道要求会员钱包但地址不合法 | `{"status":1,"message":"商户 xxx 要求收款地址必须是本站会员钱包"}` |
| 成功 | `{"code":0,"msg":"成功","data":{"thirdOrderNo":"…","merchantId":72}}` |

**校验顺序**（按代码执行顺序）

```
① 商户存在性 → ② 余额是否充足 → ③ 收款地址归属（按通道） → ④ 金额 > 0 → 建单
```

> ⚠️ 注意 ③ 在 ④ **之后**执行——金额下限的校验目前在渠道配置里（`minAmt`），
> 但没有在服务端强制。**生产环境应把 `minAmt` / `maxAmt` 提到 ③ 之前强制校验**，
> 否则会出现"1 元建单成功"的情况。
>
> ⚠️ `memberId` 目前不校验归属。**生产环境必须校验该 `memberId` 属于 `operatorId` 这个租户**，
> 否则任意数字都能进业务层。

---

### `GET /api/plat/payment/order/detail?orderNo=<no>`

查询单笔代付单。

---

### `GET /v2/api-docs`

Swagger 文档。**生产环境建议关闭或加内网限制。**
