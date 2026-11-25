package com.lab.console.security;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

/**
 * JWT 过滤器。
 *
 * 真实系统的 RuoYi JwtAuthenticationTokenFilter 有个微妙的问题：
 * 它只对 `SecurityContextHolder` 里没有身份时生效，而且
 * **如果 token 解析失败它不抛异常、直接放行** —— 后面的 controller
 * 再用 `SecurityUtils.getLoginUser()` 去抛 401。
 *
 * 本项目里保留这个行为：解析失败 = 无身份，但**请求继续走**。
 * 这样 `/prod-api/getInfo` 这类接口会返回业务层的 401 JSON，
 * 而不是 Spring Security 的 403 —— 复刻真实的响应形态。
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    public JwtAuthFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse resp, FilterChain chain)
            throws ServletException, IOException {
        String auth = req.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            String username = jwtUtil.parseUsername(auth.substring(7).trim());
            if (username != null) {
                var token = new UsernamePasswordAuthenticationToken(
                        username, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
                SecurityContextHolder.getContext().setAuthentication(token);
            }
            // ⚠️ 解析失败不拦 —— 交给业务层
        }
        chain.doFilter(req, resp);
    }
}
