package com.lab.console.controller;

import com.lab.console.security.JwtUtil;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import javax.imageio.ImageIO;

import java.awt.image.BufferedImage;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 认证：账号口令登录 / 图形验证码 / 用户信息 / 菜单。
 */
@RestController
@RequestMapping("/prod-api")
public class AuthController {

    private final JwtUtil jwtUtil;
    private final JdbcTemplate jdbc;

    /** 验证码缓存：uuid -> code（5 分钟有效） */
    private static final Map<String, String> CAPTCHA = new ConcurrentHashMap<>();
    private static final Map<String, Long> CAPTCHA_TS = new ConcurrentHashMap<>();
    private static final long CAPTCHA_TTL = 5 * 60 * 1000L;

    public AuthController(JwtUtil jwtUtil, JdbcTemplate jdbc) {
        this.jwtUtil = jwtUtil;
        this.jdbc = jdbc;
    }

    /**
     * 图形验证码。返回 base64 PNG + uuid，uuid 提交登录时带上。
     *
     * 为了靶场可用，验证码是**固定的一组字符**（见下），真实系统是随机的。
     */
    @GetMapping("/captchaImage")
    public Map<String, Object> captchaImage() {
        String uuid = UUID.randomUUID().toString();
        String code = randomCode(4);
        CAPTCHA.put(uuid, code);
        CAPTCHA_TS.put(uuid, System.currentTimeMillis());
        purgeExpired();

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", 200);
        m.put("msg", "success");
        m.put("uuid", uuid);
        m.put("img", "data:image/png;base64," + renderPng(code));
        m.put("captchaEnabled", true);
        return m;
    }

    /**
     * 账号口令登录。
     *
     * 校验顺序：验证码 → 账号 → 口令（md5）。口令比对故意用 md5，与 sys_user.password 字段一致。
     */
    @PostMapping("/login")
    public Map<String, Object> login(@RequestParam(defaultValue = "") String username,
                                     @RequestParam(defaultValue = "") String password,
                                     @RequestParam(defaultValue = "") String code,
                                     @RequestParam(defaultValue = "") String uuid) {
        // ① 验证码
        if (uuid.isBlank() || code.isBlank()) {
            return Map.of("msg", "验证码错误", "code", 500);
        }
        String expected = CAPTCHA.get(uuid);
        Long ts = CAPTCHA_TS.get(uuid);
        if (expected == null || ts == null || System.currentTimeMillis() - ts > CAPTCHA_TTL) {
            return Map.of("msg", "验证码已过期", "code", 500);
        }
        if (!expected.equalsIgnoreCase(code.trim())) {
            return Map.of("msg", "验证码错误", "code", 500);
        }
        CAPTCHA.remove(uuid);
        CAPTCHA_TS.remove(uuid);

        // ② 账号
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT user_id AS \"userId\", user_name AS \"userName\", nick_name AS \"nickName\", " +
                "password, status FROM sys_user WHERE user_name = ?", username);
        if (rows.isEmpty()) {
            return Map.of("msg", "用户不存在/密码错误", "code", 500);
        }
        Map<String, Object> u = rows.get(0);
        if (!"0".equals(String.valueOf(u.get("status")))) {
            return Map.of("msg", "账号已停用", "code", 500);
        }

        // ③ 口令（md5）
        String inputMd5 = md5(password);
        if (!inputMd5.equalsIgnoreCase(String.valueOf(u.get("password")))) {
            return Map.of("msg", "用户不存在/密码错误", "code", 500);
        }

        String token = jwtUtil.create(username);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", 200);
        m.put("msg", "success");
        m.put("token", token);
        return m;
    }

    @GetMapping("/getInfo")
    public Map<String, Object> getInfo(@RequestHeader(value = "Authorization", required = false) String auth) {
        String user = resolve(auth);
        if (user == null) {
            return Map.of("msg", "请求访问：/getInfo，认证失败，无法访问系统资源，请登录后重试!", "code", 401);
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT user_id AS \"userId\", user_name AS \"userName\", nick_name AS \"nickName\", " +
                "dept_name AS \"deptName\", phonenumber, email FROM sys_user WHERE user_name = ?", user);
        Map<String, Object> userObj;
        if (rows.isEmpty()) {
            userObj = new LinkedHashMap<>();
            userObj.put("userId", 9999);
            userObj.put("userName", user);
            userObj.put("nickName", user);
            userObj.put("roles", List.of("admin"));
        } else {
            userObj = new LinkedHashMap<>(rows.get(0));
            userObj.put("roles", List.of("admin"));
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", 200);
        m.put("msg", "success");
        m.put("user", userObj);
        m.put("roles", List.of("admin"));
        m.put("permissions", List.of("*:*:*"));
        m.put("flag", "flag{lab_jwt_forge_admin}");
        return m;
    }

    @GetMapping("/getRouters")
    public Map<String, Object> getRouters(@RequestHeader(value = "Authorization", required = false) String auth) {
        String user = resolve(auth);
        if (user == null) {
            return Map.of("msg", "请求访问：/getRouters，认证失败，无法访问系统资源，请登录后重试!", "code", 401);
        }
        List<Map<String, Object>> menus = jdbc.queryForList(
                "SELECT menu_id AS \"menuId\", menu_name AS \"menuName\", parent_id AS \"parentId\", " +
                "path, menu_type AS \"menuType\", order_num AS \"orderNum\" FROM sys_menu " +
                "WHERE parent_id = 0 ORDER BY order_num");
        List<Map<String, Object>> data = new ArrayList<>();
        for (Map<String, Object> m : menus) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("path", m.get("path"));
            node.put("name", m.get("menuName"));
            node.put("meta", Map.of("title", m.get("menuName")));
            List<Map<String, Object>> children = jdbc.queryForList(
                    "SELECT menu_id AS \"menuId\", menu_name AS \"menuName\", path, order_num AS \"orderNum\" " +
                    "FROM sys_menu WHERE parent_id = ? ORDER BY order_num", m.get("menuId"));
            if (!children.isEmpty()) {
                List<Map<String, Object>> cl = new ArrayList<>();
                for (Map<String, Object> c : children) {
                    cl.add(Map.of("path", c.get("path"), "name", c.get("menuName"),
                                  "meta", Map.of("title", c.get("menuName"))));
                }
                node.put("children", cl);
            }
            data.add(node);
        }
        return Map.of("code", 200, "data", data);
    }

    // ── helpers ───────────────────────────────────────────
    private String resolve(String auth) {
        if (auth == null || !auth.startsWith("Bearer ")) return null;
        return jwtUtil.parseUsername(auth.substring(7).trim());
    }

    private static String md5(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] d = md.digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static String randomCode(int len) {
        String chars = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ";
        Random r = new Random();
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) sb.append(chars.charAt(r.nextInt(chars.length())));
        return sb.toString();
    }

    /** 用 Java2D 画一张 120x40 的验证码图，返回 base64（不带前缀）。 */
    private static String renderPng(String code) {
        try {
            int w = 120, h = 40;
            BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(245, 247, 250));
            g.fillRect(0, 0, w, h);

            Random r = new Random();
            // 干扰线
            for (int i = 0; i < 6; i++) {
                g.setColor(new Color(150 + r.nextInt(90), 150 + r.nextInt(90), 150 + r.nextInt(90)));
                g.drawLine(r.nextInt(w), r.nextInt(h), r.nextInt(w), r.nextInt(h));
            }
            // 字符
            g.setFont(new Font("SansSerif", Font.BOLD, 26));
            for (int i = 0; i < code.length(); i++) {
                g.setColor(new Color(20 + r.nextInt(120), 20 + r.nextInt(120), 20 + r.nextInt(120)));
                double theta = (r.nextDouble() - 0.5) * 0.5;
                g.rotate(theta, 20 + i * 26, h / 2.0);
                g.drawString(String.valueOf(code.charAt(i)), 18 + i * 26, 29);
                g.rotate(-theta, 20 + i * 26, h / 2.0);
            }
            g.dispose();

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(img, "png", bos);
            return Base64.getEncoder().encodeToString(bos.toByteArray());
        } catch (Exception e) {
            return "";
        }
    }

    private static void purgeExpired() {
        long now = System.currentTimeMillis();
        CAPTCHA_TS.entrySet().removeIf(e -> now - e.getValue() > CAPTCHA_TTL);
        CAPTCHA.keySet().removeIf(k -> !CAPTCHA_TS.containsKey(k));
    }
}
