package com.lab.console.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.util.List;

/**
 * iframeLogin 的 IP 白名单 —— 登录准入的关键一层。
 *
 * 真实行为（已实测）：
 *   · 取的是 **真实 socket 源 IP**，不读任何转发头
 *   · 12 种头伪造（X-Forwarded-For / X-Real-IP / Forwarded / True-Client-IP …）全部无效
 *   · 最利的判据：XFF=999.999.999.999（非法 IP）与 XFF=super.superadmin666.com（合法 IP）响应逐字节一致
 *     → 服务端根本不解析这些头
 *
 * 这里的实现就是 `request.getRemoteAddr()`，故意不碰任何 header。
 */
@Component
public class IframeIpWhitelist {

    @Value("${lab.iframe.ip-allowlist}")
    private List<String> allowlist;

    public String clientIp(HttpServletRequest req) {
        return req.getRemoteAddr();
    }

    public boolean isAllowed(HttpServletRequest req) {
        return matches(clientIp(req));
    }

    private boolean matches(String ip) {
        try {
            InetAddress addr = InetAddress.getByName(ip);
            for (String entry : allowlist) {
                if (entry.contains("/")) {
                    if (inCidr(addr, entry)) return true;
                } else if (entry.equals(ip)) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /** 极简 CIDR 匹配（只处理 IPv4）。 */
    private boolean inCidr(InetAddress addr, String cidr) {
        try {
            String[] parts = cidr.split("/");
            byte[] net = InetAddress.getByName(parts[0]).getAddress();
            byte[] a = addr.getAddress();
            if (net.length != a.length) return false;
            int bits = Integer.parseInt(parts[1]);
            int full = bits / 8, rem = bits % 8;
            for (int i = 0; i < full; i++) if (net[i] != a[i]) return false;
            if (rem > 0) {
                int mask = 0xFF << (8 - rem);
                if ((net[full] & mask) != (a[full] & mask)) return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
