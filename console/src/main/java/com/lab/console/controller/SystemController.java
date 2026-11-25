package com.lab.console.controller;

import com.lab.console.security.JwtUtil;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 系统管理：用户 / 角色 / 菜单 / 操作日志 / 登录日志。
 *
 * 数据全部走数据库（JdbcTemplate）；分页在应用层做，保持返回结构统一。
 */
@RestController
@RequestMapping("/prod-api/system")
public class SystemController {

    private final JwtUtil jwtUtil;
    private final JdbcTemplate jdbc;

    public SystemController(JwtUtil jwtUtil, JdbcTemplate jdbc) {
        this.jwtUtil = jwtUtil;
        this.jdbc = jdbc;
    }

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
        StringBuilder sql = new StringBuilder(
                "SELECT user_id AS \"userId\", user_name AS \"userName\", nick_name AS \"nickName\", " +
                "dept_name AS \"deptName\", phonenumber, email, status, " +
                "create_time AS \"createTime\" FROM sys_user WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (userName != null && !userName.isBlank()) {
            sql.append(" AND user_name LIKE ?");
            args.add("%" + userName + "%");
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND status = ?");
            args.add(status);
        }
        sql.append(" ORDER BY user_id");
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        return page(rows, pageNum, pageSize);
    }

    @GetMapping("/user/{userId}")
    public Map<String, Object> userGet(@RequestHeader(value = "Authorization", required = false) String auth,
                                       @PathVariable long userId) {
        if (unauth(auth)) return deny("/system/user/" + userId);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT user_id AS \"userId\", user_name AS \"userName\", nick_name AS \"nickName\", " +
                "dept_name AS \"deptName\", phonenumber, email, status, " +
                "create_time AS \"createTime\" FROM sys_user WHERE user_id = ?", userId);
        if (rows.isEmpty()) return Map.of("code", 500, "msg", "用户不存在");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", 200);
        m.put("data", rows.get(0));
        m.put("roleIds", List.of(1));
        return m;
    }

    @PostMapping("/user/add")
    public Map<String, Object> userAdd(@RequestHeader(value = "Authorization", required = false) String auth,
                                       @RequestBody Map<String, Object> body) {
        if (unauth(auth)) return deny("/system/user/add");
        try {
            jdbc.update("INSERT INTO sys_user(user_name, nick_name, dept_name, phonenumber, email, status, create_time) " +
                        "VALUES(?,?,?,?,?,?,?)",
                    body.getOrDefault("userName", ""),
                    body.getOrDefault("nickName", ""),
                    body.getOrDefault("deptName", ""),
                    body.getOrDefault("phonenumber", ""),
                    body.getOrDefault("email", ""),
                    body.getOrDefault("status", "0"),
                    now());
            return Map.of("code", 200, "msg", "操作成功");
        } catch (Exception e) {
            return Map.of("code", 500, "msg", "新增失败: " + e.getMessage());
        }
    }

    @PutMapping("/user/edit")
    public Map<String, Object> userEdit(@RequestHeader(value = "Authorization", required = false) String auth,
                                        @RequestBody Map<String, Object> body) {
        if (unauth(auth)) return deny("/system/user/edit");
        Object id = body.get("userId");
        if (id == null) return Map.of("code", 500, "msg", "userId 不能为空");
        int n = jdbc.update("UPDATE sys_user SET user_name=?, nick_name=?, dept_name=?, phonenumber=?, email=?, status=? WHERE user_id=?",
                body.getOrDefault("userName", ""),
                body.getOrDefault("nickName", ""),
                body.getOrDefault("deptName", ""),
                body.getOrDefault("phonenumber", ""),
                body.getOrDefault("email", ""),
                body.getOrDefault("status", "0"),
                ((Number) id).longValue());
        return n > 0 ? Map.of("code", 200, "msg", "操作成功") : Map.of("code", 500, "msg", "用户不存在");
    }

    @DeleteMapping("/user/{userId}")
    public Map<String, Object> userDel(@RequestHeader(value = "Authorization", required = false) String auth,
                                       @PathVariable long userId) {
        if (unauth(auth)) return deny("/system/user/" + userId);
        jdbc.update("DELETE FROM sys_user WHERE user_id = ?", userId);
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
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT role_id AS \"roleId\", role_name AS \"roleName\", role_key AS \"roleKey\", " +
                "role_sort AS \"roleSort\", status, user_count AS \"userCount\" FROM sys_role ORDER BY role_sort");
        return page(rows, pageNum, pageSize);
    }

    @GetMapping("/role/optionselect")
    public Map<String, Object> roleOptions(@RequestHeader(value = "Authorization", required = false) String auth) {
        if (unauth(auth)) return deny("/system/role/optionselect");
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT role_id AS \"roleId\", role_name AS \"roleName\", role_key AS \"roleKey\" FROM sys_role ORDER BY role_sort");
        return Map.of("code", 200, "data", rows);
    }

    @PostMapping("/role/add")
    public Map<String, Object> roleAdd(@RequestHeader(value = "Authorization", required = false) String auth,
                                       @RequestBody Map<String, Object> body) {
        if (unauth(auth)) return deny("/system/role/add");
        try {
            jdbc.update("INSERT INTO sys_role(role_name, role_key, role_sort, status, user_count) VALUES(?,?,?,?,0)",
                    body.getOrDefault("roleName", ""),
                    body.getOrDefault("roleKey", ""),
                    body.getOrDefault("roleSort", 0),
                    body.getOrDefault("status", "0"));
            return Map.of("code", 200, "msg", "操作成功");
        } catch (Exception e) {
            return Map.of("code", 500, "msg", "新增失败: " + e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════
    // 菜单
    // ══════════════════════════════════════════════════════
    @GetMapping("/menu/list")
    public Map<String, Object> menuList(@RequestHeader(value = "Authorization", required = false) String auth,
                                        @RequestParam(required = false) String menuName) {
        if (unauth(auth)) return deny("/system/menu/list");
        StringBuilder sql = new StringBuilder(
                "SELECT menu_id AS \"menuId\", menu_name AS \"menuName\", parent_id AS \"parentId\", " +
                "path, menu_type AS \"menuType\", order_num AS \"orderNum\", perms FROM sys_menu WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (menuName != null && !menuName.isBlank()) {
            sql.append(" AND menu_name LIKE ?");
            args.add("%" + menuName + "%");
        }
        sql.append(" ORDER BY parent_id, order_num");
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        return Map.of("code", 200, "msg", "success", "rows", rows, "total", rows.size());
    }

    @GetMapping("/menu/treeselect")
    public Map<String, Object> menuTree(@RequestHeader(value = "Authorization", required = false) String auth) {
        if (unauth(auth)) return deny("/system/menu/treeselect");
        List<Map<String, Object>> all = jdbc.queryForList(
                "SELECT menu_id AS \"menuId\", menu_name AS \"menuName\", parent_id AS \"parentId\" FROM sys_menu ORDER BY order_num");

        List<Map<String, Object>> tree = new ArrayList<>();
        for (Map<String, Object> m : all) {
            if (num(m.get("parentId")) == 0) {
                Map<String, Object> node = new LinkedHashMap<>();
                node.put("id", m.get("menuId"));
                node.put("label", m.get("menuName"));
                List<Map<String, Object>> children = new ArrayList<>();
                long pid = num(m.get("menuId"));
                for (Map<String, Object> c : all) {
                    if (num(c.get("parentId")) == pid) {
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
        StringBuilder sql = new StringBuilder(
                "SELECT oper_id AS \"operId\", title, business_type AS \"businessType\", " +
                "oper_name AS \"operName\", dept_name AS \"deptName\", oper_ip AS \"operIp\", " +
                "oper_location AS \"operLocation\", status, oper_time AS \"operTime\", method " +
                "FROM oper_log WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (title != null && !title.isBlank()) {
            sql.append(" AND title LIKE ?");
            args.add("%" + title + "%");
        }
        sql.append(" ORDER BY oper_id");
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        return page(rows, pageNum, pageSize);
    }

    @GetMapping("/loginlog/list")
    public Map<String, Object> loginLogList(@RequestHeader(value = "Authorization", required = false) String auth,
                                            @RequestParam(defaultValue = "1") int pageNum,
                                            @RequestParam(defaultValue = "10") int pageSize,
                                            @RequestParam(required = false) String userName) {
        if (unauth(auth)) return deny("/system/loginlog/list");
        StringBuilder sql = new StringBuilder(
                "SELECT info_id AS \"infoId\", user_name AS \"userName\", ipaddr, " +
                "login_location AS \"loginLocation\", browser, os, status, msg, " +
                "login_time AS \"loginTime\" FROM login_log WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (userName != null && !userName.isBlank()) {
            sql.append(" AND user_name LIKE ?");
            args.add("%" + userName + "%");
        }
        sql.append(" ORDER BY info_id");
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        return page(rows, pageNum, pageSize);
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
