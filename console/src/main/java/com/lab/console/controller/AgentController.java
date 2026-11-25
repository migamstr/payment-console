package com.lab.console.controller;

import com.lab.console.security.JwtUtil;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 代理体系：代理层级 / 佣金结算。数据全部走数据库。
 */
@RestController
@RequestMapping("/prod-api/agent")
public class AgentController {

    private final JwtUtil jwtUtil;
    private final JdbcTemplate jdbc;

    public AgentController(JwtUtil jwtUtil, JdbcTemplate jdbc) {
        this.jwtUtil = jwtUtil;
        this.jdbc = jdbc;
    }

    private static final String AGENT_COLS =
            "agent_id AS \"agentId\", agent_no AS \"agentNo\", agent_name AS \"agentName\", " +
            "parent_id AS \"parentId\", agent_level AS \"level\", real_name AS \"realName\", " +
            "phone, commission_rate AS \"commissionRate\", settle_type AS \"settleType\", status, " +
            "total_commission AS \"totalCommission\", settled_commission AS \"settledCommission\", " +
            "create_time AS \"createTime\"";

    private static final String COMMISSION_COLS =
            "settle_id AS \"settleId\", settle_no AS \"settleNo\", agent_id AS \"agentId\", " +
            "agent_name AS \"agentName\", period_start AS \"periodStart\", period_end AS \"periodEnd\", " +
            "order_count AS \"orderCount\", order_amount AS \"orderAmount\", rate, " +
            "commission AS \"commission\", status, settle_time AS \"settleTime\"";

    // ── 代理 ──────────────────────────────────────────────
    @GetMapping("/list")
    public Map<String, Object> agentList(@RequestHeader(value = "Authorization", required = false) String auth,
                                         @RequestParam(defaultValue = "1") int pageNum,
                                         @RequestParam(defaultValue = "10") int pageSize,
                                         @RequestParam(required = false) String agentName,
                                         @RequestParam(required = false) Integer level,
                                         @RequestParam(required = false) String status) {
        if (unauth(auth)) return deny("/agent/list");
        StringBuilder sql = new StringBuilder("SELECT " + AGENT_COLS + " FROM agent WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (agentName != null && !agentName.isBlank()) {
            sql.append(" AND agent_name LIKE ?");
            args.add("%" + agentName + "%");
        }
        if (level != null) {
            sql.append(" AND agent_level = ?");
            args.add(level);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            args.add(status);
        }
        sql.append(" ORDER BY agent_id");
        return page(jdbc.queryForList(sql.toString(), args.toArray()), pageNum, pageSize);
    }

    @GetMapping("/{agentId}")
    public Map<String, Object> agentGet(@RequestHeader(value = "Authorization", required = false) String auth,
                                        @PathVariable long agentId) {
        if (unauth(auth)) return deny("/agent/" + agentId);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT " + AGENT_COLS + " FROM agent WHERE agent_id = ?", agentId);
        if (rows.isEmpty()) return Map.of("code", 500, "msg", "代理不存在");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", 200);
        m.put("data", rows.get(0));
        m.put("subAgents", jdbc.queryForList(
                "SELECT " + AGENT_COLS + " FROM agent WHERE parent_id = ?", agentId));
        return m;
    }

    @GetMapping("/tree")
    public Map<String, Object> agentTree(@RequestHeader(value = "Authorization", required = false) String auth) {
        if (unauth(auth)) return deny("/agent/tree");
        List<Map<String, Object>> all = jdbc.queryForList(
                "SELECT agent_id AS \"agentId\", agent_name AS \"agentName\", parent_id AS \"parentId\", " +
                "agent_level AS \"level\", commission_rate AS \"commissionRate\" FROM agent ORDER BY agent_id");
        List<Map<String, Object>> tree = new ArrayList<>();
        for (Map<String, Object> a : all) {
            if (num(a.get("parentId")) == 0) tree.add(buildNode(a, all));
        }
        return Map.of("code", 200, "msg", "success", "data", tree);
    }

    private Map<String, Object> buildNode(Map<String, Object> a, List<Map<String, Object>> all) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", a.get("agentId"));
        node.put("label", a.get("agentName"));
        node.put("level", a.get("level"));
        node.put("commissionRate", a.get("commissionRate"));
        long id = num(a.get("agentId"));
        List<Map<String, Object>> children = new ArrayList<>();
        for (Map<String, Object> c : all) {
            if (num(c.get("parentId")) == id) children.add(buildNode(c, all));
        }
        if (!children.isEmpty()) node.put("children", children);
        return node;
    }

    @PostMapping("/add")
    public Map<String, Object> agentAdd(@RequestHeader(value = "Authorization", required = false) String auth,
                                        @RequestBody Map<String, Object> body) {
        if (unauth(auth)) return deny("/agent/add");
        try {
            jdbc.update("INSERT INTO agent(agent_no, agent_name, parent_id, agent_level, real_name, phone, " +
                        "commission_rate, settle_type, status, total_commission, settled_commission, create_time) " +
                        "VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                    body.getOrDefault("agentNo", ""),
                    body.getOrDefault("agentName", ""),
                    body.getOrDefault("parentId", 0),
                    body.getOrDefault("level", 1),
                    body.getOrDefault("realName", ""),
                    body.getOrDefault("phone", ""),
                    body.getOrDefault("commissionRate", 0.0),
                    body.getOrDefault("settleType", "T+1"),
                    body.getOrDefault("status", "0"),
                    0.0, 0.0, now());
            return Map.of("code", 200, "msg", "操作成功");
        } catch (Exception e) {
            return Map.of("code", 500, "msg", "新增失败: " + e.getMessage());
        }
    }

    @PutMapping("/edit")
    public Map<String, Object> agentEdit(@RequestHeader(value = "Authorization", required = false) String auth,
                                         @RequestBody Map<String, Object> body) {
        if (unauth(auth)) return deny("/agent/edit");
        Object id = body.get("agentId");
        if (id == null) return Map.of("code", 500, "msg", "agentId 不能为空");
        int n = jdbc.update("UPDATE agent SET agent_name=?, agent_level=?, real_name=?, phone=?, " +
                            "commission_rate=?, settle_type=?, status=? WHERE agent_id=?",
                body.getOrDefault("agentName", ""),
                body.getOrDefault("level", 1),
                body.getOrDefault("realName", ""),
                body.getOrDefault("phone", ""),
                body.getOrDefault("commissionRate", 0.0),
                body.getOrDefault("settleType", "T+1"),
                body.getOrDefault("status", "0"),
                ((Number) id).longValue());
        return n > 0 ? Map.of("code", 200, "msg", "操作成功") : Map.of("code", 500, "msg", "代理不存在");
    }

    // ── 佣金 ──────────────────────────────────────────────
    @GetMapping("/commission/list")
    public Map<String, Object> commissionList(@RequestHeader(value = "Authorization", required = false) String auth,
                                              @RequestParam(defaultValue = "1") int pageNum,
                                              @RequestParam(defaultValue = "10") int pageSize,
                                              @RequestParam(required = false) Long agentId,
                                              @RequestParam(required = false) Integer status) {
        if (unauth(auth)) return deny("/agent/commission/list");
        StringBuilder sql = new StringBuilder("SELECT " + COMMISSION_COLS + " FROM commission WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (agentId != null) { sql.append(" AND agent_id = ?"); args.add(agentId); }
        if (status != null)  { sql.append(" AND status = ?");   args.add(status); }
        sql.append(" ORDER BY settle_id");
        return page(jdbc.queryForList(sql.toString(), args.toArray()), pageNum, pageSize);
    }

    @PostMapping("/commission/settle")
    public Map<String, Object> settle(@RequestHeader(value = "Authorization", required = false) String auth,
                                      @RequestBody Map<String, Object> body) {
        if (unauth(auth)) return deny("/agent/commission/settle");
        Object settleId = body.get("settleId");
        if (settleId == null) return Map.of("code", 500, "msg", "settleId 不能为空");
        long id = ((Number) settleId).longValue();
        int n = jdbc.update("UPDATE commission SET status=1, settle_time=? WHERE settle_id=? AND status=0", now(), id);
        if (n == 0) return Map.of("code", 500, "msg", "结算单不存在或已结算");
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT " + COMMISSION_COLS + " FROM commission WHERE settle_id = ?", id);
        return Map.of("code", 200, "msg", "结算成功", "data", rows.get(0));
    }

    // ── helpers ───────────────────────────────────────────
    private boolean unauth(String auth) {
        return auth == null || !auth.startsWith("Bearer ") || jwtUtil.parseUsername(auth.substring(7).trim()) == null;
    }

    private Map<String, Object> deny(String path) {
        return Map.of("msg", "请求访问：" + path + "，认证失败，无法访问系统资源，请登录后重试!", "code", 401);
    }

    private static long num(Object o) {
        return o instanceof Number n ? n.longValue() : 0L;
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
