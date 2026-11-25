package com.lab.console.controller;

import com.lab.console.security.JwtUtil;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 风控：规则 / 黑名单 / 命中记录 / 限额配置。数据全部走数据库。
 */
@RestController
@RequestMapping("/prod-api/risk")
public class RiskController {

    private final JwtUtil jwtUtil;
    private final JdbcTemplate jdbc;

    public RiskController(JwtUtil jwtUtil, JdbcTemplate jdbc) {
        this.jwtUtil = jwtUtil;
        this.jdbc = jdbc;
    }

    private static final String RULE_COLS =
            "rule_id AS \"ruleId\", rule_name AS \"ruleName\", rule_type AS \"ruleType\", " +
            "threshold, time_window AS \"window\", action_type AS \"action\", priority, status, " +
            "hit_count AS \"hitCount\", remark";

    private static final String BLACKLIST_COLS =
            "id, list_type AS \"listType\", list_value AS \"value\", reason, source, status, " +
            "create_by AS \"createBy\", create_time AS \"createTime\"";

    private static final String HIT_COLS =
            "hit_id AS \"hitId\", rule_id AS \"ruleId\", rule_name AS \"ruleName\", target, " +
            "target_type AS \"targetType\", detail, action_type AS \"action\", " +
            "handle_status AS \"handleStatus\", hit_time AS \"hitTime\", " +
            "handle_by AS \"handleBy\", handle_time AS \"handleTime\"";

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
        StringBuilder sql = new StringBuilder("SELECT " + RULE_COLS + " FROM risk_rule WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (ruleName != null && !ruleName.isBlank()) { sql.append(" AND rule_name LIKE ?"); args.add("%" + ruleName + "%"); }
        if (ruleType != null && !ruleType.isBlank()) { sql.append(" AND rule_type = ?"); args.add(ruleType); }
        if (status != null && !status.isBlank())     { sql.append(" AND status = ?");    args.add(status); }
        sql.append(" ORDER BY priority, rule_id");
        return page(jdbc.queryForList(sql.toString(), args.toArray()), pageNum, pageSize);
    }

    @PostMapping("/rule/add")
    public Map<String, Object> ruleAdd(@RequestHeader(value = "Authorization", required = false) String auth,
                                       @RequestBody Map<String, Object> body) {
        if (unauth(auth)) return deny("/risk/rule/add");
        try {
            jdbc.update("INSERT INTO risk_rule(rule_name, rule_type, threshold, time_window, action_type, " +
                        "priority, status, hit_count, remark) VALUES(?,?,?,?,?,?,?,0,?)",
                    body.getOrDefault("ruleName", ""),
                    body.getOrDefault("ruleType", "AMOUNT_SINGLE"),
                    body.getOrDefault("threshold", 0.0),
                    body.getOrDefault("window", "-"),
                    body.getOrDefault("action", "REVIEW"),
                    body.getOrDefault("priority", 10),
                    body.getOrDefault("status", "0"),
                    body.getOrDefault("remark", ""));
            return Map.of("code", 200, "msg", "操作成功");
        } catch (Exception e) {
            return Map.of("code", 500, "msg", "新增失败: " + e.getMessage());
        }
    }

    @PutMapping("/rule/edit")
    public Map<String, Object> ruleEdit(@RequestHeader(value = "Authorization", required = false) String auth,
                                        @RequestBody Map<String, Object> body) {
        if (unauth(auth)) return deny("/risk/rule/edit");
        Object id = body.get("ruleId");
        if (id == null) return Map.of("code", 500, "msg", "ruleId 不能为空");
        int n = jdbc.update("UPDATE risk_rule SET rule_name=?, rule_type=?, threshold=?, time_window=?, " +
                            "action_type=?, priority=?, status=?, remark=? WHERE rule_id=?",
                body.getOrDefault("ruleName", ""),
                body.getOrDefault("ruleType", "AMOUNT_SINGLE"),
                body.getOrDefault("threshold", 0.0),
                body.getOrDefault("window", "-"),
                body.getOrDefault("action", "REVIEW"),
                body.getOrDefault("priority", 10),
                body.getOrDefault("status", "0"),
                body.getOrDefault("remark", ""),
                ((Number) id).longValue());
        return n > 0 ? Map.of("code", 200, "msg", "操作成功") : Map.of("code", 500, "msg", "规则不存在");
    }

    @PutMapping("/rule/status")
    public Map<String, Object> ruleStatus(@RequestHeader(value = "Authorization", required = false) String auth,
                                          @RequestBody Map<String, Object> body) {
        if (unauth(auth)) return deny("/risk/rule/status");
        Object id = body.get("ruleId");
        if (id == null) return Map.of("code", 500, "msg", "ruleId 不能为空");
        int n = jdbc.update("UPDATE risk_rule SET status=? WHERE rule_id=?",
                String.valueOf(body.getOrDefault("status", "0")), ((Number) id).longValue());
        return n > 0 ? Map.of("code", 200, "msg", "操作成功") : Map.of("code", 500, "msg", "规则不存在");
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
        StringBuilder sql = new StringBuilder("SELECT " + BLACKLIST_COLS + " FROM risk_blacklist WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (listType != null && !listType.isBlank()) { sql.append(" AND list_type = ?"); args.add(listType); }
        if (value != null && !value.isBlank())       { sql.append(" AND list_value LIKE ?"); args.add("%" + value + "%"); }
        sql.append(" ORDER BY id");
        return page(jdbc.queryForList(sql.toString(), args.toArray()), pageNum, pageSize);
    }

    @PostMapping("/blacklist/add")
    public Map<String, Object> blacklistAdd(@RequestHeader(value = "Authorization", required = false) String auth,
                                            @RequestBody Map<String, Object> body) {
        if (unauth(auth)) return deny("/risk/blacklist/add");
        Object v = body.get("value");
        if (v == null || v.toString().isBlank()) return Map.of("code", 500, "msg", "value 不能为空");
        try {
            jdbc.update("INSERT INTO risk_blacklist(list_type, list_value, reason, source, status, create_by, create_time) " +
                        "VALUES(?,?,?,?,?,?,?)",
                    body.getOrDefault("listType", "IP"),
                    v.toString(),
                    body.getOrDefault("reason", ""),
                    body.getOrDefault("source", "MANUAL"),
                    body.getOrDefault("status", "0"),
                    "admin", now());
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT " + BLACKLIST_COLS + " FROM risk_blacklist ORDER BY id DESC LIMIT 1");
            return Map.of("code", 200, "msg", "操作成功", "data", rows.isEmpty() ? Map.of() : rows.get(0));
        } catch (Exception e) {
            return Map.of("code", 500, "msg", "新增失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/blacklist/{id}")
    public Map<String, Object> blacklistDel(@RequestHeader(value = "Authorization", required = false) String auth,
                                            @PathVariable long id) {
        if (unauth(auth)) return deny("/risk/blacklist/" + id);
        jdbc.update("DELETE FROM risk_blacklist WHERE id = ?", id);
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
        StringBuilder sql = new StringBuilder("SELECT " + HIT_COLS + " FROM risk_hit WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (ruleId != null)       { sql.append(" AND rule_id = ?");      args.add(ruleId); }
        if (handleStatus != null) { sql.append(" AND handle_status = ?"); args.add(handleStatus); }
        sql.append(" ORDER BY hit_id");
        return page(jdbc.queryForList(sql.toString(), args.toArray()), pageNum, pageSize);
    }

    @PostMapping("/hit/handle")
    public Map<String, Object> hitHandle(@RequestHeader(value = "Authorization", required = false) String auth,
                                         @RequestBody Map<String, Object> body) {
        if (unauth(auth)) return deny("/risk/hit/handle");
        Object id = body.get("hitId");
        if (id == null) return Map.of("code", 500, "msg", "hitId 不能为空");
        int status = body.get("handleStatus") instanceof Number n ? n.intValue() : 1;
        int rows = jdbc.update("UPDATE risk_hit SET handle_status=?, handle_by=?, handle_time=? WHERE hit_id=?",
                status, "admin", now(), ((Number) id).longValue());
        return rows > 0 ? Map.of("code", 200, "msg", "处理成功") : Map.of("code", 500, "msg", "记录不存在");
    }

    // ══════════════════════════════════════════════════════
    // 限额配置（走 sys_config）
    // ══════════════════════════════════════════════════════
    @GetMapping("/limit/config")
    public Map<String, Object> limitConfig(@RequestHeader(value = "Authorization", required = false) String auth) {
        if (unauth(auth)) return deny("/risk/limit/config");
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT config_value AS \"configValue\" FROM sys_config WHERE config_key = 'risk_limit_config'");
        Map<String, Object> data = new LinkedHashMap<>();
        if (!rows.isEmpty() && rows.get(0).get("configValue") != null) {
            data = parseFlatJson(String.valueOf(rows.get(0).get("configValue")));
        }
        if (data.isEmpty()) data = defaultLimits();
        return Map.of("code", 200, "msg", "success", "data", data);
    }

    @PutMapping("/limit/config")
    public Map<String, Object> limitSave(@RequestHeader(value = "Authorization", required = false) String auth,
                                         @RequestBody Map<String, Object> body) {
        if (unauth(auth)) return deny("/risk/limit/config");
        String json = toFlatJson(body);
        int n = jdbc.update("UPDATE sys_config SET config_value=? WHERE config_key='risk_limit_config'", json);
        if (n == 0) {
            jdbc.update("INSERT INTO sys_config(config_key, config_name, config_value) VALUES(?,?,?)",
                    "risk_limit_config", "风控限额配置", json);
        }
        return Map.of("code", 200, "msg", "保存成功", "data", body);
    }

    private static Map<String, Object> defaultLimits() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("singleMax", 50000.00);
        m.put("dailyMax", 200000.00);
        m.put("monthlyMax", 2000000.00);
        m.put("minAmount", 10.00);
        m.put("dailyCountLimit", 20);
        m.put("reviewThreshold", 10000.00);
        return m;
    }

    private static String toFlatJson(Map<String, Object> m) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : m.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append('"').append(e.getKey()).append("\":");
            Object v = e.getValue();
            if (v instanceof Number) sb.append(v);
            else sb.append('"').append(v).append('"');
        }
        return sb.append('}').toString();
    }

    private static Map<String, Object> parseFlatJson(String s) {
        Map<String, Object> m = new LinkedHashMap<>();
        String body = s.trim();
        if (body.startsWith("{")) body = body.substring(1);
        if (body.endsWith("}")) body = body.substring(0, body.length() - 1);
        for (String pair : body.split(",")) {
            String[] kv = pair.split(":", 2);
            if (kv.length != 2) continue;
            String k = kv[0].trim().replace("\"", "");
            String v = kv[1].trim().replace("\"", "");
            try {
                m.put(k, v.contains(".") ? Double.parseDouble(v) : Long.parseLong(v));
            } catch (Exception e) {
                m.put(k, v);
            }
        }
        return m;
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
}
