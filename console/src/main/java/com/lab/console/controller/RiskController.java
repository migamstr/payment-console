package com.lab.console.controller;

import com.lab.console.security.JwtUtil;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 风控：规则 / 黑名单 / 命中记录 / 限额配置。
 *
 * 复刻支付后台的标准风控模型：
 *   · 规则引擎（单笔、日累计、频次、设备、地域）
 *   · 黑白名单（IP / 设备 / 收款地址 / 会员）
 *   · 命中后的动作（拦截 / 人工审核 / 降级）
 */
@RestController
@RequestMapping("/prod-api/risk")
public class RiskController {

    private final JwtUtil jwtUtil;

    public RiskController(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    private static final List<Map<String, Object>> RULES = new ArrayList<>(List.of(
            row("ruleId", 1, "ruleName", "单笔出款限额", "ruleType", "AMOUNT_SINGLE",
                "threshold", 50000.00, "window", "1笔", "action", "REVIEW",
                "priority", 1, "status", "0", "hitCount", 12,
                "remark", "单笔出款超过 5 万转人工审核"),
            row("ruleId", 2, "ruleName", "日累计出款限额", "ruleType", "AMOUNT_DAILY",
                "threshold", 200000.00, "window", "1天", "action", "BLOCK",
                "priority", 2, "status", "0", "hitCount", 3,
                "remark", "单会员日累计出款超过 20 万直接拦截"),
            row("ruleId", 3, "ruleName", "同 IP 多账号", "ruleType", "IP_MULTI_ACCOUNT",
                "threshold", 5.00, "window", "10分钟", "action", "REVIEW",
                "priority", 3, "status", "0", "hitCount", 87,
                "remark", "同 IP 10 分钟内超过 5 个账号发起充值"),
            row("ruleId", 4, "ruleName", "设备指纹重复", "ruleType", "DEVICE_REPEAT",
                "threshold", 3.00, "window", "1小时", "action", "REVIEW",
                "priority", 4, "status", "0", "hitCount", 41,
                "remark", "同设备 1 小时内绑定超过 3 个账号"),
            row("ruleId", 5, "ruleName", "收款地址黑名单", "ruleType", "ADDRESS_BLACKLIST",
                "threshold", 0.00, "window", "-", "action", "BLOCK",
                "priority", 0, "status", "0", "hitCount", 6,
                "remark", "收款地址命中黑名单直接拦截"),
            row("ruleId", 6, "ruleName", "夜间大额出款", "ruleType", "TIME_AMOUNT",
                "threshold", 10000.00, "window", "02:00-06:00", "action", "REVIEW",
                "priority", 5, "status", "1", "hitCount", 0,
                "remark", "已停用")
    ));

    private static final List<Map<String, Object>> BLACKLIST = new ArrayList<>(List.of(
            row("id", 1, "listType", "ADDRESS", "value", "TQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQ",
                "reason", "涉赌资金归集地址", "source", "MANUAL", "status", "0",
                "createBy", "risk", "createTime", "2025-11-02 14:20:00"),
            row("id", 2, "listType", "IP", "value", "198.51.100.0/24",
                "reason", "批量注册来源", "source", "AUTO", "status", "0",
                "createBy", "system", "createTime", "2025-12-11 03:12:41"),
            row("id", 3, "listType", "DEVICE", "value", "fp_9f3a2c8b1e",
                "reason", "同设备多账号套利", "source", "AUTO", "status", "0",
                "createBy", "system", "createTime", "2025-12-20 22:05:13"),
            row("id", 4, "listType", "MEMBER", "value", "u_160",
                "reason", "异常出款", "source", "MANUAL", "status", "1",
                "createBy", "risk", "createTime", "2026-01-01 10:22:00")
    ));

    private static final List<Map<String, Object>> HITS = new ArrayList<>(List.of(
            row("hitId", 1, "ruleId", 3, "ruleName", "同 IP 多账号",
                "target", "198.51.100.77", "targetType", "IP",
                "detail", "10 分钟内 7 个账号从同 IP 发起充值", "action", "REVIEW",
                "handleStatus", 0, "hitTime", "2026-01-01 03:12:44"),
            row("hitId", 2, "ruleId", 1, "ruleName", "单笔出款限额",
                "target", "u_159", "targetType", "MEMBER",
                "detail", "单笔出款 80000.00 超过阈值 50000.00", "action", "REVIEW",
                "handleStatus", 1, "hitTime", "2026-01-01 09:41:02"),
            row("hitId", 3, "ruleId", 5, "ruleName", "收款地址黑名单",
                "target", "TQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQQ", "targetType", "ADDRESS",
                "detail", "出款目标地址命中黑名单", "action", "BLOCK",
                "handleStatus", 2, "hitTime", "2026-01-01 11:03:19")
    ));

    private static final Map<String, Object> LIMITS = row(
            "singleMax", 50000.00,
            "dailyMax", 200000.00,
            "monthlyMax", 2000000.00,
            "minAmount", 10.00,
            "dailyCountLimit", 20,
            "reviewThreshold", 10000.00
    );

    // ══════════════════════════════════════════════════════
    // 规则
    // ══════════════════════════════════════════════════════
    @GetMapping("/rule/list")
    public Map<String, Object> ruleList(@RequestHeader(value = "Authorization", required = false) String auth,
                                        @RequestParam(defaultValue = "1") int pageNum,
                                        @RequestParam(defaultValue = "10") int pageSize,
                                        @RequestParam(required = false) String ruleName,
                                        @RequestParam(required = false) String ruleType,
                                        @RequestParam(required = false) String status) {
        if (unauth(auth)) return deny("/risk/rule/list");
        List<Map<String, Object>> rows = RULES.stream()
                .filter(r -> ruleName == null || ruleName.isBlank() || String.valueOf(r.get("ruleName")).contains(ruleName))
                .filter(r -> ruleType == null || ruleType.isBlank() || ruleType.equals(r.get("ruleType")))
                .filter(r -> status == null || status.isBlank() || status.equals(r.get("status")))
                .collect(Collectors.toList());
        return page(rows, pageNum, pageSize);
    }

    @PostMapping("/rule/add")
    public Map<String, Object> ruleAdd(@RequestHeader(value = "Authorization", required = false) String auth,
                                       @RequestBody Map<String, Object> body) {
        if (unauth(auth)) return deny("/risk/rule/add");
        Map<String, Object> r = new LinkedHashMap<>(body);
        r.put("ruleId", RULES.size() + 1);
        r.putIfAbsent("status", "0");
        r.putIfAbsent("hitCount", 0);
        RULES.add(r);
        return Map.of("code", 200, "msg", "操作成功");
    }

    @PutMapping("/rule/edit")
    public Map<String, Object> ruleEdit(@RequestHeader(value = "Authorization", required = false) String auth,
                                        @RequestBody Map<String, Object> body) {
        if (unauth(auth)) return deny("/risk/rule/edit");
        long id = ((Number) body.getOrDefault("ruleId", 0)).longValue();
        for (Map<String, Object> r : RULES) {
            if (((Number) r.get("ruleId")).longValue() == id) {
                r.putAll(body);
                return Map.of("code", 200, "msg", "操作成功");
            }
        }
        return Map.of("code", 500, "msg", "规则不存在");
    }

    @PutMapping("/rule/status")
    public Map<String, Object> ruleStatus(@RequestHeader(value = "Authorization", required = false) String auth,
                                          @RequestBody Map<String, Object> body) {
        if (unauth(auth)) return deny("/risk/rule/status");
        long id = ((Number) body.getOrDefault("ruleId", 0)).longValue();
        String status = String.valueOf(body.getOrDefault("status", "0"));
        for (Map<String, Object> r : RULES) {
            if (((Number) r.get("ruleId")).longValue() == id) {
                r.put("status", status);
                return Map.of("code", 200, "msg", "操作成功");
            }
        }
        return Map.of("code", 500, "msg", "规则不存在");
    }

    // ══════════════════════════════════════════════════════
    // 黑名单
    // ══════════════════════════════════════════════════════
    @GetMapping("/blacklist/list")
    public Map<String, Object> blacklistList(@RequestHeader(value = "Authorization", required = false) String auth,
                                             @RequestParam(defaultValue = "1") int pageNum,
                                             @RequestParam(defaultValue = "10") int pageSize,
                                             @RequestParam(required = false) String listType,
                                             @RequestParam(required = false) String value) {
        if (unauth(auth)) return deny("/risk/blacklist/list");
        List<Map<String, Object>> rows = BLACKLIST.stream()
                .filter(b -> listType == null || listType.isBlank() || listType.equals(b.get("listType")))
                .filter(b -> value == null || value.isBlank() || String.valueOf(b.get("value")).contains(value))
                .collect(Collectors.toList());
        return page(rows, pageNum, pageSize);
    }

    @PostMapping("/blacklist/add")
    public Map<String, Object> blacklistAdd(@RequestHeader(value = "Authorization", required = false) String auth,
                                            @RequestBody Map<String, Object> body) {
        if (unauth(auth)) return deny("/risk/blacklist/add");
        Object v = body.get("value");
        if (v == null || v.toString().isBlank()) {
            return Map.of("code", 500, "msg", "value 不能为空");
        }
        Map<String, Object> b = new LinkedHashMap<>(body);
        b.put("id", BLACKLIST.size() + 1);
        b.putIfAbsent("listType", "IP");
        b.putIfAbsent("source", "MANUAL");
        b.putIfAbsent("status", "0");
        b.put("createTime", now());
        BLACKLIST.add(b);
        return Map.of("code", 200, "msg", "操作成功", "data", b);
    }

    @DeleteMapping("/blacklist/{id}")
    public Map<String, Object> blacklistDel(@RequestHeader(value = "Authorization", required = false) String auth,
                                            @PathVariable long id) {
        if (unauth(auth)) return deny("/risk/blacklist/" + id);
        BLACKLIST.removeIf(b -> ((Number) b.get("id")).longValue() == id);
        return Map.of("code", 200, "msg", "操作成功");
    }

    // ══════════════════════════════════════════════════════
    // 命中记录
    // ══════════════════════════════════════════════════════
    @GetMapping("/hit/list")
    public Map<String, Object> hitList(@RequestHeader(value = "Authorization", required = false) String auth,
                                       @RequestParam(defaultValue = "1") int pageNum,
                                       @RequestParam(defaultValue = "10") int pageSize,
                                       @RequestParam(required = false) Long ruleId,
                                       @RequestParam(required = false) Integer handleStatus) {
        if (unauth(auth)) return deny("/risk/hit/list");
        List<Map<String, Object>> rows = HITS.stream()
                .filter(h -> ruleId == null || ruleId.equals(((Number) h.get("ruleId")).longValue()))
                .filter(h -> handleStatus == null || handleStatus.equals(((Number) h.get("handleStatus")).intValue()))
                .collect(Collectors.toList());
        return page(rows, pageNum, pageSize);
    }

    @PostMapping("/hit/handle")
    public Map<String, Object> hitHandle(@RequestHeader(value = "Authorization", required = false) String auth,
                                         @RequestBody Map<String, Object> body) {
        if (unauth(auth)) return deny("/risk/hit/handle");
        long id = ((Number) body.getOrDefault("hitId", 0)).longValue();
        int status = ((Number) body.getOrDefault("handleStatus", 1)).intValue();
        for (Map<String, Object> h : HITS) {
            if (((Number) h.get("hitId")).longValue() == id) {
                h.put("handleStatus", status);
                h.put("handleBy", "admin");
                h.put("handleTime", now());
                return Map.of("code", 200, "msg", "处理成功");
            }
        }
        return Map.of("code", 500, "msg", "记录不存在");
    }

    // ══════════════════════════════════════════════════════
    // 限额配置
    // ══════════════════════════════════════════════════════
    @GetMapping("/limit/config")
    public Map<String, Object> limitConfig(@RequestHeader(value = "Authorization", required = false) String auth) {
        if (unauth(auth)) return deny("/risk/limit/config");
        return Map.of("code", 200, "msg", "success", "data", LIMITS);
    }

    @PutMapping("/limit/config")
    public Map<String, Object> limitSave(@RequestHeader(value = "Authorization", required = false) String auth,
                                         @RequestBody Map<String, Object> body) {
        if (unauth(auth)) return deny("/risk/limit/config");
        LIMITS.putAll(body);
        return Map.of("code", 200, "msg", "保存成功", "data", LIMITS);
    }

    // ── helpers ───────────────────────────────────────────
    private boolean unauth(String auth) {
        return auth == null || !auth.startsWith("Bearer ") || jwtUtil.parseUsername(auth.substring(7).trim()) == null;
    }

    private Map<String, Object> deny(String path) {
        return Map.of("msg", "请求访问：" + path + "，认证失败，无法访问系统资源，请登录后重试!", "code", 401);
    }

    private static Map<String, Object> page(List<Map<String, Object>> rows, int pageNum, int pageSize) {
        int from = Math.max(0, (pageNum - 1) * pageSize);
        int to = Math.min(rows.size(), from + pageSize);
        List<Map<String, Object>> slice = from >= rows.size() ? List.of() : rows.subList(from, to);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", 200);
        m.put("msg", "success");
        m.put("total", rows.size());
        m.put("rows", slice);
        return m;
    }

    private static String now() {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    }

    private static Map<String, Object> row(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }
}
