package com.lab.console.controller;

import com.lab.console.security.JwtUtil;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 系统管理：用户 / 角色 / 菜单 / 操作日志 / 登录日志。
 *
 * 复刻 RuoYi 的标准接口形态：分页返回 {code, msg, rows, total}，
 * 树形结构用 {id, label, children}。
 */
@RestController
@RequestMapping("/prod-api/system")
public class SystemController {

    private final JwtUtil jwtUtil;

    public SystemController(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    // ══════════════════════════════════════════════════════
    // 内存数据（真实项目里走 MyBatis）
    // ══════════════════════════════════════════════════════
    private static final List<Map<String, Object>> USERS = new ArrayList<>(List.of(
            row("userId", 1, "userName", "admin", "nickName", "超级管理员", "deptName", "总部",
                "phonenumber", "138****8000", "email", "admin@example.com", "status", "0",
                "roleNames", "超级管理员", "createTime", "2025-01-01 00:00:00"),
            row("userId", 2, "userName", "ops", "nickName", "运营", "deptName", "运营部",
                "phonenumber", "139****9000", "email", "ops@example.com", "status", "0",
                "roleNames", "运营", "createTime", "2025-03-12 10:22:31"),
            row("userId", 3, "userName", "finance", "nickName", "财务", "deptName", "财务部",
                "phonenumber", "137****7000", "email", "fin@example.com", "status", "0",
                "roleNames", "财务", "createTime", "2025-03-12 10:25:04"),
            row("userId", 4, "userName", "agent01", "nickName", "代理一号", "deptName", "渠道部",
                "phonenumber", "136****6000", "email", "agent01@example.com", "status", "0",
                "roleNames", "代理", "createTime", "2025-06-08 09:12:00"),
            row("userId", 5, "userName", "risk", "nickName", "风控", "deptName", "风控部",
                "phonenumber", "135****5000", "email", "risk@example.com", "status", "0",
                "roleNames", "风控", "createTime", "2025-06-20 14:03:11")
    ));

    private static final List<Map<String, Object>> ROLES = new ArrayList<>(List.of(
            row("roleId", 1, "roleName", "超级管理员", "roleKey", "admin", "roleSort", 1, "status", "0", "userCount", 1),
            row("roleId", 2, "roleName", "运营", "roleKey", "ops", "roleSort", 2, "status", "0", "userCount", 1),
            row("roleId", 3, "roleName", "财务", "roleKey", "finance", "roleSort", 3, "status", "0", "userCount", 1),
            row("roleId", 4, "roleName", "代理", "roleKey", "agent", "roleSort", 4, "status", "0", "userCount", 1),
            row("roleId", 5, "roleName", "风控", "roleKey", "risk", "roleSort", 5, "status", "0", "userCount", 1)
    ));

    private static final List<Map<String, Object>> MENUS = new ArrayList<>(List.of(
            row("menuId", 1, "menuName", "首页", "parentId", 0, "path", "/dashboard", "menuType", "C", "orderNum", 1, "perms", ""),
            row("menuId", 2, "menuName", "商户管理", "parentId", 0, "path", "/merchant", "menuType", "M", "orderNum", 2, "perms", ""),
            row("menuId", 21, "menuName", "商户列表", "parentId", 2, "path", "list", "menuType", "C", "orderNum", 1, "perms", "merchant:list"),
            row("menuId", 22, "menuName", "通道配置", "parentId", 2, "path", "channel", "menuType", "C", "orderNum", 2, "perms", "merchant:channel"),
            row("menuId", 3, "menuName", "支付管理", "parentId", 0, "path", "/payment", "menuType", "M", "orderNum", 3, "perms", ""),
            row("menuId", 31, "menuName", "支付配置", "parentId", 3, "path", "config", "menuType", "C", "orderNum", 1, "perms", "payment:config"),
            row("menuId", 32, "menuName", "代付单", "parentId", 3, "path", "withdraw", "menuType", "C", "orderNum", 2, "perms", "payment:withdraw"),
            row("menuId", 33, "menuName", "充值单", "parentId", 3, "path", "deposit", "menuType", "C", "orderNum", 3, "perms", "payment:deposit"),
            row("menuId", 4, "menuName", "会员管理", "parentId", 0, "path", "/member", "menuType", "M", "orderNum", 4, "perms", ""),
            row("menuId", 41, "menuName", "会员列表", "parentId", 4, "path", "list", "menuType", "C", "orderNum", 1, "perms", "member:list"),
            row("menuId", 5, "menuName", "代理管理", "parentId", 0, "path", "/agent", "menuType", "M", "orderNum", 5, "perms", ""),
            row("menuId", 51, "menuName", "代理列表", "parentId", 5, "path", "list", "menuType", "C", "orderNum", 1, "perms", "agent:list"),
            row("menuId", 52, "menuName", "佣金结算", "parentId", 5, "path", "commission", "menuType", "C", "orderNum", 2, "perms", "agent:commission"),
            row("menuId", 6, "menuName", "风控管理", "parentId", 0, "path", "/risk", "menuType", "M", "orderNum", 6, "perms", ""),
            row("menuId", 61, "menuName", "风控规则", "parentId", 6, "path", "rule", "menuType", "C", "orderNum", 1, "perms", "risk:rule"),
            row("menuId", 62, "menuName", "黑名单", "parentId", 6, "path", "blacklist", "menuType", "C", "orderNum", 2, "perms", "risk:blacklist"),
            row("menuId", 7, "menuName", "系统管理", "parentId", 0, "path", "/system", "menuType", "M", "orderNum", 7, "perms", ""),
            row("menuId", 71, "menuName", "用户管理", "parentId", 7, "path", "user", "menuType", "C", "orderNum", 1, "perms", "system:user"),
            row("menuId", 72, "menuName", "角色管理", "parentId", 7, "path", "role", "menuType", "C", "orderNum", 2, "perms", "system:role"),
            row("menuId", 73, "menuName", "菜单管理", "parentId", 7, "path", "menu", "menuType", "C", "orderNum", 3, "perms", "system:menu"),
            row("menuId", 74, "menuName", "操作日志", "parentId", 7, "path", "operlog", "menuType", "C", "orderNum", 4, "perms", "system:operlog"),
            row("menuId", 75, "menuName", "登录日志", "parentId", 7, "path", "loginlog", "menuType", "C", "orderNum", 5, "perms", "system:loginlog")
    ));

    private static final List<Map<String, Object>> OPER_LOGS = new ArrayList<>(List.of(
            row("operId", 1, "title", "支付配置", "businessType", 1, "operName", "admin", "deptName", "总部",
                "operIp", "203.0.113.10", "operLocation", "内网", "status", 0, "operTime", "2026-01-01 09:12:33",
                "method", "com.lab.console.controller.BusinessController.paymentList()"),
            row("operId", 2, "title", "IP 白名单", "businessType", 2, "operName", "ops", "deptName", "运营部",
                "operIp", "203.0.113.21", "operLocation", "内网", "status", 0, "operTime", "2026-01-01 10:02:11",
                "method", "com.lab.console.controller.BusinessController.ipGroupAdd()"),
            row("operId", 3, "title", "会员详情", "businessType", 0, "operName", "ops", "deptName", "运营部",
                "operIp", "203.0.113.21", "operLocation", "内网", "status", 0, "operTime", "2026-01-01 10:05:44",
                "method", "com.lab.console.controller.BusinessController.memberDetail()"),
            row("operId", 4, "title", "代付单", "businessType", 1, "operName", "finance", "deptName", "财务部",
                "operIp", "203.0.113.35", "operLocation", "内网", "status", 0, "operTime", "2026-01-01 11:31:02",
                "method", "com.lab.console.controller.BusinessController.financeWithdraw()")
    ));

    private static final List<Map<String, Object>> LOGIN_LOGS = new ArrayList<>(List.of(
            row("infoId", 1, "userName", "admin", "ipaddr", "203.0.113.10", "loginLocation", "内网",
                "browser", "Chrome 120", "os", "Windows 10", "status", "0", "msg", "登录成功", "loginTime", "2026-01-01 08:55:12"),
            row("infoId", 2, "userName", "ops", "ipaddr", "203.0.113.21", "loginLocation", "内网",
                "browser", "Chrome 120", "os", "macOS 14", "status", "0", "msg", "登录成功", "loginTime", "2026-01-01 09:01:03"),
            row("infoId", 3, "userName", "unknown", "ipaddr", "198.51.100.77", "loginLocation", "未知",
                "browser", "curl/8.5", "os", "Linux", "status", "1", "msg", "用户不存在/密码错误", "loginTime", "2026-01-01 09:44:51")
    ));

    // ══════════════════════════════════════════════════════
    // 用户
    // ══════════════════════════════════════════════════════
    @GetMapping("/user/list")
    public Map<String, Object> userList(@RequestHeader(value = "Authorization", required = false) String auth,
                                        @RequestParam(defaultValue = "1") int pageNum,
                                        @RequestParam(defaultValue = "10") int pageSize,
                                        @RequestParam(required = false) String userName,
                                        @RequestParam(required = false) String status) {
        if (unauth(auth)) return deny("/system/user/list");
        List<Map<String, Object>> rows = USERS.stream()
                .filter(u -> userName == null || userName.isBlank() || String.valueOf(u.get("userName")).contains(userName))
                .filter(u -> status == null || status.isBlank() || status.equals(u.get("status")))
                .collect(Collectors.toList());
        return page(rows, pageNum, pageSize);
    }

    @GetMapping("/user/{userId}")
    public Map<String, Object> userGet(@RequestHeader(value = "Authorization", required = false) String auth,
                                       @PathVariable long userId) {
        if (unauth(auth)) return deny("/system/user/" + userId);
        return USERS.stream().filter(u -> Long.valueOf(userId).equals(((Number) u.get("userId")).longValue()))
                .findFirst().map(u -> Map.of("code", 200, "data", u, "roleIds", List.of(1)))
                .orElse(Map.of("code", 500, "msg", "用户不存在"));
    }

    @PostMapping("/user/add")
    public Map<String, Object> userAdd(@RequestHeader(value = "Authorization", required = false) String auth,
                                       @RequestBody Map<String, Object> body) {
        if (unauth(auth)) return deny("/system/user/add");
        Map<String, Object> u = new LinkedHashMap<>(body);
        u.put("userId", USERS.size() + 1);
        u.putIfAbsent("status", "0");
        u.put("createTime", now());
        USERS.add(u);
        return Map.of("code", 200, "msg", "操作成功");
    }

    @PutMapping("/user/edit")
    public Map<String, Object> userEdit(@RequestHeader(value = "Authorization", required = false) String auth,
                                        @RequestBody Map<String, Object> body) {
        if (unauth(auth)) return deny("/system/user/edit");
        long id = ((Number) body.getOrDefault("userId", 0)).longValue();
        for (Map<String, Object> u : USERS) {
            if (((Number) u.get("userId")).longValue() == id) {
                u.putAll(body);
                return Map.of("code", 200, "msg", "操作成功");
            }
        }
        return Map.of("code", 500, "msg", "用户不存在");
    }

    @DeleteMapping("/user/{userId}")
    public Map<String, Object> userDel(@RequestHeader(value = "Authorization", required = false) String auth,
                                       @PathVariable long userId) {
        if (unauth(auth)) return deny("/system/user/" + userId);
        USERS.removeIf(u -> ((Number) u.get("userId")).longValue() == userId);
        return Map.of("code", 200, "msg", "操作成功");
    }

    // ══════════════════════════════════════════════════════
    // 角色
    // ══════════════════════════════════════════════════════
    @GetMapping("/role/list")
    public Map<String, Object> roleList(@RequestHeader(value = "Authorization", required = false) String auth,
                                        @RequestParam(defaultValue = "1") int pageNum,
                                        @RequestParam(defaultValue = "10") int pageSize) {
        if (unauth(auth)) return deny("/system/role/list");
        return page(ROLES, pageNum, pageSize);
    }

    @GetMapping("/role/optionselect")
    public Map<String, Object> roleOptions(@RequestHeader(value = "Authorization", required = false) String auth) {
        if (unauth(auth)) return deny("/system/role/optionselect");
        return Map.of("code", 200, "data", ROLES);
    }

    @PostMapping("/role/add")
    public Map<String, Object> roleAdd(@RequestHeader(value = "Authorization", required = false) String auth,
                                       @RequestBody Map<String, Object> body) {
        if (unauth(auth)) return deny("/system/role/add");
        Map<String, Object> r = new LinkedHashMap<>(body);
        r.put("roleId", ROLES.size() + 1);
        r.putIfAbsent("status", "0");
        ROLES.add(r);
        return Map.of("code", 200, "msg", "操作成功");
    }

    // ══════════════════════════════════════════════════════
    // 菜单
    // ══════════════════════════════════════════════════════
    @GetMapping("/menu/list")
    public Map<String, Object> menuList(@RequestHeader(value = "Authorization", required = false) String auth,
                                        @RequestParam(required = false) String menuName) {
        if (unauth(auth)) return deny("/system/menu/list");
        List<Map<String, Object>> rows = MENUS.stream()
                .filter(m -> menuName == null || menuName.isBlank() || String.valueOf(m.get("menuName")).contains(menuName))
                .collect(Collectors.toList());
        return Map.of("code", 200, "msg", "success", "rows", rows, "total", rows.size());
    }

    @GetMapping("/menu/treeselect")
    public Map<String, Object> menuTree(@RequestHeader(value = "Authorization", required = false) String auth) {
        if (unauth(auth)) return deny("/system/menu/treeselect");
        List<Map<String, Object>> tree = new ArrayList<>();
        for (Map<String, Object> m : MENUS) {
            if (((Number) m.get("parentId")).intValue() == 0) {
                Map<String, Object> node = new LinkedHashMap<>();
                node.put("id", m.get("menuId"));
                node.put("label", m.get("menuName"));
                List<Map<String, Object>> children = new ArrayList<>();
                for (Map<String, Object> c : MENUS) {
                    if (((Number) c.get("parentId")).intValue() == ((Number) m.get("menuId")).intValue()) {
                        children.add(Map.of("id", c.get("menuId"), "label", c.get("menuName")));
                    }
                }
                if (!children.isEmpty()) node.put("children", children);
                tree.add(node);
            }
        }
        return Map.of("code", 200, "msg", "success", "data", tree);
    }

    // ══════════════════════════════════════════════════════
    // 日志
    // ══════════════════════════════════════════════════════
    @GetMapping("/operlog/list")
    public Map<String, Object> operLogList(@RequestHeader(value = "Authorization", required = false) String auth,
                                           @RequestParam(defaultValue = "1") int pageNum,
                                           @RequestParam(defaultValue = "10") int pageSize,
                                           @RequestParam(required = false) String title) {
        if (unauth(auth)) return deny("/system/operlog/list");
        List<Map<String, Object>> rows = OPER_LOGS.stream()
                .filter(o -> title == null || title.isBlank() || String.valueOf(o.get("title")).contains(title))
                .collect(Collectors.toList());
        return page(rows, pageNum, pageSize);
    }

    @GetMapping("/loginlog/list")
    public Map<String, Object> loginLogList(@RequestHeader(value = "Authorization", required = false) String auth,
                                            @RequestParam(defaultValue = "1") int pageNum,
                                            @RequestParam(defaultValue = "10") int pageSize,
                                            @RequestParam(required = false) String userName) {
        if (unauth(auth)) return deny("/system/loginlog/list");
        List<Map<String, Object>> rows = LOGIN_LOGS.stream()
                .filter(l -> userName == null || userName.isBlank() || String.valueOf(l.get("userName")).contains(userName))
                .collect(Collectors.toList());
        return page(rows, pageNum, pageSize);
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

    /** 构造一个有序的 Map（比 Map.of 更灵活，支持 null 值和动态 key）。 */
    private static Map<String, Object> row(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }
}
