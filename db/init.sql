-- 数据库初始化
-- ⚠️ 所有数据为假值。表结构照真实支付后台设计，方便蓝队练 SQL 注入与数据面审计。

CREATE DATABASE IF NOT EXISTS payment_lab DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE payment_lab;

-- ── 后台账号 ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS sys_user (
    user_id     BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_name   VARCHAR(64)  NOT NULL UNIQUE,
    nick_name   VARCHAR(64),
    password    VARCHAR(128) NOT NULL COMMENT 'md5hex',
    status      CHAR(1) DEFAULT '0',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO sys_user (user_name, nick_name, password) VALUES
  ('admin',   '超级管理员', '21232f297a57a5a743894a0e4a801fc3'),  -- md5("admin")
  ('agent01', '代理一号',   'e10adc3949ba59abbe56e057f20f883e'),  -- md5("123456")
  ('finance', '财务',       'e10adc3949ba59abbe56e057f20f883e')
ON DUPLICATE KEY UPDATE nick_name = VALUES(nick_name);

-- ── 支付通道 ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS pay_channel (
    channel_id    INT PRIMARY KEY,
    channel_name  VARCHAR(64),
    min_amt       DECIMAL(18,4) DEFAULT 100,
    max_amt       DECIMAL(18,4) DEFAULT 50000,
    check_owner   TINYINT DEFAULT 1 COMMENT '1=要求收款地址是本站会员钱包'
);

INSERT INTO pay_channel VALUES
  (63, 'TRC20-direct', 100, 50000, 0),
  (22, 'C2C-wallet',    10,  5000, 1),
  (19, 'C2C-wallet-2',  10,  5000, 1),
  (93, 'C2C-wallet-3',  10,  5000, 1)
ON DUPLICATE KEY UPDATE channel_name = VALUES(channel_name);

-- ── 商户 ────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS pay_merchant (
    merchant_id   INT PRIMARY KEY,
    merchant_no   VARCHAR(32),
    merchant_name VARCHAR(64),
    channel_id    INT,
    mch_key       VARCHAR(128),
    notify_url    VARCHAR(255),
    df_gateway    VARCHAR(255),
    balance       DECIMAL(18,4) DEFAULT 0
);

INSERT INTO pay_merchant VALUES
  (72,  '121138', 'ChannelA', 63, 'flag{lab_mchkey_channelA_72}', 'https://pay.example.test/api/pay/notify', 'https://pay.example.test/api/pay', 5721.6200),
  (100, '121139', 'ChannelB', 63, 'flag{lab_mchkey_channelB_100}', 'https://pay.example.test/api/pay/notify', 'https://pay.example.test/api/pay', 2600.0000),
  (22,  '121140', 'ChannelC', 22, 'requires_member_wallet', '', '', 0),
  (19,  '121141', 'ChannelD', 19, 'requires_member_wallet', '', '', 0),
  (93,  '121142', 'ChannelE', 93, 'requires_member_wallet', '', '', 0)
ON DUPLICATE KEY UPDATE balance = VALUES(balance);

-- ── 系统配置 ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS sys_config (
    config_id    INT PRIMARY KEY,
    config_key   VARCHAR(128) NOT NULL UNIQUE,
    config_name  VARCHAR(128),
    config_value TEXT
);

INSERT INTO sys_config VALUES
  (101, 'pay_channel_config', '支付通道配置',
   '{"channels":[{"merchantId":72,"mchId":"121138","mchKey":"flag{lab_sysconfig_mchkey}"}],"dfGateway":"https://pay.example.test/api/pay"}'),
  (102, 'pay_notify_url', '支付回调地址', 'https://pay.example.test/api/pay/notify'),
  (103, 'pay_ip_whitelist', '回调 IP 白名单', '198.51.100.0/24,203.0.113.0/24'),
  (104, 'lab_jwt_secret', 'JWT 密钥', 'abcdefghijklmnopqrstuvwxyz')
ON DUPLICATE KEY UPDATE config_value = VALUES(config_value);

-- ── IP 白名单 ────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS agent_ip_group (
    id          INT PRIMARY KEY AUTO_INCREMENT,
    group_name  VARCHAR(64),
    ip_list     VARCHAR(1024),
    remark      VARCHAR(255)
);

INSERT INTO agent_ip_group (group_name, ip_list, remark) VALUES
  ('iframe-relay', '198.51.100.0/24',     'iframe 调用的来源段'),
  ('ops-egress',   '203.0.113.0/24',     '运维出口');

-- ── 会员（KYC 三要素，本项目里是假值）──────────────────────
CREATE TABLE IF NOT EXISTS member (
    memberId       BIGINT PRIMARY KEY AUTO_INCREMENT,
    username       VARCHAR(64),
    real_name      VARCHAR(64),
    identity_id    VARCHAR(32),
    phone          VARCHAR(32),
    email          VARCHAR(64),
    balance        DECIMAL(18,4) DEFAULT 0,
    bind_google    TINYINT DEFAULT 0,
    create_time    DATETIME DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO member (memberId, username, real_name, identity_id, phone, email, balance) VALUES
  (159, 'u_159', '张**', '1101**********1234', '138****8000', 'u***@example.test', 65.4400),
  (160, 'u_160', '李**', '3101**********5678', '139****9000', 'v***@example.test', 0)
ON DUPLICATE KEY UPDATE balance = VALUES(balance);

-- ── 出款单 ───────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS withdraw_order (
    id             BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_no       VARCHAR(64) UNIQUE,
    operator_id    INT,
    memberId         VARCHAR(64),
    amount         DECIMAL(18,4),
    fee            DECIMAL(18,4),
    bank_account   VARCHAR(128),
    channel_id     INT,
    merchant_id    INT,
    third_order_no VARCHAR(64),
    status         TINYINT DEFAULT 0,
    create_time    DATETIME DEFAULT CURRENT_TIMESTAMP
);

-- 给蓝队一个靶标账号（只读，方便练 SQL 注入）
CREATE USER IF NOT EXISTS 'lab_ro'@'%' IDENTIFIED BY 'lab_ro';
GRANT SELECT ON payment_lab.* TO 'lab_ro'@'%';
FLUSH PRIVILEGES;
