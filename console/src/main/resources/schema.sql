-- 数据库结构（H2 MODE=MySQL 与 MySQL 8 双兼容）
-- 字段一律下划线命名，查询时用别名转驼峰

CREATE TABLE IF NOT EXISTS sys_user (
    user_id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_name    VARCHAR(64)  NOT NULL,
    nick_name    VARCHAR(64),
    dept_name    VARCHAR(64),
    phonenumber  VARCHAR(32),
    email        VARCHAR(128),
    status       VARCHAR(2)   DEFAULT '0',
    create_time  VARCHAR(32)
);

CREATE TABLE IF NOT EXISTS sys_role (
    role_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
    role_name   VARCHAR(64),
    role_key    VARCHAR(64),
    role_sort   INT DEFAULT 0,
    status      VARCHAR(2) DEFAULT '0',
    user_count  INT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS sys_menu (
    menu_id    BIGINT AUTO_INCREMENT PRIMARY KEY,
    menu_name  VARCHAR(64),
    parent_id  BIGINT DEFAULT 0,
    path       VARCHAR(128),
    menu_type  VARCHAR(2),
    order_num  INT DEFAULT 0,
    perms      VARCHAR(128)
);

CREATE TABLE IF NOT EXISTS oper_log (
    oper_id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    title         VARCHAR(64),
    business_type INT DEFAULT 0,
    oper_name     VARCHAR(64),
    dept_name     VARCHAR(64),
    oper_ip       VARCHAR(64),
    oper_location VARCHAR(64),
    status        INT DEFAULT 0,
    oper_time     VARCHAR(32),
    method        VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS login_log (
    info_id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_name      VARCHAR(64),
    ipaddr         VARCHAR(64),
    login_location VARCHAR(64),
    browser        VARCHAR(64),
    os             VARCHAR(64),
    status         VARCHAR(2),
    msg            VARCHAR(128),
    login_time     VARCHAR(32)
);

CREATE TABLE IF NOT EXISTS agent (
    agent_id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    agent_no           VARCHAR(32),
    agent_name         VARCHAR(64),
    parent_id          BIGINT DEFAULT 0,
    agent_level        INT DEFAULT 1,
    real_name          VARCHAR(64),
    phone              VARCHAR(32),
    commission_rate    DOUBLE DEFAULT 0,
    settle_type        VARCHAR(16),
    status             VARCHAR(2) DEFAULT '0',
    total_commission   DOUBLE DEFAULT 0,
    settled_commission DOUBLE DEFAULT 0,
    create_time        VARCHAR(32)
);

CREATE TABLE IF NOT EXISTS commission (
    settle_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
    settle_no     VARCHAR(32),
    agent_id      BIGINT,
    agent_name    VARCHAR(64),
    period_start  VARCHAR(16),
    period_end    VARCHAR(16),
    order_count   INT DEFAULT 0,
    order_amount  DOUBLE DEFAULT 0,
    rate          DOUBLE DEFAULT 0,
    commission    DOUBLE DEFAULT 0,
    status        INT DEFAULT 0,
    settle_time   VARCHAR(32)
);

CREATE TABLE IF NOT EXISTS deposit (
    order_no      VARCHAR(64) PRIMARY KEY,
    member_id     BIGINT,
    user_name     VARCHAR(64),
    amount        DOUBLE DEFAULT 0,
    actual_amount DOUBLE DEFAULT 0,
    fee           DOUBLE DEFAULT 0,
    channel_id    INT,
    channel_name  VARCHAR(64),
    merchant_id   INT,
    merchant_name VARCHAR(64),
    address       VARCHAR(128),
    txid          VARCHAR(128),
    status        INT DEFAULT 0,
    status_text   VARCHAR(32),
    create_time   VARCHAR(32),
    finish_time   VARCHAR(32)
);

CREATE TABLE IF NOT EXISTS risk_rule (
    rule_id    BIGINT AUTO_INCREMENT PRIMARY KEY,
    rule_name  VARCHAR(64),
    rule_type  VARCHAR(32),
    threshold  DOUBLE DEFAULT 0,
    time_window  VARCHAR(32),
    action_type  VARCHAR(16),
    priority   INT DEFAULT 0,
    status     VARCHAR(2) DEFAULT '0',
    hit_count  INT DEFAULT 0,
    remark     VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS risk_blacklist (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    list_type   VARCHAR(16),
    list_value  VARCHAR(255),
    reason      VARCHAR(255),
    source      VARCHAR(16),
    status      VARCHAR(2) DEFAULT '0',
    create_by   VARCHAR(64),
    create_time VARCHAR(32)
);

CREATE TABLE IF NOT EXISTS risk_hit (
    hit_id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    rule_id       BIGINT,
    rule_name     VARCHAR(64),
    target        VARCHAR(255),
    target_type   VARCHAR(16),
    detail        VARCHAR(255),
    action_type   VARCHAR(16),
    handle_status INT DEFAULT 0,
    hit_time      VARCHAR(32),
    handle_by     VARCHAR(64),
    handle_time   VARCHAR(32)
);

CREATE TABLE IF NOT EXISTS merchant (
    merchant_id   BIGINT PRIMARY KEY,
    merchant_no   VARCHAR(32),
    merchant_name VARCHAR(64),
    channel_id    INT,
    check_owner   INT DEFAULT 0,
    balance       DOUBLE DEFAULT 0,
    mch_key       VARCHAR(128),
    notify_url    VARCHAR(255),
    df_gateway    VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS pay_channel (
    channel_id   INT PRIMARY KEY,
    channel_name VARCHAR(64),
    min_amt      DOUBLE DEFAULT 100,
    max_amt      DOUBLE DEFAULT 50000
);

CREATE TABLE IF NOT EXISTS sys_config (
    config_id    BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_key   VARCHAR(128),
    config_name  VARCHAR(128),
    config_value TEXT
);

CREATE TABLE IF NOT EXISTS ip_group (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_name VARCHAR(64),
    ip_list    VARCHAR(1024),
    remark     VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS member (
    member_id   BIGINT PRIMARY KEY,
    username    VARCHAR(64),
    real_name   VARCHAR(64),
    identity_id VARCHAR(32),
    phone       VARCHAR(32),
    email       VARCHAR(128),
    balance     DOUBLE DEFAULT 0,
    bind_google INT DEFAULT 0
);

CREATE TABLE IF NOT EXISTS withdraw_order (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no       VARCHAR(64),
    operator_id    INT,
    member_id      VARCHAR(64),
    amount         DOUBLE DEFAULT 0,
    fee            DOUBLE DEFAULT 0,
    bank_account   VARCHAR(128),
    channel_id     INT,
    merchant_id    INT,
    third_order_no VARCHAR(64),
    status         INT DEFAULT 0,
    create_time    VARCHAR(32)
);
