package com.lab.console.controller;

import com.lab.console.security.JwtUtil;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.*;

/**
 * 业务面 —— 支付配置 / 系统配置 / IP 白名单 / 会员 / 财务。
 *
 * ⚠️ 这些接口在真实系统里**全部 401**（因为它们不在 permitAll 白名单里）。
 * 本项目里也一样：只有带了合法 admin JWT 才能读。
 *
 * 蓝队的目标是拿到其中任意一份包含 mchKey 的配置。
 */
@RestController
@RequestMapping("/prod-api")
public class BusinessController {

    private final JwtUtil jwtUtil;

    // 内存里的"IP 白名单表" —— 进后台后调 add 就能给自己开口
    private final List<Map<String, Object>> ipGroups = new ArrayList<>(List.of(
            Map.of("id", 1, "groupName", "iframe-relay", "ipList", "198.51.100.0/24", "remark", "iframe 调用的来源段"),
            Map.of("id", 2, "groupName", "ops-egress", "ipList", "203.0.113.0/24", "remark", "运维出口")
    ));

    public BusinessController(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    // ══════════════════════════════════════════════════════
    // 支付配置 —— 最终目标
    // ══════════════════════════════════════════════════════
    @GetMapping("/payment/payment/list")
    public Map<String, Object> paymentList(@RequestHeader(value = "Authorization", required = false) String auth) {
        if (unauth(auth)) return deny("/payment/payment/list");
        List<Map<String, Object>> rows = List.of(
                Map.of("merchantId", 72, "merchantNo", "121138", "merchantName", "ChannelA",
                        "channelId", 63, "mchKey", "flag{lab_mchkey_channelA_72}",
                        "notifyUrl", "https://pay.example.test/api/pay/notify",
                        "dfGateway", "https://pay.example.test/api/pay", "balance", 5721.62),
                Map.of("merchantId", 100, "merchantNo", "121139", "merchantName", "ChannelB",
                        "channelId", 63, "mchKey", "flag{lab_mchkey_channelB_100}",
                        "notifyUrl", "https://pay.example.test/api/pay/notify",
                        "dfGateway", "https://pay.example.test/api/pay", "balance", 2600.00),
                Map.of("merchantId", 22, "merchantNo", "121140", "merchantName", "ChannelC",
                        "channelId", 22, "mchKey", "requires_member_wallet",
                        "notifyUrl", "", "dfGateway", "", "balance", 0.00)
        );
        return Map.of("code", 200, "msg", "success", "total", rows.size(), "rows", rows);
    }

    @GetMapping("/payment/merchantInfo")
    public Map<String, Object> merchantInfo(@RequestHeader(value = "Authorization", required = false) String auth) {
        if (unauth(auth)) return deny("/payment/merchantInfo");
        Map<String, Object> data = new HashMap<>();
        data.put("merchantName", "Demo Merchant");
        data.put("merchantNo", "121138");
        data.put("mchId", "121138");
        data.put("mchKey", "flag{lab_final_mchkey_reached}");
        data.put("notifyUrl", "https://pay.example.test/api/pay/notify");
        data.put("dfGateway", "https://pay.example.test/api/pay");
        return Map.of("code", 200, "data", data, "flag", "flag{lab_payment_config_pwned}");
    }

    // ══════════════════════════════════════════════════════
    // 系统配置
    // ══════════════════════════════════════════════════════
    private static final Map<String, Map<String, Object>> SYS_CONFIG = Map.of(
            "pay_channel_config", Map.of(
                    "configId", 101, "configKey", "pay_channel_config", "configName", "支付通道配置",
                    "configValue", "{\"channels\":[{\"merchantId\":72,\"mchId\":\"121138\",\"mchKey\":\"flag{lab_sysconfig_mchkey}\"}],\"dfGateway\":\"https://pay.example.test/api/pay\"}"),
            "pay_notify_url", Map.of(
                    "configId", 102, "configKey", "pay_notify_url", "configName", "支付回调地址",
                    "configValue", "https://pay.example.test/api/pay/notify"),
            "pay_ip_whitelist", Map.of(
                    "configId", 103, "configKey", "pay_ip_whitelist", "configName", "回调 IP 白名单",
                    "configValue", "198.51.100.0/24,203.0.113.0/24")
    );

    @GetMapping("/sysConfig/getConfigByKey")
    public Map<String, Object> getConfigByKey(@RequestHeader(value = "Authorization", required = false) String auth,
                                              @RequestParam(defaultValue = "") String key) {
        if (unauth(auth)) return deny("/sysConfig/getConfigByKey");
        Map<String, Object> cfg = SYS_CONFIG.get(key);
        if (cfg == null) return Map.of("code", 500, "msg", "配置项 " + key + " 不存在");
        Map<String, Object> m = new HashMap<>();
        m.put("code", 200);
        m.put("msg", "success");
        m.put("data", cfg);
        if (key.contains("pay")) m.put("flag", "flag{lab_sysconfig_leak}");
        return m;
    }

    @GetMapping("/sysConfig/list")
    public Map<String, Object> configList(@RequestHeader(value = "Authorization", required = false) String auth) {
        if (unauth(auth)) return deny("/sysConfig/list");
        return Map.of("code", 200, "rows", new ArrayList<>(SYS_CONFIG.values()), "total", SYS_CONFIG.size());
    }

    // ══════════════════════════════════════════════════════
    // IP 白名单（进后台后可永久开口）
    // ══════════════════════════════════════════════════════
    @GetMapping("/agent/ipgroup/list")
    public Map<String, Object> ipGroupList(@RequestHeader(value = "Authorization", required = false) String auth) {
        if (unauth(auth)) return deny("/agent/ipgroup/list");
        return Map.of("code", 200, "rows", ipGroups, "total", ipGroups.size());
    }

    @PostMapping("/agent/ipgroup/add")
    public Map<String, Object> ipGroupAdd(@RequestHeader(value = "Authorization", required = false) String auth,
                                          @RequestBody Map<String, Object> body) {
        if (unauth(auth)) return deny("/agent/ipgroup/add");
        Object ip = body.getOrDefault("ip", body.get("ipList"));
        if (ip == null || ip.toString().isBlank()) {
            return Map.of("code", 500, "msg", "ip 不能为空");
        }
        Map<String, Object> row = new HashMap<>();
        row.put("id", ipGroups.size() + 1);
        row.put("groupName", body.getOrDefault("groupName", "lab"));
        row.put("ipList", ip.toString());
        row.put("remark", "added via API");
        ipGroups.add(row);
        return Map.of("code", 200, "msg", "操作成功", "data", row, "flag", "flag{lab_ipgroup_self_add}");
    }

    // ══════════════════════════════════════════════════════
    // 会员 / 财务
    // ══════════════════════════════════════════════════════
    @GetMapping("/member/member/list")
    public Map<String, Object> memberList(@RequestHeader(value = "Authorization", required = false) String auth) {
        if (unauth(auth)) return deny("/member/member/list");
        return Map.of("code", 200, "rows", List.of(
                Map.of("memberId", 159, "username", "u_159", "balance", 65.44, "status", "0"),
                Map.of("memberId", 160, "username", "u_160", "balance", 0.00, "status", "0")
        ), "total", 2);
    }

    /**
     * ⚠️ 真实系统里这个接口一次返回 KYC 三要素 —— 姓名 / 身份证 / 手机 / 邮箱。
     * 本项目里保留（数据是假的）。
     */
    @GetMapping("/member/member/detail")
    public Map<String, Object> memberDetail(@RequestHeader(value = "Authorization", required = false) String auth,
                                            @RequestParam(defaultValue = "159") long memberId) {
        if (unauth(auth)) return deny("/member/member/detail");
        Map<String, Object> data = new HashMap<>();
        data.put("memberId", memberId);
        data.put("realName", "张**");
        data.put("identityId", "1101**********1234");
        data.put("phone", "138****8000");
        data.put("email", "u***@example.test");
        data.put("balance", 65.44);
        data.put("bindGoogleKey", 0);
        return Map.of("code", 200, "data", data, "flag", "flag{lab_kyc_pii_leak}");
    }

    @PostMapping("/member/member/resetPayPwd")
    public Map<String, Object> resetPayPwd(@RequestHeader(value = "Authorization", required = false) String auth) {
        if (unauth(auth)) return deny("/member/member/resetPayPwd");
        return Map.of("code", 200, "msg", "操作成功", "flag", "flag{lab_reset_paypwd}");
    }

    @GetMapping("/finance/withdraw")
    public Map<String, Object> financeWithdraw(@RequestHeader(value = "Authorization", required = false) String auth) {
        if (unauth(auth)) return deny("/finance/withdraw");
        return Map.of("code", 200, "rows", List.of(
                Map.of("orderNo", "WD20260101001", "amount", 100.00, "status", 1,
                        "address", "TXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"),
                Map.of("orderNo", "WD20260101002", "amount", 500.00, "status", 0,
                        "address", "TXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX")
        ));
    }

    // ── helpers ───────────────────────────────────────────
    private boolean unauth(String auth) {
        return auth == null || !auth.startsWith("Bearer ") || jwtUtil.parseUsername(auth.substring(7).trim()) == null;
    }

    private Map<String, Object> deny(String path) {
        return Map.of("msg", "请求访问：" + path + "，认证失败，无法访问系统资源，请登录后重试!", "code", 401);
    }
}
