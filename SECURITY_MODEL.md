# Security model: payment-console

## System overview
Payment operations backend: an operator/merchant **console** (Spring Boot 2.7, Spring Security + hand-rolled HS256 JWT, JdbcTemplate on MySQL 8 via Druid) and a **payment gateway** (Spring Boot, Apache Shiro 1.4.2) that creates third-party payout (proxy-withdraw) orders against merchant channels. An OpenResty **edge** fronts both. Deployed with `docker-compose.yml`; no separate infrastructure repository exists. Deployment is taken from the in-repo `docker-compose.yml`, `*/Dockerfile` and `nginx/default.conf`.

**User facts (from the threat-model questions):**
- The system is **headed for production**. Treat every flaw as real, even when comments, README or `docs/` describe it as a lab, demo or intentional exercise.
- The edge (port 8080) is reachable from the **internal network / VPN only**, not the public internet.
- Console accounts are held by **employees (operators) and invited merchants and agents**. Authenticated users are therefore not all trusted insiders.
- Critical assets: **payouts** via `createThirdProxyWithdrawOrder` and merchant balances; **channel secrets** (mchKey, notifyUrl, dfGateway); **member KYC PII**; **credentials** (JWT secret, DB and Redis passwords); **deposit crediting** (`/deposit/callback`).

## Components
| Component | Code |
|---|---|
| **Console API** | `console/src/main/java/com/lab/console/controller/**` (Business, Deposit, Agent, Risk, System, Leak controllers), `console/src/main/resources/*.sql` |
| **Console Auth** | `console/.../config/SecurityConfig.java`, `config/IframeIpWhitelist.java`, `security/JwtUtil.java`, `security/JwtAuthFilter.java`, `controller/AuthController.java`, `controller/IframeLoginController.java` |
| **Gateway** | `gateway/src/main/java/**` (ShiroConfig, WithdrawController, MerchantController) |
| **Edge** | `nginx/default.conf` (OpenResty) |
| **Database** | `db/init.sql`, MySQL 8 schema, seed data and DB users |
| **Deployment Config** | `docker-compose.yml`, `*/Dockerfile`, `*/application.yml`, `*/pom.xml`, `console/.../config/LabSeed.java` |

## Trust boundaries and entry points
1. **VPN/internal client to Edge**: anyone on the internal network or VPN. `/api/` and `/druid/` go to the Gateway; everything else goes to the Console. The edge blocks some actuator keywords and forwards `X-Real-IP` and `X-Forwarded-For`.
2. **Edge to Console** (`/prod-api/**`): Spring Security permits all GET and POST `/prod-api/**`, actuator, `/druid/**`, Swagger, and backup/config-style paths (`/backup.sql`, `/.env`, `/.git/**`, `/upload/**`). Authentication is done per controller with `unauth(auth)`, which only checks for a valid JWT. There are no role or ownership checks, so merchants and agents can reach admin functions (system users, roles, risk rules, channel config).
3. **Edge to Gateway** (`/api/**`): Shiro maps `/api/**` and `/**` to `anon`, and `lab.gateway.no-auth: true` skips controller auth. Payout creation and merchant/channel listing are therefore reachable without an account by anyone on the internal network.
4. **Payment provider to Console**: `/prod-api/finance/deposit/callback` marks deposits paid with no signature or source verification.
5. **Iframe parent platform to Console**: `/prod-api/iframeLogin` uses an HMAC keyed by a client-supplied fingerprint, plus a `getRemoteAddr()` IP allowlist. Behind the edge, the allowlist sees the edge's address.
6. **Services to MySQL**: credentials come from `application.yml` and compose.

## Assets (all critical per user)
- Payout flow and merchant balances (`WithdrawController.create`, `pay_merchant`, withdraw tables).
- Deposit status and crediting (`deposit` table, `DepositController.depositCallback`).
- Channel secrets: `mchKey`, `notifyUrl`, `dfGateway` in `pay_merchant` and `sys_config`.
- Member KYC PII: name, ID number, phone, email in member tables.
- Credentials: JWT secret (`lab.jwt.secret` / `sys_config`), DB passwords, Redis password, Druid console credentials, Shiro cipher key, seeded `sys_user` MD5 password hashes.

## Attacker profiles that matter (user-provided)
- **Unauthenticated internal/VPN user**: any host on the internal network or VPN. Reaches the Gateway anonymously and reaches the Console's permitAll routes.
- **Authenticated merchant or agent (invited, external, lower trust)**: holds a console JWT. Tenant isolation, ownership and role checks matter against them.
- **Malicious or compromised operator (employee)**.
- **Forged payment-provider callback**: from anyone who can reach the callback route.
- *Out of profile:* anonymous internet attackers with no VPN access (user: edge is internal/VPN only).

## Existing controls
- JdbcTemplate with `?` placeholders in most queries (e.g. `DepositController.list` builds dynamic SQL but binds values).
- JWT HMAC signature verification in `JwtUtil.parseUsername`.
- Login captcha with TTL in `AuthController.login`. Passwords stored as unsalted MD5 (weak).
- Edge keyword blocks for some actuator paths.
- Iframe IP allowlist (`IframeIpWhitelist`).

## Security invariants
- **Authentication (CWE-287/306)**: every `/prod-api/**` and `/api/**` route except login, captcha and verified callbacks must require authentication at the framework layer (`SecurityConfig` `.authenticated()`, Shiro `authc`), not through opt-in per-controller `unauth()` calls. **Gap:** `SecurityConfig` permitAll on GET/POST `/prod-api/**`, Shiro `anon` on `/**`, and `lab.gateway.no-auth: true`.
- **Authorization / tenant isolation (CWE-639/862/269)**: merchant and agent tokens may only read and modify their own merchant, member, agent and order rows. Admin functions (`SystemController`, `RiskController`, `BusinessController` config/channel endpoints, agent commission settle) need an admin role check. Payout creation must verify that `memberId` and `operatorId` belong to the caller's tenant. **Gap:** no role or ownership checks exist anywhere. `JwtUtil` returns only a username.
- **Payment integrity (CWE-345/840)**: deposit callbacks must be verified (HMAC with the channel key, plus provider IP), and `actualAmount` must not exceed the order amount. Withdrawals must validate `amount > 0` *before* balance checks, deduct the balance atomically, and be idempotent on `orderNo`. **Gap:** `DepositController.depositCallback` has no signature check. `WithdrawController.create` checks the amount late and has no ownership check.
- **Injection (CWE-89)**: SQL goes only through JdbcTemplate/MyBatis-Plus bound parameters. Any `+` concatenation of request values into SQL, ORDER BY or LIKE patterns is a finding.
- **Path traversal / file handling (CWE-22)**: file downloads must canonicalize the path and confirm it stays inside a fixed base directory. **Gap:** `BusinessController.downloadResource` only does string replacement.
- **Sensitive data exposure (CWE-200/312/532)**: `mchKey`, other secrets and full KYC fields must never appear in API responses or logs. Backup, `.env`, `.git` and upload routes (`LeakController`) must not exist. **Gap:** permitAll on those paths, and merchant/channel listings return plaintext keys.
- **Secrets and cryptography (CWE-798/321/327/916)**: the JWT secret must be at least 256 random bits from the environment, never from defaults in `application.yml` or `sys_config`. Passwords use bcrypt/argon2, not MD5. No default credentials (Druid admin, seeded users, DB users) in code. The iframe HMAC must use a server-held key, not the client's `fingerprint`.
- **Management interfaces (CWE-16/668)**: actuator (beyond health), Druid stat views, Swagger/knife4j and the H2 console must be disabled or authenticated in production. **Gap:** all are permitAll or anon.
- **CSRF/CORS (CWE-352/942)**: CSRF is disabled, which is acceptable only for bearer-token APIs. CORS must not reflect arbitrary origins with credentials.
- **Proxy trust (CWE-348)**: the IP allowlist must read the client IP from the trusted edge header, and only for requests from the edge.
- **Vulnerable dependencies (CWE-1104)**: Shiro 1.4.2 has known auth-bypass and deserialization CVEs (e.g. CVE-2020-1957, CVE-2020-11989, CVE-2020-13933). Druid and knife4j versions should be checked.
- **SSRF / outbound (CWE-918)**: payout notify/df gateway URLs come from channel config. Changing them must be admin-only, and outbound calls should be restricted to an allowlist.

## Out of scope / accepted risk (user-provided)
- None. The user asked that every flaw be treated as real. Only the anonymous public-internet attacker is out of profile, because the edge is internal/VPN only.

## Open questions
- Is the iframe parent platform an internal service, and which IPs belong on its allowlist?
- Where do the Redis credentials live in production (they are referenced by `JwtUtil`/`LabSeed` but there is no Redis service in compose)?
