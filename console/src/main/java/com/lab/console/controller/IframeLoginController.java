package com.lab.console.controller;

import com.lab.console.config.IframeIpWhitelist;
import com.lab.console.security.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * /prod-api/iframeLogin —— 真实系统中的主登录入口。
 *
 * 三层门（严格按真实顺序）：
 *   ① 三头签名  fingerprint / timestamp / dynamicHeader
 *                dynamicHeader = base64url(HMAC-SHA256(key=fingerprint, msg=timestamp))
 *                ⚠️ HMAC 的 key 就是客户端自己生成的 fingerprint —— 服务端没有任何私密材料
 *   ② 账号口令
 *   ③ 真实 socket IP 白名单
 *
 * ⚠️ 注意顺序：真实系统里 IP 判定在**账号校验之前** ——
 *    所以你连"用户存不存在"都探不到，这是最关键的复刻点。
 */
@RestController
@RequestMapping("/prod-api")
public class IframeLoginController {

    private final IframeIpWhitelist ipWhitelist;
    private final JwtUtil jwtUtil;

    @Value("${lab.iframe.timestamp-window-seconds:300}")
    private long tsWindowSeconds;

    public IframeLoginController(IframeIpWhitelist ipWhitelist, JwtUtil jwtUtil) {
        this.ipWhitelist = ipWhitelist;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping(value = "/iframeLogin", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> iframeLogin(
            HttpServletRequest request,
            @RequestParam(defaultValue = "") String username,
            @RequestParam(defaultValue = "") String password,
            @RequestParam(defaultValue = "false") String rememberMe,
            @RequestHeader(value = "fingerprint", required = false) String fingerprint,
            @RequestHeader(value = "timestamp", required = false) String timestamp,
            @RequestHeader(value = "dynamicHeader", required = false) String dynamicHeader) {

        // ── 门 ①：三头签名 ────────────────────────────────
        if (isBlank(fingerprint) || isBlank(timestamp) || isBlank(dynamicHeader)) {
            return err("非法访问");
        }
        long ts;
        try {
            ts = Long.parseLong(timestamp);
        } catch (NumberFormatException e) {
            return err("非法访问");
        }
        if (Math.abs(System.currentTimeMillis() - ts) > tsWindowSeconds * 1000) {
            return err("非法访问（时间戳超窗）");
        }
        String expected = hmacB64Url(fingerprint, timestamp);
        if (!constantTimeEquals(expected, stripPad(dynamicHeader))) {
            return err("非法访问");
        }

        // ── 门 ③：真实 socket IP（在账号查询之前！）──────────
        if (!ipWhitelist.isAllowed(request)) {
            return errIp();
        }

        // ── 门 ②：账号口令 ────────────────────────────────
        // 本项目里只认 admin/admin（真实系统是查库 + bcrypt）
        if (!"admin".equals(username) || !"admin".equals(password)) {
            return err("用户名或密码错误");
        }

        String token = jwtUtil.create(username);
        Map<String, Object> ok = new HashMap<>();
        ok.put("code", 200);
        ok.put("msg", "success");
        ok.put("token", token);
        Map<String, Object> data = new HashMap<>();
        data.put("token", token);
        ok.put("data", data);
        ok.put("flag", "flag{lab_iframe_login_bypass}");
        return ok;
    }

    /** 调试接口：告诉你服务端看到的源 IP 是什么（真实系统没有）。 */
    @GetMapping("/iframeLogin/ipcheck")
    public Map<String, Object> ipcheck(HttpServletRequest request) {
        Map<String, Object> m = new HashMap<>();
        m.put("clientIp", ipWhitelist.clientIp(request));
        m.put("allowed", ipWhitelist.isAllowed(request));
        m.put("note", "debug helper");
        return m;
    }

    // ── helpers ───────────────────────────────────────────
    private static String hmacB64Url(String key, String msg) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] sig = mac.doFinal(msg.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(sig);
        } catch (Exception e) {
            return "";
        }
    }

    private static String stripPad(String s) {
        int i = s.indexOf('=');
        return i >= 0 ? s.substring(0, i) : s;
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) return false;
        int r = 0;
        for (int i = 0; i < a.length(); i++) r |= a.charAt(i) ^ b.charAt(i);
        return r == 0;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static Map<String, Object> err(String msg) {
        Map<String, Object> m = new HashMap<>();
        m.put("msg", msg);
        m.put("code", 500);
        return m;
    }

    private static Map<String, Object> errIp() {
        Map<String, Object> m = new HashMap<>();
        m.put("msg", "非法ip地址");
        m.put("code", 500);
        return m;
    }
}
