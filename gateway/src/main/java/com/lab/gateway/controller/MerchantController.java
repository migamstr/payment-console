package com.lab.gateway.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 商户 / 通道 / 订单面 —— 本项目里**故意零鉴权**。
 *
 * 真实系统（已实测）：
 *   GET /api/plat/payment/merchantAndPaymentChannels
 *   → 无 token / 乱 token / 空 token 三种响应**字节完全一致**
 *   → 说明 Swagger 里声明的 x-auth-token 过滤器一个都没装配
 *
 * 蓝队要做的第一件事就是「三态对照」：
 *   for H in "" "x-auth-token: garbage" "x-auth-token: "; do curl -H "$H" ... | md5sum; done
 * 三个 md5 一样 = 鉴权没装。
 */
@RestController
@RequestMapping("/api/plat/payment")
public class MerchantController {

    @Value("${lab.gateway.no-auth:true}")
    private boolean noAuth;

    private static final List<Map<String, Object>> MERCHANTS = List.of(
            Map.of("merchantId", 72, "merchantNo", "121138", "merchantName", "ChannelA",
                    "channelId", 63, "checkOwner", false, "balance", 5721.62),
            Map.of("merchantId", 100, "merchantNo", "121139", "merchantName", "ChannelB",
                    "channelId", 63, "checkOwner", false, "balance", 2600.00),
            Map.of("merchantId", 22, "merchantNo", "121140", "merchantName", "ChannelC",
                    "channelId", 22, "checkOwner", true, "balance", 0.00),
            Map.of("merchantId", 19, "merchantNo", "121141", "merchantName", "ChannelD",
                    "channelId", 19, "checkOwner", true, "balance", 0.00),
            Map.of("merchantId", 93, "merchantNo", "121142", "merchantName", "ChannelE",
                    "channelId", 93, "checkOwner", true, "balance", 0.00)
    );

    private static final List<Map<String, Object>> CHANNELS = List.of(
            Map.of("channelId", 63, "channelName", "TRC20-direct", "minAmt", 100, "maxAmt", 50000),
            Map.of("channelId", 22, "channelName", "C2C-wallet", "minAmt", 10, "maxAmt", 5000),
            Map.of("channelId", 19, "channelName", "C2C-wallet-2", "minAmt", 10, "maxAmt", 5000),
            Map.of("channelId", 93, "channelName", "C2C-wallet-3", "minAmt", 10, "maxAmt", 5000)
    );

    private static final Map<Integer, String> OPERATORS = Map.of(
            10000, "Tenant-A", 10005, "Tenant-B", 10012, "Tenant-C", 10013, "Tenant-D"
    );

    /**
     * ⚠️ 零鉴权。真实系统里这个接口曾匿名返回 45 家商户 + 完整通道配置。
     */
    @GetMapping("/merchantAndPaymentChannels")
    public Map<String, Object> merchantAndChannels(
            @RequestParam(defaultValue = "1") int bizType,
            @RequestParam(defaultValue = "10000") int operatorId) {
        if (!noAuth) {
            return Map.of("code", 401, "msg", "请重新登录", "data", Map.of("value", "logout"));
        }
        Map<String, Object> data = new HashMap<>();
        data.put("operatorId", operatorId);
        data.put("operatorName", OPERATORS.getOrDefault(operatorId, "unknown"));
        data.put("channels", CHANNELS);
        data.put("merchants", MERCHANTS);

        Map<String, Object> m = new HashMap<>();
        m.put("code", 0);
        m.put("msg", "success");
        m.put("data", data);
        return m;
    }

    @GetMapping("/merchant/list")
    public Map<String, Object> merchantList() {
        if (!noAuth) return Map.of("code", 401, "msg", "请重新登录");
        return Map.of("code", 0, "msg", "success", "total", MERCHANTS.size(), "rows", MERCHANTS);
    }

    @GetMapping("/channel/list")
    public Map<String, Object> channelList() {
        if (!noAuth) return Map.of("code", 401, "msg", "请重新登录");
        return Map.of("code", 0, "msg", "success", "total", CHANNELS.size(), "rows", CHANNELS);
    }

    @GetMapping("/order/list")
    public Map<String, Object> orderList(@RequestParam(defaultValue = "10000") int operatorId) {
        if (!noAuth) return Map.of("code", 401, "msg", "请重新登录");
        return Map.of("code", 0, "msg", "success", "total", 0, "rows", List.of());
    }
}
