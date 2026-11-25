package com.lab.console.controller;

import com.lab.console.security.JwtUtil;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 业务面：支付配置 / 系统配置 / IP 白名单 / 会员 / 财务。
 *
 * 全部走数据库。
 */
@RestController
@RequestMapping("/prod-api")
public class BusinessController {

    private final JwtUtil jwtUtil;
    private final JdbcTemplate jdbc;

    public BusinessController(JwtUtil jwtUtil, JdbcTemplate jdbc) {
        this.jwtUtil = jwtUtil;
        this.jdbc = jdbc;
    }

    // ══════════════════════════════════════════════════════
    // 支付配置
    // ══════════════════════════════════════════════════════
    @GetMapping("/payment/payment/list")
    public Map<String, Object> paymentList(@RequestHeader(value = "Authorization", required = false) String auth) {
        if (unauth(auth)) return deny("/payment/payment/list");
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT merchant_id AS \"merchantId\", merchant_no AS \"merchantNo\", " +
                "merchant_name AS \"merchantName\", channel_id AS \"channelId\", " +
                "mch_key AS \"mchKey\", notify_url AS \"notifyUrl\", df_gateway AS \"dfGateway\", " +
                "balance FROM merchant ORDER BY merchant_id");
        return Map.of("code", 200, "msg", "success", "total", rows.size(), "rows", rows);
    }

    @GetMapping("/payment/merchantInfo")
    public Map<String, Object> merchantInfo(@RequestHeader(value = "Authorization", required = false) String auth,
                                            @RequestParam(defaultValue = "72") int merchantId) {
        if (unauth(auth)) return deny("/payment/merchantInfo");
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT merchant_id AS \"merchantId\", merchant_no AS \"merchantNo\", " +
                "merchant_name AS \"merchantName\", channel_id AS \"channelId\", " +
                "mch_key AS \"mchKey\", notify_url AS \"notifyUrl\", df_gateway AS \"dfGateway\", " +
                "balance FROM merchant WHERE merchant_id = ?", merchantId);
        if (rows.isEmpty()) return Map.of("code", 500, "msg", "商户不存在");
        return Map.of("code", 200, "data", rows.get(0), "flag", "flag{lab_payment_config_pwned}");
    }

    // ══════════════════════════════════════════════════════
    // 系统配置
    // ══════════════════════════════════════════════════════
    @GetMapping("/sysConfig/getConfigByKey")
    public Map<String, Object> getConfigByKey(@RequestHeader(value = "Authorization", required = false) String auth,
                                              @RequestParam(defaultValue = "") String key) {
        if (unauth(auth)) return deny("/sysConfig/getConfigByKey");
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT config_id AS \"configId\", config_key AS \"configKey\", " +
                "config_name AS \"configName\", config_value AS \"configValue\" " +
                "FROM sys_config WHERE config_key = ?", key);
        if (rows.isEmpty()) return Map.of("code", 500, "msg", "配置项 " + key + " 不存在");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", 200);
        m.put("msg", "success");
        m.put("data", rows.get(0));
        if (key.contains("pay")) m.put("flag", "flag{lab_sysconfig_leak}");
        return m;
    }

    @GetMapping("/sysConfig/list")
    public Map<String, Object> configList(@RequestHeader(value = "Authorization", required = false) String auth) {
        if (unauth(auth)) return deny("/sysConfig/list");
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT config_id AS \"configId\", config_key AS \"configKey\", " +
                "config_name AS \"configName\", config_value AS \"configValue\" FROM sys_config ORDER BY config_id");
        return Map.of("code", 200, "rows", rows, "total", rows.size());
    }

    // ══════════════════════════════════════════════════════
    // IP 白名单
    // ══════════════════════════════════════════════════════
    @GetMapping("/agent/ipgroup/list")
    public Map<String, Object> ipGroupList(@RequestHeader(value = "Authorization", required = false) String auth) {
        if (unauth(auth)) return deny("/agent/ipgroup/list");
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, group_name AS \"groupName\", ip_list AS \"ipList\", remark FROM ip_group ORDER BY id");
        return Map.of("code", 200, "rows", rows, "total", rows.size());
    }

    @PostMapping("/agent/ipgroup/add")
    public Map<String, Object> ipGroupAdd(@RequestHeader(value = "Authorization", required = false) String auth,
                                          @RequestBody Map<String, Object> body) {
        if (unauth(auth)) return deny("/agent/ipgroup/add");
        Object ip = body.getOrDefault("ip", body.get("ipList"));
        if (ip == null || ip.toString().isBlank()) {
            return Map.of("code", 500, "msg", "ip 不能为空");
        }
        try {
            jdbc.update("INSERT INTO ip_group(group_name, ip_list, remark) VALUES(?,?,?)",
                    body.getOrDefault("groupName", "lab"),
                    ip.toString(),
                    "added via API");
        } catch (Exception e) {
            return Map.of("code", 500, "msg", "新增失败: " + e.getMessage());
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT id, group_name AS \"groupName\", ip_list AS \"ipList\", remark FROM ip_group ORDER BY id DESC LIMIT 1");
        return Map.of("code", 200, "msg", "操作成功",
                      "data", rows.isEmpty() ? Map.of() : rows.get(0),
                      "flag", "flag{lab_ipgroup_self_add}");
    }

    // ══════════════════════════════════════════════════════
    // 会员
    // ══════════════════════════════════════════════════════
    @GetMapping("/member/member/list")
    public Map<String, Object> memberList(@RequestHeader(value = "Authorization", required = false) String auth) {
        if (unauth(auth)) return deny("/member/member/list");
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT member_id AS \"memberId\", username, real_name AS \"realName\", " +
                "phone, balance, bind_google AS \"bindGoogleKey\" FROM member ORDER BY member_id");
        return Map.of("code", 200, "rows", rows, "total", rows.size());
    }

    @GetMapping("/member/member/detail")
    public Map<String, Object> memberDetail(@RequestHeader(value = "Authorization", required = false) String auth,
                                            @RequestParam(defaultValue = "159") long memberId) {
        if (unauth(auth)) return deny("/member/member/detail");
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT member_id AS \"memberId\", username, real_name AS \"realName\", " +
                "identity_id AS \"memberId\", phone, email, balance, " +
                "bind_google AS \"bindGoogleKey\" FROM member WHERE member_id = ?", memberId);
        if (rows.isEmpty()) return Map.of("code", 500, "msg", "会员不存在");
        return Map.of("code", 200, "data", rows.get(0), "flag", "flag{lab_kyc_pii_leak}");
    }

    @PostMapping("/member/member/resetPayPwd")
    public Map<String, Object> resetPayPwd(@RequestHeader(value = "Authorization", required = false) String auth,
                                           @RequestBody(required = false) Map<String, Object> body) {
        if (unauth(auth)) return deny("/member/member/resetPayPwd");
        return Map.of("code", 200, "msg", "操作成功", "flag", "flag{lab_reset_paypwd}");
    }

    // ══════════════════════════════════════════════════════
    // 财务
    // ══════════════════════════════════════════════════════
    @GetMapping("/finance/withdraw")
    public Map<String, Object> financeWithdraw(@RequestHeader(value = "Authorization", required = false) String auth) {
        if (unauth(auth)) return deny("/finance/withdraw");
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT order_no AS \"orderNo\", amount, fee, bank_account AS \"bankAccount\", " +
                "channel_id AS \"channelId\", merchant_id AS \"merchantId\", " +
                "third_order_no AS \"thirdOrderNo\", status, create_time AS \"createTime\" " +
                "FROM withdraw_order ORDER BY id DESC");
        return Map.of("code", 200, "rows", rows, "total", rows.size());
    }

    // ── helpers ───────────────────────────────────────────
    private boolean unauth(String auth) {
        return auth == null || !auth.startsWith("Bearer ") || jwtUtil.parseUsername(auth.substring(7).trim()) == null;
    }

    private Map<String, Object> deny(String path) {
        return Map.of("msg", "请求访问：" + path + "，认证失败，无法访问系统资源，请登录后重试!", "code", 401);
    }
}
