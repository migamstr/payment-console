package com.lab.console.controller;

import com.lab.console.security.JwtUtil;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 代理体系：代理层级 / 佣金规则 / 结算记录。
 *
 * 复刻支付服务商后台的标准代理模型：
 *   · 多级代理（parentAgentId 递归）
 *   · 分润比例（按通道差异化）
 *   · 按日/月结算，结算单可查
 */
@RestController
@RequestMapping("/prod-api/agent")
public class AgentController {

    private final JwtUtil jwtUtil;

    public AgentController(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    private static final List<Map<String, Object>> AGENTS = new ArrayList<>(List.of(
            row("agentId", 1, "agentNo", "AG20250001", "agentName", "总代-A", "parentId", 0,
                "level", 1, "realName", "陈**", "phone", "138****1111",
                "commissionRate", 0.35, "settleType", "T+1", "status", "0",
                "totalCommission", 128450.32, "settledCommission", 98620.10,
                "createTime", "2025-01-05 09:00:00"),
            row("agentId", 2, "agentNo", "AG20250002", "agentName", "一级-B", "parentId", 1,
                "level", 2, "realName", "李**", "phone", "139****2222",
                "commissionRate", 0.18, "settleType", "T+1", "status", "0",
                "totalCommission", 42180.55, "settledCommission", 31020.00,
                "createTime", "2025-02-11 14:20:00"),
            row("agentId", 3, "agentNo", "AG20250003", "agentName", "一级-C", "parentId", 1,
                "level", 2, "realName", "王**", "phone", "137****3333",
                "commissionRate", 0.15, "settleType", "T+1", "status", "0",
                "totalCommission", 20330.18, "settledCommission", 20330.18,
                "createTime", "2025-02-11 14:22:00"),
            row("agentId", 4, "agentNo", "AG20250004", "agentName", "二级-D", "parentId", 2,
                "level", 3, "realName", "赵**", "phone", "136****4444",
                "commissionRate", 0.08, "settleType", "T+7", "status", "0",
                "totalCommission", 5120.44, "settledCommission", 4200.00,
                "createTime", "2025-04-01 10:05:00"),
            row("agentId", 5, "agentNo", "AG20250005", "agentName", "二级-E", "parentId", 2,
                "level", 3, "realName", "孙**", "phone", "135****5555",
                "commissionRate", 0.08, "settleType", "T+7", "status", "1",
                "totalCommission", 0.00, "settledCommission", 0.00,
                "createTime", "2025-08-19 16:41:00")
    ));

    private static final List<Map<String, Object>> COMMISSIONS = new ArrayList<>(List.of(
            row("settleId", 1, "settleNo", "ST20260101001", "agentId", 1, "agentName", "总代-A",
                "periodStart", "2025-12-31", "periodEnd", "2025-12-31",
                "orderCount", 1284, "orderAmount", 486230.00, "rate", 0.35,
                "commission", 1701.80, "status", 1, "settleTime", "2026-01-01 02:00:12"),
            row("settleId", 2, "settleNo", "ST20260101002", "agentId", 2, "agentName", "一级-B",
                "periodStart", "2025-12-31", "periodEnd", "2025-12-31",
                "orderCount", 402, "orderAmount", 152180.00, "rate", 0.18,
                "commission", 273.92, "status", 1, "settleTime", "2026-01-01 02:00:14"),
            row("settleId", 3, "settleNo", "ST20260101003", "agentId", 3, "agentName", "一级-C",
                "periodStart", "2025-12-31", "periodEnd", "2025-12-31",
                "orderCount", 88, "orderAmount", 32400.00, "rate", 0.15,
                "commission", 48.60, "status", 0, "settleTime", null),
            row("settleId", 4, "settleNo", "ST20260101004", "agentId", 4, "agentName", "二级-D",
                "periodStart", "2025-12-25", "periodEnd", "2025-12-31",
                "orderCount", 16, "orderAmount", 4820.00, "rate", 0.08,
                "commission", 3.86, "status", 0, "settleTime", null)
    ));

    // ── 代理 ──────────────────────────────────────────────
    @GetMapping("/list")
    public Map<String, Object> agentList(@RequestHeader(value = "Authorization", required = false) String auth,
                                         @RequestParam(defaultValue = "1") int pageNum,
                                         @RequestParam(defaultValue = "10") int pageSize,
                                         @RequestParam(required = false) String agentName,
                                         @RequestParam(required = false) Integer level,
                                         @RequestParam(required = false) String status) {
        if (unauth(auth)) return deny("/agent/list");
        List<Map<String, Object>> rows = AGENTS.stream()
                .filter(a -> agentName == null || agentName.isBlank() || String.valueOf(a.get("agentName")).contains(agentName))
                .filter(a -> level == null || level.equals(((Number) a.get("level")).intValue()))
                .filter(a -> status == null || status.isBlank() || status.equals(a.get("status")))
                .collect(Collectors.toList());
        return page(rows, pageNum, pageSize);
    }

    @GetMapping("/{agentId}")
    public Map<String, Object> agentGet(@RequestHeader(value = "Authorization", required = false) String auth,
                                        @PathVariable long agentId) {
        if (unauth(auth)) return deny("/agent/" + agentId);
        for (Map<String, Object> a : AGENTS) {
            if (((Number) a.get("agentId")).longValue() == agentId) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("code", 200);
                m.put("data", a);
                m.put("subAgents", AGENTS.stream()
                        .filter(x -> ((Number) x.get("parentId")).longValue() == agentId)
                        .collect(Collectors.toList()));
                return m;
            }
        }
        return Map.of("code", 500, "msg", "代理不存在");
    }

    /** 代理层级树。 */
    @GetMapping("/tree")
    public Map<String, Object> agentTree(@RequestHeader(value = "Authorization", required = false) String auth) {
        if (unauth(auth)) return deny("/agent/tree");
        List<Map<String, Object>> tree = new ArrayList<>();
        for (Map<String, Object> a : AGENTS) {
            if (((Number) a.get("parentId")).intValue() == 0) {
                tree.add(buildNode(a));
            }
        }
        return Map.of("code", 200, "msg", "success", "data", tree);
    }

    private Map<String, Object> buildNode(Map<String, Object> a) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", a.get("agentId"));
        node.put("label", a.get("agentName"));
        node.put("level", a.get("level"));
        node.put("commissionRate", a.get("commissionRate"));
        List<Map<String, Object>> children = new ArrayList<>();
        long id = ((Number) a.get("agentId")).longValue();
        for (Map<String, Object> c : AGENTS) {
            if (((Number) c.get("parentId")).longValue() == id) {
                children.add(buildNode(c));
            }
        }
        if (!children.isEmpty()) node.put("children", children);
        return node;
    }

    @PostMapping("/add")
    public Map<String, Object> agentAdd(@RequestHeader(value = "Authorization", required = false) String auth,
                                        @RequestBody Map<String, Object> body) {
        if (unauth(auth)) return deny("/agent/add");
        Map<String, Object> a = new LinkedHashMap<>(body);
        a.put("agentId", AGENTS.size() + 1);
        a.putIfAbsent("status", "0");
        a.putIfAbsent("totalCommission", 0.0);
        a.putIfAbsent("settledCommission", 0.0);
        a.put("createTime", now());
        AGENTS.add(a);
        return Map.of("code", 200, "msg", "操作成功");
    }

    @PutMapping("/edit")
    public Map<String, Object> agentEdit(@RequestHeader(value = "Authorization", required = false) String auth,
                                         @RequestBody Map<String, Object> body) {
        if (unauth(auth)) return deny("/agent/edit");
        long id = ((Number) body.getOrDefault("agentId", 0)).longValue();
        for (Map<String, Object> a : AGENTS) {
            if (((Number) a.get("agentId")).longValue() == id) {
                a.putAll(body);
                return Map.of("code", 200, "msg", "操作成功");
            }
        }
        return Map.of("code", 500, "msg", "代理不存在");
    }

    // ── 佣金结算 ──────────────────────────────────────────
    @GetMapping("/commission/list")
    public Map<String, Object> commissionList(@RequestHeader(value = "Authorization", required = false) String auth,
                                              @RequestParam(defaultValue = "1") int pageNum,
                                              @RequestParam(defaultValue = "10") int pageSize,
                                              @RequestParam(required = false) Long agentId,
                                              @RequestParam(required = false) Integer status) {
        if (unauth(auth)) return deny("/agent/commission/list");
        List<Map<String, Object>> rows = COMMISSIONS.stream()
                .filter(c -> agentId == null || agentId.equals(((Number) c.get("agentId")).longValue()))
                .filter(c -> status == null || status.equals(((Number) c.get("status")).intValue()))
                .collect(Collectors.toList());
        return page(rows, pageNum, pageSize);
    }

    /** 触发一次结算（真实项目里是定时任务，这里做成手动触发方便演示）。 */
    @PostMapping("/commission/settle")
    public Map<String, Object> settle(@RequestHeader(value = "Authorization", required = false) String auth,
                                      @RequestBody Map<String, Object> body) {
        if (unauth(auth)) return deny("/agent/commission/settle");
        Object settleId = body.get("settleId");
        if (settleId == null) {
            return Map.of("code", 500, "msg", "settleId 不能为空");
        }
        long id = ((Number) settleId).longValue();
        for (Map<String, Object> c : COMMISSIONS) {
            if (((Number) c.get("settleId")).longValue() == id) {
                c.put("status", 1);
                c.put("settleTime", now());
                return Map.of("code", 200, "msg", "结算成功", "data", c);
            }
        }
        return Map.of("code", 500, "msg", "结算单不存在");
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
