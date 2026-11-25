package com.lab.console.controller;

import com.lab.console.security.JwtUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * RuoYi-Vue 原生认证面 —— 本项目里复刻它的"结构性废弃"。
 *
 * 真实行为（已实测）：
 *   POST /prod-api/login        → {"msg":"验证码错误","code":500}
 *   GET  /prod-api/captchaImage → 404 + {"code":401,"msg":"登录已失效，请重新登录"}
 *
 * 登录要验证码，但拿验证码的接口 404 —— 这是二次开发改坏了。
 * 结果：口令通道**结构性不可用**，只剩 iframeLogin 一条路。
 *
 * 另外复刻 getInfo 的三态：
 *   无 token     → 200 + {"msg":"请求访问：/getInfo，认证失败…","code":401}（108 字节）
 *   任何 token   → 500 + /error（过滤器抛异常）—— 本项目里简化成"解析失败即无身份"
 */
@RestController
@RequestMapping("/prod-api")
public class AuthController {

    private final JwtUtil jwtUtil;

    public AuthController(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestParam(defaultValue = "") String username,
                                     @RequestParam(defaultValue = "") String password,
                                     @RequestParam(defaultValue = "") String code,
                                     @RequestParam(defaultValue = "") String uuid) {
        // 强制验证码，但验证码接口 404 → 永远拿不到正确 code
        return Map.of("msg", "验证码错误", "code", 500);
    }

    @GetMapping("/captchaImage")
    public ResponseEntity<Map<String, Object>> captchaImage() {
        // 复刻真实：nginx 层直接 404
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("code", 401, "msg", "登录已失效，请重新登录"));
    }

    @GetMapping("/getInfo")
    public Map<String, Object> getInfo(@RequestHeader(value = "Authorization", required = false) String auth) {
        String user = resolve(auth);
        if (user == null) {
            return Map.of("msg", "请求访问：/getInfo，认证失败，无法访问系统资源，请登录后重试!", "code", 401);
        }
        Map<String, Object> userObj = new HashMap<>();
        userObj.put("userId", 1);
        userObj.put("userName", user);
        userObj.put("nickName", "超级管理员");
        userObj.put("roles", List.of("admin"));

        Map<String, Object> m = new HashMap<>();
        m.put("code", 200);
        m.put("msg", "success");
        m.put("user", userObj);
        m.put("flag", "flag{lab_jwt_forge_admin}");
        return m;
    }

    @GetMapping("/getRouters")
    public Map<String, Object> getRouters(@RequestHeader(value = "Authorization", required = false) String auth) {
        String user = resolve(auth);
        if (user == null) {
            return Map.of("msg", "请求访问：/getRouters，认证失败，无法访问系统资源，请登录后重试!", "code", 401);
        }
        return Map.of("code", 200, "data", List.of(
                Map.of("path", "/payment", "component", "payment/index", "meta", Map.of("title", "支付管理")),
                Map.of("path", "/agent", "component", "agent/index", "meta", Map.of("title", "代理管理")),
                Map.of("path", "/member", "component", "member/index", "meta", Map.of("title", "会员管理"))
        ));
    }

    private String resolve(String auth) {
        if (auth == null || !auth.startsWith("Bearer ")) return null;
        return jwtUtil.parseUsername(auth.substring(7).trim());
    }
}
