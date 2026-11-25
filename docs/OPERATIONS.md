# 运维手册

部署、监控、排障。

---

## 部署

### Docker Compose（推荐）

```bash
docker compose up -d --build
docker compose ps
docker compose logs -f console
```

四个容器：

| 容器 | 作用 | 端口 |
|---|---|---|
| `lab-mysql` | MySQL 8 | 仅内网 |
| `lab-console` | 支付控制台 | 仅内网 8080 |
| `lab-gateway` | 聚合网关 | 仅内网 8081 |
| `lab-edge` | openresty | 对外 8080 |

只有 `edge` 对外暴露端口，其余都在 Docker 内网里。

### 传统部署

```bash
# 1. 数据库
mysql -uroot -p < db/init.sql

# 2. 控制台
cd console && mvn clean package -DskipTests
java -jar target/payment-console-1.0.0.jar \
  --spring.datasource.url='jdbc:mysql://127.0.0.1:3306/payment_lab?...' \
  --spring.datasource.password='<强口令>' \
  --lab.jwt.secret='<32字节随机串>'

# 3. 聚合网关
cd gateway && mvn clean package -DskipTests
java -jar target/payment-gateway-1.0.0.jar \
  --spring.datasource.url='jdbc:mysql://127.0.0.1:3306/payment_lab?...' \
  --lab.gateway.no-auth=false
```

### 启动顺序

```
MySQL → console / gateway → openresty
```

`console` 和 `gateway` 启动时会连 MySQL，所以 MySQL 必须先 ready。
用 Compose 的话 `depends_on: condition: service_healthy` 已经处理了。

---

## 健康检查

```bash
# 控制台
curl -s http://localhost:8080/actuator/health

# 聚合网关（没有 actuator，用业务接口探）
curl -s 'http://localhost:8080/api/plat/payment/merchantAndPaymentChannels?bizType=1&operatorId=10000'
```

**注意**：`/actuator/health` 在生产环境应该只对内网开放。

---

## 日志

默认输出到 stdout，Docker 环境下用 `docker compose logs` 看。

关键日志位置：

| 现象 | 看哪里 |
|---|---|
| 启动失败 | 启动日志里的 `APPLICATION FAILED TO START` + `Description:` |
| 登录 500 | `JwtUtil` / `IframeLoginController` 的堆栈 |
| 数据库连接失败 | `DruidDataSource` 的初始化日志 |
| Shiro 相关 | `ShiroConfig` 里打印的 `shiro cipherKey`（仅启动时一次） |

---

## 常见问题

### 1. 启动报 `Port 8080 was already in use`

```bash
ss -tlnp | grep 8080
kill <pid>
```

Compose 环境下检查是不是上一个容器没停干净：`docker compose down`。

### 2. 启动报 `Could not resolve placeholder 'lab.xxx'`

配置项写成了 YAML 数组。Spring 的 `@Value` 注入 `List<String>` 只认**逗号分隔字符串**：

```yaml
# ✗ 错
lab:
  iframe:
    ip-allowlist:
      - 127.0.0.1
      - 10.0.0.0/8

# ✓ 对
lab:
  iframe:
    ip-allowlist: "127.0.0.1,10.0.0.0/8"
```

### 3. 启动报 `WeakKeyException: The signing key's size is 208 bits…`

用了 jjwt 的 `Keys.hmacShaKeyFor()` 或 `signWith(key, alg)`，它强制要求密钥 ≥256 bit。
本项目已经改成手工 HS256（`SecretKeySpec`），不会触发。如果自己换回 jjwt，记得把密钥
补到 32 字节以上。

### 4. `iframeLogin` 一直返回「非法ip地址」

说明签名层已经过了，卡在 IP 白名单。排查步骤：

```bash
# ① 看服务端实际看到的 IP
curl -s http://localhost:8080/prod-api/iframeLogin/ipcheck

# ② 对照配置
grep -A2 'ip-allowlist' console/src/main/resources/application.yml
```

**如果前面有 nginx / 负载均衡**：`request.getRemoteAddr()` 拿到的是**直连的那一跳**的 IP，
不是客户端的真实 IP。多层代理的场景下，要么让最外层做真实的来源判定，要么改配置把
代理的 IP 段加进白名单 —— **但不要改成读 `X-Forwarded-For`**，那个头可以伪造。

### 5. `iframeLogin` 返回「非法访问」

签名没带或算错。三个头缺一不可，且 `dynamicHeader` 必须是：

```
base64url( HMAC-SHA256( key=fingerprint, msg=timestamp ) )
```

注意：**HMAC 的 key 就是 fingerprint 本身**，不需要任何服务端下发的密钥。
时间戳要和服务器时间对齐（默认窗口 300 秒）。

### 6. 聚合网关的出款接口一直返回「商户号不正确」

`channelAndMerchantWeightMap` 的结构不对。正确格式：

```json
{"63": [{"merchantId": 72, "weight": 100}]}
```

外层 key 是 `channelId`（字符串），value 是数组，数组元素里要有 `merchantId`。

### 7. 出款接口返回「可用余额不足」

这是预期的余额校验。想确认商户当前余额：

```bash
curl -s 'http://localhost:8080/api/plat/payment/merchantAndPaymentChannels?bizType=1&operatorId=10000' \
  | jq '.data.merchants[] | {merchantId, merchantName, balance}'
```

### 8. Shiro 的 rememberMe 一直解不开

先确认加密模式。Shiro 1.4.2 默认是 **GCM**，不是 CBC：

```bash
# 带一个任意 rememberMe 值
curl -sI http://localhost:8080/api/plat/payment/merchant/list \
  -H 'Cookie: rememberMe=test' | grep -i set-cookie
```

- 回 `rememberMe=deleteMe` → 解密失败（密钥或模式不对）
- **不回 deleteMe** → 密钥正确

详见 [CONFIG.md 的「关于加密模式」](CONFIG.md#关于加密模式重要)。

---

## 备份

### 数据库

```bash
docker exec lab-mysql mysqldump -uroot -p'<pwd>' \
  --single-transaction --routines payment_lab \
  | gzip > backup-$(date +%Y%m%d).sql.gz
```

### 配置

`application.yml` 里的敏感项（数据库口令、JWT 密钥）建议放配置中心或环境变量，
不要跟代码一起提交。

---

## 监控指标

建议接入：

| 指标 | 来源 |
|---|---|
| JVM 堆 / GC | `/actuator/metrics/jvm.*` |
| 连接池使用率 | Druid 监控页 `/druid/datasource.json` |
| 接口 QPS / 耗时 | openresty 访问日志 或 Spring Boot Actuator |
| 出款成功率 | 业务侧埋点（`createThirdProxyWithdrawOrder` 的返回码分布） |
| 登录失败率 | 业务侧埋点 |

---

## 扩容

两个模块都是无状态的（会话在 token 里），可以直接多实例 + 负载均衡。

**注意**：

- `lab.iframe.ip-allowlist` 要包含负载均衡的出口 IP
- 如果要保持 rememberMe 跨实例有效，`lab.shiro.random-cipher-key` 必须改成 `false`，
  并且所有实例用同一个固定密钥（否则每个实例启动时生成的密钥不同，会话不互通）

---

## 安全巡检（建议每周）

```bash
# 1. 检查是否有未授权的 IP 白名单条目
curl -s -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/prod-api/agent/ipgroup/list | jq

# 2. 检查 actuator 是否被意外暴露
curl -s http://localhost:8080/actuator/env | head -c 200

# 3. 检查 Druid 监控页
curl -s -o /dev/null -w '%{http_code}\n' http://localhost:8080/druid/index.html

# 4. 检查网关鉴权是否关闭
curl -s -o /dev/null -w '%{http_code}\n' \
  'http://localhost:8080/api/plat/payment/merchantAndPaymentChannels?bizType=1&operatorId=10000'
# 200 = 鉴权关闭，需要立刻确认是不是故意的
```

---

## 变更记录

| 日期 | 变更 |
|---|---|
| — | 初始版本 |
