-- 初始数据（H2 / MySQL 双兼容，可重复执行）

-- 用户
MERGE INTO sys_user (user_id, user_name, nick_name, dept_name, phonenumber, email, password, status, create_time) KEY(user_id) VALUES
 (1, 'admin',   '超级管理员', '总部',   '138****8000', 'admin@example.com',  '21232f297a57a5a743894a0e4a801fc3', '0', '2025-01-01 00:00:00'),
 (2, 'ops',     '运营',       '运营部', '139****9000', 'ops@example.com',    'e10adc3949ba59abbe56e057f20f883e', '0', '2025-03-12 10:22:31'),
 (3, 'finance', '财务',       '财务部', '137****7000', 'fin@example.com',    'e10adc3949ba59abbe56e057f20f883e', '0', '2025-03-12 10:25:04'),
 (4, 'agent01', '代理一号',   '渠道部', '136****6000', 'agent01@example.com','e10adc3949ba59abbe56e057f20f883e','0', '2025-06-08 09:12:00'),
 (5, 'risk',    '风控',       '风控部', '135****5000', 'risk@example.com',   'e10adc3949ba59abbe56e057f20f883e',   '0', '2025-06-20 14:03:11');

-- 角色
MERGE INTO sys_role (role_id, role_name, role_key, role_sort, status, user_count) KEY(role_id) VALUES
 (1, '超级管理员', 'admin',   1, '0', 1),
 (2, '运营',       'ops',     2, '0', 1),
 (3, '财务',       'finance', 3, '0', 1),
 (4, '代理',       'agent',   4, '0', 1),
 (5, '风控',       'risk',    5, '0', 1);

-- 菜单
MERGE INTO sys_menu (menu_id, menu_name, parent_id, path, menu_type, order_num, perms) KEY(menu_id) VALUES
 (1,  '首页',     0, '/dashboard', 'C', 1, ''),
 (2,  '商户管理', 0, '/merchant',  'M', 2, ''),
 (21, '商户列表', 2, 'list',       'C', 1, 'merchant:list'),
 (22, '通道配置', 2, 'channel',    'C', 2, 'merchant:channel'),
 (3,  '支付管理', 0, '/payment',   'M', 3, ''),
 (31, '支付配置', 3, 'config',     'C', 1, 'payment:config'),
 (32, '代付单',   3, 'withdraw',   'C', 2, 'payment:withdraw'),
 (33, '充值单',   3, 'deposit',    'C', 3, 'payment:deposit'),
 (4,  '会员管理', 0, '/member',    'M', 4, ''),
 (41, '会员列表', 4, 'list',       'C', 1, 'member:list'),
 (5,  '代理管理', 0, '/agent',     'M', 5, ''),
 (51, '代理列表', 5, 'list',       'C', 1, 'agent:list'),
 (52, '佣金结算', 5, 'commission', 'C', 2, 'agent:commission'),
 (6,  '风控管理', 0, '/risk',      'M', 6, ''),
 (61, '风控规则', 6, 'rule',       'C', 1, 'risk:rule'),
 (62, '黑名单',   6, 'blacklist',  'C', 2, 'risk:blacklist'),
 (7,  '系统管理', 0, '/system',    'M', 7, ''),
 (71, '用户管理', 7, 'user',       'C', 1, 'system:user'),
 (72, '角色管理', 7, 'role',       'C', 2, 'system:role'),
 (73, '菜单管理', 7, 'menu',       'C', 3, 'system:menu'),
 (74, '操作日志', 7, 'operlog',    'C', 4, 'system:operlog'),
 (75, '登录日志', 7, 'loginlog',   'C', 5, 'system:loginlog');

-- 操作日志
MERGE INTO oper_log (oper_id, title, business_type, oper_name, dept_name, oper_ip, oper_location, status, oper_time, method) KEY(oper_id) VALUES
 (1, '支付配置',  1, 'admin',   '总部',   '203.0.113.10', '内网', 0, '2026-01-01 09:12:33', 'BusinessController.paymentList()'),
 (2, 'IP 白名单', 2, 'ops',     '运营部', '203.0.113.21', '内网', 0, '2026-01-01 10:02:11', 'BusinessController.ipGroupAdd()'),
 (3, '会员详情',  0, 'ops',     '运营部', '203.0.113.21', '内网', 0, '2026-01-01 10:05:44', 'BusinessController.memberDetail()'),
 (4, '代付单',    1, 'finance', '财务部', '203.0.113.35', '内网', 0, '2026-01-01 11:31:02', 'BusinessController.financeWithdraw()');

-- 登录日志
MERGE INTO login_log (info_id, user_name, ipaddr, login_location, browser, os, status, msg, login_time) KEY(info_id) VALUES
 (1, 'admin',   '203.0.113.10', '内网', 'Chrome 120', 'Windows 10', '0', '登录成功',           '2026-01-01 08:55:12'),
 (2, 'ops',     '203.0.113.21', '内网', 'Chrome 120', 'macOS 14',   '0', '登录成功',           '2026-01-01 09:01:03'),
 (3, 'unknown', '198.51.100.77','未知', 'curl/8.5',   'Linux',      '1', '用户不存在/密码错误', '2026-01-01 09:44:51');

-- 代理
MERGE INTO agent (agent_id, agent_no, agent_name, parent_id, agent_level, real_name, phone, commission_rate, settle_type, status, total_commission, settled_commission, create_time) KEY(agent_id) VALUES
 (1, 'AG20250001', '总代-A', 0, 1, '陈**', '138****1111', 0.35, 'T+1', '0', 128450.32, 98620.10, '2025-01-05 09:00:00'),
 (2, 'AG20250002', '一级-B', 1, 2, '李**', '139****2222', 0.18, 'T+1', '0',  42180.55, 31020.00, '2025-02-11 14:20:00'),
 (3, 'AG20250003', '一级-C', 1, 2, '王**', '137****3333', 0.15, 'T+1', '0',  20330.18, 20330.18, '2025-02-11 14:22:00'),
 (4, 'AG20250004', '二级-D', 2, 3, '赵**', '136****4444', 0.08, 'T+7', '0',   5120.44,  4200.00, '2025-04-01 10:05:00'),
 (5, 'AG20250005', '二级-E', 2, 3, '孙**', '135****5555', 0.08, 'T+7', '1',      0.00,     0.00, '2025-08-19 16:41:00');

-- 佣金
MERGE INTO commission (settle_id, settle_no, agent_id, agent_name, period_start, period_end, order_count, order_amount, rate, commission, status, settle_time) KEY(settle_id) VALUES
 (1, 'ST20260101001', 1, '总代-A', '2025-12-31', '2025-12-31', 1284, 486230.00, 0.35, 1701.80, 1, '2026-01-01 02:00:12'),
 (2, 'ST20260101002', 2, '一级-B', '2025-12-31', '2025-12-31',  402, 152180.00, 0.18,  273.92, 1, '2026-01-01 02:00:14'),
 (3, 'ST20260101003', 3, '一级-C', '2025-12-31', '2025-12-31',   88,  32400.00, 0.15,   48.60, 0, NULL),
 (4, 'ST20260101004', 4, '二级-D', '2025-12-25', '2025-12-31',   16,   4820.00, 0.08,    3.86, 0, NULL);

-- 充值单
MERGE INTO deposit (order_no, member_id, user_name, amount, actual_amount, fee, channel_id, channel_name, merchant_id, merchant_name, address, txid, status, status_text, create_time, finish_time) KEY(order_no) VALUES
 ('DP20260101001', 159, 'u_159', 100.00, 100.00, 0, 63, 'TRC20-direct', 72, 'ChannelA', 'TXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX', '0xabc1f2', 1, '已到账', '2026-01-01 09:12:00', '2026-01-01 09:13:41'),
 ('DP20260101002', 160, 'u_160', 287.88, 287.88, 0, 63, 'TRC20-direct', 100, 'ChannelB', 'TYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYY', '0xdef2a3', 1, '已到账', '2026-01-01 10:02:31', '2026-01-01 10:04:02'),
 ('DP20260101003', 159, 'u_159', 500.00,   0.00, 0, 63, 'TRC20-direct', 72, 'ChannelA', 'TZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZ', NULL, 0, '待支付', '2026-01-01 11:20:08', NULL),
 ('DP20260101004', 160, 'u_160',  50.00,   0.00, 0, 22, 'C2C-wallet',   22, 'ChannelC', NULL, NULL, 2, '已过期', '2026-01-01 08:01:00', '2026-01-01 08:31:00');

-- 风控规则
MERGE INTO risk_rule (rule_id, rule_name, rule_type, threshold, time_window, action_type, priority, status, hit_count, remark) KEY(rule_id) VALUES
 (1, '单笔出款限额',   'AMOUNT_SINGLE',      50000.00, '1笔',       'REVIEW', 1, '0', 12, '单笔出款超过 5 万转人工审核'),
 (2, '日累计出款限额', 'AMOUNT_DAILY',      200000.00, '1天',       'BLOCK',  2, '0',  3, '单会员日累计出款超过 20 万直接拦截'),
 (3, '同 IP 多账号',   'IP_MULTI_ACCOUNT',       5.00, '10分钟',    'REVIEW', 3, '0', 87, '同 IP 10 分钟内超过 5 个账号发起充值'),
 (4, '设备指纹重复',   'DEVICE_REPEAT',          3.00, '1小时',     'REVIEW', 4, '0', 41, '同设备 1 小时内绑定超过 3 个账号'),
 (5, '收款地址黑名单', 'ADDRESS_BLACKLIST',       0.00, '-',         'BLOCK',  0, '0',  6, '收款地址命中黑名单直接拦截'),
 (6, '夜间大额出款',   'TIME_AMOUNT',        10000.00, '02:00-06:00','REVIEW', 5, '1',  0, '已停用');

-- 黑名单
MERGE INTO risk_blacklist (id, list_type, list_value, reason, source, status, create_by, create_time) KEY(id) VALUES
 (1, 'ADDRESS', 'TQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQ', '涉赌资金归集地址', 'MANUAL', '0', 'risk',   '2025-11-02 14:20:00'),
 (2, 'IP',      '198.51.100.0/24',                     '批量注册来源',     'AUTO',   '0', 'system', '2025-12-11 03:12:41'),
 (3, 'DEVICE',  'fp_9f3a2c8b1e',                       '同设备多账号套利', 'AUTO',   '0', 'system', '2025-12-20 22:05:13'),
 (4, 'MEMBER',  'u_160',                               '异常出款',         'MANUAL', '1', 'risk',   '2026-01-01 10:22:00');

-- 命中记录
MERGE INTO risk_hit (hit_id, rule_id, rule_name, target, target_type, detail, action_type, handle_status, hit_time) KEY(hit_id) VALUES
 (1, 3, '同 IP 多账号',   '198.51.100.77', 'IP',      '10 分钟内 7 个账号从同 IP 发起充值', 'REVIEW', 0, '2026-01-01 03:12:44'),
 (2, 1, '单笔出款限额',   'u_159',         'MEMBER',  '单笔出款 80000.00 超过阈值 50000.00', 'REVIEW', 1, '2026-01-01 09:41:02'),
 (3, 5, '收款地址黑名单', 'TQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQ', 'ADDRESS', '出款目标地址命中黑名单', 'BLOCK', 2, '2026-01-01 11:03:19');

-- 商户
MERGE INTO merchant (merchant_id, merchant_no, merchant_name, channel_id, check_owner, balance, mch_key, notify_url, df_gateway) KEY(merchant_id) VALUES
 (72,  '121138', 'ChannelA', 63, 0, 5721.62, 'flag{lab_mchkey_channelA_72}',  'https://198.51.100.92/api/pay/notify', 'https://198.51.100.92/api/pay'),
 (100, '121139', 'ChannelB', 63, 0, 2600.00, 'flag{lab_mchkey_channelB_100}', 'https://198.51.100.92/api/pay/notify', 'https://198.51.100.92/api/pay'),
 (22,  '121140', 'ChannelC', 22, 1,    0.00, 'requires_member_wallet',        '', ''),
 (19,  '121141', 'ChannelD', 19, 1,    0.00, 'requires_member_wallet',        '', ''),
 (93,  '121142', 'ChannelE', 93, 1,    0.00, 'requires_member_wallet',        '', '');

-- 通道
MERGE INTO pay_channel (channel_id, channel_name, min_amt, max_amt) KEY(channel_id) VALUES
 (63, 'TRC20-direct', 100, 50000),
 (22, 'C2C-wallet',    10,  5000),
 (19, 'C2C-wallet-2',  10,  5000),
 (93, 'C2C-wallet-3',  10,  5000);

-- 系统配置
MERGE INTO sys_config (config_id, config_key, config_name, config_value) KEY(config_id) VALUES
 (101, 'pay_channel_config', '支付通道配置', '{"channels":[{"merchantId":72,"mchId":"121138","mchKey":"flag{lab_sysconfig_mchkey}"}],"dfGateway":"https://198.51.100.92/api/pay"}'),
 (102, 'pay_notify_url',     '支付回调地址', 'https://198.51.100.92/api/pay/notify'),
 (103, 'pay_ip_whitelist',   '回调 IP 白名单', '10.254.30.0/24,203.0.113.0/24'),
 (104, 'lab_jwt_secret',     'JWT 密钥',      'abcdefghijklmnopqrstuvwxyz');

-- IP 白名单
MERGE INTO ip_group (id, group_name, ip_list, remark) KEY(id) VALUES
 (1, 'iframe-relay', '10.254.30.0/24', 'iframe 调用的来源段'),
 (2, 'ops-egress',   '203.0.113.0/24', '运维出口');

-- 会员
MERGE INTO member (member_id, username, real_name, identity_id, phone, email, balance, bind_google) KEY(member_id) VALUES
 (159, 'u_159', '张**', '1101**********1234', '138****8000', 'u***@example.internal',  65.44, 0),
 (160, 'u_160', '李**', '3101**********5678', '139****9000', 'v***@example.internal',   0.00, 0);

-- 代付单
MERGE INTO withdraw_order (id, order_no, operator_id, member_id, amount, fee, bank_account, channel_id, merchant_id, third_order_no, status, create_time) KEY(id) VALUES
 (1, 'WD20260101001', 10000, '159', 100.00, 2.00, 'TXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX', 63, 72, '10001', 1, '2026-01-01 09:20:00'),
 (2, 'WD20260101002', 10000, '160', 500.00, 2.00, 'TYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYY', 63, 72, '10002', 0, '2026-01-01 10:40:00');
