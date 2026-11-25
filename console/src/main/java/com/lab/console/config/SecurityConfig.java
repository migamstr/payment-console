package com.lab.console.config;

import com.lab.console.security.JwtAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * 鉴权配置。
 *
 * 真实系统里 RuoYi-Vue 的 SecurityConfig 长这样：
 *
 *   httpSecurity
 *       .authorizeRequests()
 *       .antMatchers("/login", "/register", "/captchaImage").permitAll()
 *       .antMatchers("/getInfo", "/getRouters").authenticated()
 *       .anyRequest().authenticated()
 *
 * 看起来没问题。但实际装配时：
 *   ① permitAll 列表被扩成了 `/prod-api/**`（为了"让前端好调试"）
 *   ② `anyRequest().authenticated()` 被注释掉了
 *   ③ filter 只对 `/getInfo` 生效（因为写在 specific matcher 里）
 *
 * 结果：所有 `/prod-api/*` 业务接口实际上都是匿名可达的。
 *
 * 本项目里保留这个装配，蓝队要做的是**发现它**，而不是利用它。
 */
@Configuration
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf().disable()
            .cors().configurationSource(corsSource())
            .and()
            .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            .and()
            .authorizeRequests()
            // ── 放行（真实系统里也是这么写的）──────────────
            .antMatchers("/prod-api/login", "/prod-api/iframeLogin",
                         "/prod-api/iframeLogin/**", "/prod-api/captchaImage").permitAll()
            // ⚠️ 洞：actuator 和 druid 忘了鉴权
            .antMatchers("/actuator/**", "/prod-api/actuator/**").permitAll()
            .antMatchers("/druid/**").permitAll()
            // ⚠️ 洞：静态资源用通配放行，但 `/prod-api/**` 也被这条吃掉了
            .antMatchers(HttpMethod.GET, "/prod-api/**").permitAll()
            .antMatchers(HttpMethod.POST, "/prod-api/**").permitAll()
            // ⚠️ 洞：doc.html / swagger 也放行
            .antMatchers("/doc.html", "/webjars/**", "/swagger-resources/**",
                         "/v2/api-docs", "/v3/api-docs/**").permitAll()
            .anyRequest().authenticated()
            .and()
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        // 让 /prod-api/getInfo 这类需要身份的接口由 filter 内部判断，
        // 而不是靠 Spring Security 的 matcher —— 复刻"两套鉴权并存"的混乱
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOriginPatterns(List.of("*"));
        cfg.setAllowedMethods(List.of("*"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", cfg);
        return src;
    }
}
