package com.lab.console.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JWT 工具 —— 手工实现 HS256，不用 jjwt。
 *
 * 为什么不用 jjwt：
 *   jjwt 0.11.x 的 `signWith(key, alg)` 和 `Keys.hmacShaKeyFor()` 都会强制检查
 *   密钥长度（要求 ≥256 bit = 32 字节），而真实的弱密钥
 *   （`abcdefghijklmnopqrstuvwxyz`，26 字节 = 208 bit）会被它直接拒掉。
 *
 *   真实系统里能跑，是因为它用的要么是旧版 jjwt（0.9.x），要么是
 *   java-jwt / 手写实现 —— 这些都不检查长度。
 *
 *   所以示例这里也手写：**弱密钥能用**，才是这个洞成立的前提。
 *
 * ⚠️ 本项目的另一个洞：parseUsername 直接信任 payload 里的 login_user_key，
 *    不回查会话表。真实 RuoYi 用它当 Redis 键（login_tokens:<uuid>），
 *    即使签过也拿不到会话对象 —— 这里简化了，方便蓝队打通完整链路。
 */
@Component
public class JwtUtil {

    @Value("${lab.jwt.secret}")
    private String secret;

    @Value("${lab.jwt.expire-minutes:720}")
    private long expireMinutes;

    private static final Pattern USER_KEY = Pattern.compile("\"login_user_key\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern SUB = Pattern.compile("\"sub\"\\s*:\\s*\"([^\"]*)\"");

    public String create(String username) {
        long now = System.currentTimeMillis() / 1000;
        long exp = now + expireMinutes * 60;
        String header = b64url("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payload = b64url("{\"login_user_key\":\"" + username + "\",\"sub\":\"" + username + "\",\"iat\":" + now + ",\"exp\":" + exp + "}");
        String signingInput = header + "." + payload;
        return signingInput + "." + b64url(hmacSha256(signingInput));
    }

    /** 校验签名 + 解析身份；任何一步失败都返回 null（交给业务层拒绝）。 */
    public String parseUsername(String token) {
        if (token == null) return null;
        String[] parts = token.split("\\.");
        if (parts.length != 3) return null;

        String expected = b64url(hmacSha256(parts[0] + "." + parts[1]));
        if (!constantTimeEquals(expected, parts[2])) return null;

        try {
            String json = new String(Base64.getUrlDecoder().decode(pad(parts[1])), StandardCharsets.UTF_8);
            Matcher m = USER_KEY.matcher(json);
            if (m.find()) return m.group(1);
            m = SUB.matcher(json);
            if (m.find()) return m.group(1);
        } catch (Exception ignored) {
        }
        return null;
    }

    public String getSecret() {
        return secret;
    }

    // ── helpers ───────────────────────────────────────────
    private byte[] hmacSha256(String input) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            // ⚠️ 这里用 SecretKeySpec，不检查长度 —— 26 字节的弱密钥才能用
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC 失败", e);
        }
    }

    private static String b64url(String s) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String b64url(byte[] b) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    /** base64url 解码前补 padding。 */
    private static String pad(String s) {
        int r = s.length() % 4;
        return r == 0 ? s : s + "====".substring(r);
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) return false;
        int r = 0;
        for (int i = 0; i < a.length(); i++) r |= a.charAt(i) ^ b.charAt(i);
        return r == 0;
    }
}
