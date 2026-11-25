package com.lab.gateway.config;

import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.spring.web.ShiroFilterFactoryBean;
import org.apache.shiro.web.mgt.CookieRememberMeManager;
import org.apache.shiro.web.mgt.DefaultWebSecurityManager;
import org.apache.shiro.web.servlet.SimpleCookie;
import org.apache.shiro.crypto.AesCipherService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ⚠️ 这个类是本项目里"方法论"含量最高的一个，因为它复刻了一个非常容易踩坑的点：
 *
 *   Shiro 1.2.4  →  AesCipherService 默认 AES/CBC/PKCS5Padding  （密文 432B）
 *   Shiro 1.4.2  →  默认改成 AES/GCM                            ← 就是这一版
 *   Shiro 1.7.1  →  AES/GCM/NoPadding                          （密文 436B）
 *
 * 含义：只要目标 shiro ≥ 1.4.2，rememberMe 必须是 **AES/GCM**。
 *      用 CBC 加密的 payload **100% 解不开，无论 key 对不对**。
 *
 * 所以"跑了几千个 key 全失败"很可能不是 key 不对，而是**加密模式用错了**。
 *
 * 阳性判据（已实测验证）：
 *   带 rememberMe=<随机>  → 必回 Set-Cookie: rememberMe=deleteMe; Max-Age=0
 *   不带 cookie            → 不回
 *   **响应里没有 deleteMe 才是 key 正确**
 *
 * 本项目里 cipherKey 默认是随机生成的（复刻 RuoYi >= 4.6.2 的官方行为：
 * application.yml 原文注释写着「默认启动生成随机秘钥」）。
 * 蓝队要做的是**确认它是随机的**，而不是继续爆破 1101 个字典 key。
 */
@Configuration
public class ShiroConfig {

    @Value("${lab.shiro.random-cipher-key:true}")
    private boolean randomCipherKey;

    /** 本项目里固定为随机 16 字节 —— 字典攻击期望值为 0。 */
    @Bean
    public byte[] cipherKey() {
        if (randomCipherKey) {
            byte[] k = new byte[16];
            new SecureRandom().nextBytes(k);
            // 只在启动日志里打一次，方便维护者核对（生产不会打）
            System.err.println("[lab] shiro cipherKey = " + Base64.getEncoder().encodeToString(k));
            return k;
        }
        // 非随机模式：用 RuoYi 3.x 的公开默认值（方便对比练习）
        return Base64.getDecoder().decode("fCq+/xW488hMTCD+cmJ3aQ==");
    }

    @Bean
    public CookieRememberMeManager rememberMeManager(byte[] cipherKey) {
        CookieRememberMeManager mgr = new CookieRememberMeManager();
        SimpleCookie cookie = new SimpleCookie("rememberMe");
        cookie.setMaxAge(3600);
        cookie.setHttpOnly(true);
        mgr.setCookie(cookie);

        // ⚠️ 关键：AesCipherService 在 1.4.2 里默认就是 GCM。
        //    很多人在这里手动 setMode(CBC) 去"兼容"，结果把 payload 加密错了。
        //    本项目里保持默认（GCM）。
        AesCipherService aes = new AesCipherService();
        mgr.setCipherKey(cipherKey);
        return mgr;
    }

    @Bean
    public SecurityManager securityManager(CookieRememberMeManager rememberMeManager) {
        DefaultWebSecurityManager sm = new DefaultWebSecurityManager();
        sm.setRememberMeManager(rememberMeManager);
        return sm;
    }

    /**
     * ⚠️ 示例核心：filterChain 里**故意只写了一条 anon**，
     *    而且 anon 的范围覆盖了 `/api/**` —— 复刻真实系统的"装配缺失"。
     */
    @Bean
    public ShiroFilterFactoryBean shiroFilter(SecurityManager securityManager) {
        ShiroFilterFactoryBean bean = new ShiroFilterFactoryBean();
        bean.setSecurityManager(securityManager);

        Map<String, String> chain = new LinkedHashMap<>();
        chain.put("/druid/**", "anon");
        chain.put("/v2/api-docs", "anon");
        chain.put("/swagger-resources/**", "anon");
        // ⚠️ 洞：整个 /api/** 都是 anon
        chain.put("/api/**", "anon");
        chain.put("/**", "anon");
        bean.setFilterChainDefinitionMap(chain);
        return bean;
    }
}
