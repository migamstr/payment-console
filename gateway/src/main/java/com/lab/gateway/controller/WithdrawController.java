package com.lab.gateway.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 出款接口 —— 本项目里最危险的那个。
 *
 * 复刻真实系统的**三重缺失**：
 *   ① 无鉴权（任何请求都能进）
 *   ② memberId 不校验归属（填 999999999 直接进业务）
 *   ③ 部分通道的收款地址字段是裸的（不校验归属）
 *
 * 加上两个"信息泄漏"：
 *   ④ 余额探针：amount 超余额 → 只回显不建单
 *   ⑤ 金额梯度：minAmt=100 但它校验在余额之后 → amount=1 能建单
 *
 * 通道差异（真实差分出来的）：
 *   ch63 / m72  → 接受任意 TRC20 地址
 *   ch63 / m100 → 同上
 *   ch22 / m22  → 要求收款地址必须是本站会员钱包
 *   ch19 / m19  → 同上
 *   ch93 / m93  → 同上
 */
@RestController
@RequestMapping("/api/plat/payment")
public class WithdrawController {

    @Value("${lab.gateway.no-auth:true}")
    private boolean noAuth;

    @Value("${lab.gateway.unchecked-channels}")
    private List<String> uncheckedChannels;

    // 内存里的商户余额（本项目里可变）
    private static final Map<Integer, double[]> BALANCE = new LinkedHashMap<>();
    private static final Map<Integer, Boolean> CHECK_OWNER = new HashMap<>();
    private static final Map<Integer, Integer> MERCHANT_CHANNEL = new HashMap<>();

    static {
        BALANCE.put(72, new double[]{5721.62});
        BALANCE.put(100, new double[]{2600.00});
        BALANCE.put(22, new double[]{0.0});
        BALANCE.put(19, new double[]{0.0});
        BALANCE.put(93, new double[]{0.0});

        CHECK_OWNER.put(72, false);
        CHECK_OWNER.put(100, false);
        CHECK_OWNER.put(22, true);
        CHECK_OWNER.put(19, true);
        CHECK_OWNER.put(93, true);

        MERCHANT_CHANNEL.put(72, 63);
        MERCHANT_CHANNEL.put(100, 63);
        MERCHANT_CHANNEL.put(22, 22);
        MERCHANT_CHANNEL.put(19, 19);
        MERCHANT_CHANNEL.put(93, 93);
    }

    private static final Map<String, Map<String, Object>> ORDERS = new LinkedHashMap<>();

    @PostMapping("/createThirdProxyWithdrawOrder")
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        if (!noAuth) {
            return Map.of("code", 401, "msg", "请重新登录");
        }

        int operatorId = toInt(body.get("operatorId"), 10000);
        String memberId = String.valueOf(body.getOrDefault("memberId", ""));
        String orderNo = String.valueOf(body.getOrDefault("orderNo", "LAB" + System.currentTimeMillis() / 1000));
        double amount = toDouble(body.get("amount"), 0);
        String bankAccount = String.valueOf(body.getOrDefault("bankAccount", ""));

        // 解析目标通道
        Map<String, Object> weight = asMap(body.get("channelAndMerchantWeightMap"));
        if (weight.isEmpty()) {
            Map<String, Object> m = new HashMap<>();
            m.put("status", 1);
            m.put("message", "merchantId is null");
            m.put("merchantId", null);
            return m;
        }
        String firstChannel = weight.keySet().iterator().next();
        int channelId = toInt(firstChannel, 0);
        int merchantId = extractMerchantId(weight.get(firstChannel));
        if (!BALANCE.containsKey(merchantId)) {
            return Map.of("status", 1, "message", "商户号不正确");
        }

        // ── ④ 余额探针：超余额只回显不建单 ──────────────────
        double bal = BALANCE.get(merchantId)[0];
        if (amount > bal) {
            Map<String, Object> m = new HashMap<>();
            m.put("status", 1);
            m.put("message", String.format("可用余额不足，当前可用余额：%.4f", bal));
            m.put("merchantId", merchantId);
            m.put("channelId", channelId);
            return m;
        }

        // ── ③ 收款地址归属校验（通道差异）─────────────────────
        String key = channelId + "-" + merchantId;
        boolean check = CHECK_OWNER.getOrDefault(merchantId, true) && !uncheckedChannels.contains(key);
        if (check) {
            if (bankAccount == null || !bankAccount.startsWith("T") || bankAccount.length() < 20) {
                Map<String, Object> m = new HashMap<>();
                m.put("status", 1);
                m.put("message", "商户 " + merchantId + " 要求收款地址必须是本站会员钱包");
                m.put("merchantId", merchantId);
                return m;
            }
        }

        // ── ⑤ 金额梯度：minAmt 校验在余额之后 ─────────────────
        if (amount <= 0) {
            return Map.of("status", 1, "message", "提现金额必须大于0");
        }

        // ── 建单成功 ──────────────────────────────────────
        BALANCE.get(merchantId)[0] = bal - amount - 2.0;   // 扣本金 + 2 USDT 手续费
        String thirdOrderNo = String.valueOf(10000 + new Random().nextInt(90000));

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("orderNo", orderNo);
        row.put("operatorId", operatorId);
        row.put("memberId", memberId);
        row.put("amount", amount);
        row.put("fee", 2.0);
        row.put("bankAccount", bankAccount);
        row.put("channelId", channelId);
        row.put("merchantId", merchantId);
        row.put("thirdOrderNo", thirdOrderNo);
        row.put("createTime", new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        ORDERS.put(orderNo, row);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", 0);
        data.put("paymentChannelId", channelId);
        data.put("merchantId", merchantId);
        data.put("merchantNo", "1211" + merchantId);
        data.put("message", null);
        data.put("thirdOrderNo", thirdOrderNo);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", 0);
        m.put("msg", "成功");
        m.put("data", data);
        m.put("flag", "flag{lab_aggregator_withdraw}");
        return m;
    }

    @GetMapping("/order/detail")
    public Map<String, Object> detail(@RequestParam String orderNo) {
        if (!noAuth) return Map.of("code", 401);
        Map<String, Object> row = ORDERS.get(orderNo);
        if (row == null) return Map.of("code", 500, "msg", "订单不存在");
        return Map.of("code", 0, "msg", "success", "data", row);
    }

    // ── helpers ───────────────────────────────────────────
    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return o instanceof Map ? (Map<String, Object>) o : Map.of();
    }

    @SuppressWarnings("unchecked")
    private static int extractMerchantId(Object v) {
        try {
            if (v instanceof List<?> list && !list.isEmpty()) {
                Object first = list.get(0);
                if (first instanceof Map) {
                    return toInt(((Map<String, Object>) first).get("merchantId"), 0);
                }
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    private static int toInt(Object o, int def) {
        try {
            return o == null ? def : Integer.parseInt(o.toString());
        } catch (Exception e) {
            return def;
        }
    }

    private static double toDouble(Object o, double def) {
        try {
            return o == null ? def : Double.parseDouble(o.toString());
        } catch (Exception e) {
            return def;
        }
    }
}
