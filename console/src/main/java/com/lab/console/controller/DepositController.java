package com.lab.console.controller;

import com.lab.console.security.JwtUtil;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 充值与对账：充值单 / 三方代收 / 三方代付 / 回调。
 *
 * 复刻支付后台的标准资金入口：
 *   · 会员发起充值 → 生成充值单 + 收款地址
 *   · 通道回调通知 → 更新订单状态
 *   · 按通道 / 日期汇总对账
 */
@RestController
@RequestMapping("/prod-api/finance")
public class DepositController {

    private final JwtUtil jwtUtil;

    public DepositController(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    private static final List<Map<String, Object>> DEPOSITS = new ArrayList<>(List.of(
            row("orderNo", "DP20260101001", "memberId", 159, "userName", "u_159",
                "amount", 100.00, "actualAmount", 100.00, "fee", 0.00,
                "channelId", 63, "channelName", "TRC20-direct", "merchantId", 72, "merchantName", "ChannelA",
                "address", "TXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX", "txid", "0xabc…1f2",
                "status", 1, "statusText", "已到账", "createTime", "2026-01-01 09:12:00",
                "finishTime", "2026-01-01 09:13:41"),
            row("orderNo", "DP20260101002", "memberId", 160, "userName", "u_160",
                "amount", 287.88, "actualAmount", 287.88, "fee", 0.00,
                "channelId", 63, "channelName", "TRC20-direct", "merchantId", 100, "merchantName", "ChannelB",
                "address", "TYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYYY", "txid", "0xdef…2a3",
                "status", 1, "statusText", "已到账", "createTime", "2026-01-01 10:02:31",
                "finishTime", "2026-01-01 10:04:02"),
            row("orderNo", "DP20260101003", "memberId", 159, "userName", "u_159",
                "amount", 500.00, "actualAmount", 0.00, "fee", 0.00,
                "channelId", 63, "channelName", "TRC20-direct", "merchantId", 72, "merchantName", "ChannelA",
                "address", "TZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZZ", "txid", null,
                "status", 0, "statusText", "待支付", "createTime", "2026-01-01 11:20:08",
                "finishTime", null),
            row("orderNo", "DP20260101004", "memberId", 160, "userName", "u_160",
                "amount", 50.00, "actualAmount", 0.00, "fee", 0.00,
                "channelId", 22, "channelName", "C2C-wallet", "merchantId", 22, "merchantName", "ChannelC",
                "address", null, "txid", null,
                "status", 2, "statusText", "已过期", "createTime", "2026-01-01 08:01:00",
                "finishTime", "2026-01-01 08:31:00")
    ));

    private static final List<Map<String, Object>> THIRD_COLLECT = new ArrayList<>(List.of(
            row("id", 1, "batchNo", "TC20260101", "channelId", 63, "channelName", "TRC20-direct",
                "merchantId", 72, "merchantName", "ChannelA", "orderCount", 1284,
                "totalAmount", 486230.00, "fee", 972.46, "netAmount", 485257.54,
                "status", 1, "settleDate", "2026-01-01", "createTime", "2026-01-02 01:00:00")
    ));

    private static final List<Map<String, Object>> THIRD_PAYMENT = new ArrayList<>(List.of(
            row("id", 1, "batchNo", "TP20260101", "channelId", 63, "channelName", "TRC20-direct",
                "merchantId", 72, "merchantName", "ChannelA", "orderCount", 42,
                "totalAmount", 18620.00, "fee", 84.00, "netAmount", 18536.00,
                "status", 1, "settleDate", "2026-01-01", "createTime", "2026-01-02 01:00:00")
    ));

    // ══════════════════════════════════════════════════════
    // 充值单
    // ══════════════════════════════════════════════════════
    @GetMapping("/deposit/list")
    public Map<String, Object> depositList(@RequestHeader(value = "Authorization", required = false) String auth,
                                           @RequestParam(defaultValue = "1") int pageNum,
                                           @RequestParam(defaultValue = "10") int pageSize,
                                           @RequestParam(required = false) String orderNo,
                                           @RequestParam(required = false) Long memberId,
                                           @RequestParam(required = false) Integer status,
                                           @RequestParam(required = false) String beginTime,
                                           @RequestParam(required = false) String endTime) {
        if (unauth(auth)) return deny("/finance/deposit/list");
        List<Map<String, Object>> rows = DEPOSITS.stream()
                .filter(d -> orderNo == null || orderNo.isBlank() || String.valueOf(d.get("orderNo")).contains(orderNo))
                .filter(d -> memberId == null || memberId.equals(((Number) d.get("memberId")).longValue()))
                .filter(d -> status == null || status.equals(((Number) d.get("status")).intValue()))
                .filter(d -> beginTime == null || String.valueOf(d.get("createTime")).compareTo(beginTime) >= 0)
                .filter(d -> endTime == null || String.valueOf(d.get("createTime")).compareTo(endTime) <= 0)
                .collect(Collectors.toList());
        return page(rows, pageNum, pageSize);
    }

    @GetMapping("/deposit/detail/{orderNo}")
    public Map<String, Object> depositDetail(@RequestHeader(value = "Authorization", required = false) String auth,
                                             @PathVariable String orderNo) {
        if (unauth(auth)) return deny("/finance/deposit/detail/" + orderNo);
        for (Map<String, Object> d : DEPOSITS) {
            if (orderNo.equals(d.get("orderNo"))) {
                return Map.of("code", 200, "data", d);
            }
        }
        return Map.of("code", 500, "msg", "订单不存在");
    }

    /**
     * 创建充值单 —— 返回收款地址。
     *
     * 真实系统里这一步会调用通道侧的「获取充值地址」接口，并把地址回给前端。
     */
    @PostMapping("/deposit/create")
    public Map<String, Object> depositCreate(@RequestHeader(value = "Authorization", required = false) String auth,
                                             @RequestBody Map<String, Object> body) {
        if (unauth(auth)) return deny("/finance/deposit/create");
        Object amountObj = body.get("amount");
        if (amountObj == null) return Map.of("code", 500, "msg", "amount 不能为空");
        double amount;
        try {
            amount = Double.parseDouble(amountObj.toString());
        } catch (Exception e) {
            return Map.of("code", 500, "msg", "amount 格式错误");
        }
        if (amount <= 0) return Map.of("code", 500, "msg", "金额必须大于 0");

        String orderNo = "DP" + new java.text.SimpleDateFormat("yyyyMMddHHmmss").format(new Date())
                + String.format("%03d", new Random().nextInt(1000));

        int channelId = body.get("channelId") instanceof Number n ? n.intValue() : 63;
        int merchantId = body.get("merchantId") instanceof Number n2 ? n2.intValue() : 72;

        Map<String, Object> d = row(
                "orderNo", orderNo,
                "memberId", body.getOrDefault("memberId", 0),
                "userName", body.getOrDefault("userName", ""),
                "amount", amount, "actualAmount", 0.00, "fee", 0.00,
                "channelId", channelId, "channelName", channelName(channelId),
                "merchantId", merchantId, "merchantName", merchantName(merchantId),
                "address", "T" + randomBase58(33),
                "txid", null, "status", 0, "statusText", "待支付",
                "createTime", now(), "finishTime", null
        );
        DEPOSITS.add(0, d);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderNo", orderNo);
        data.put("amount", amount);
        data.put("address", d.get("address"));
        data.put("expireAt", System.currentTimeMillis() + 30 * 60 * 1000);
        return Map.of("code", 200, "msg", "success", "data", data);
    }

    /**
     * 通道回调。
     *
     * ⚠️ 真实系统里这一类回调**经常不做签名校验**，只靠"订单号 + 金额"匹配。
     * 这个接口保留了那个形态 —— 上生产前必须补验签（见 docs/CONFIG.md 的检查清单）。
     */
    @PostMapping("/deposit/callback")
    public Map<String, Object> depositCallback(@RequestBody Map<String, Object> body) {
        Object orderNoObj = body.get("orderNo");
        if (orderNoObj == null) {
            return Map.of("code", 500, "msg", "orderNo 不能为空");
        }
        String orderNo = orderNoObj.toString();
        for (Map<String, Object> d : DEPOSITS) {
            if (orderNo.equals(d.get("orderNo"))) {
                if (((Number) d.get("status")).intValue() != 0) {
                    return Map.of("code", 200, "msg", "订单已处理");
                }
                Object actual = body.get("actualAmount");
                d.put("actualAmount", actual != null ? Double.parseDouble(actual.toString()) : d.get("amount"));
                d.put("txid", body.getOrDefault("txid", null));
                d.put("status", 1);
                d.put("statusText", "已到账");
                d.put("finishTime", now());
                return Map.of("code", 200, "msg", "success");
            }
        }
        return Map.of("code", 500, "msg", "订单不存在");
    }

    /** 按通道汇总。 */
    @GetMapping("/deposit/summary")
    public Map<String, Object> depositSummary(@RequestHeader(value = "Authorization", required = false) String auth,
                                              @RequestParam(required = false) String beginTime,
                                              @RequestParam(required = false) String endTime) {
        if (unauth(auth)) return deny("/finance/deposit/summary");
        Map<Integer, double[]> agg = new LinkedHashMap<>();
        for (Map<String, Object> d : DEPOSITS) {
            if (((Number) d.get("status")).intValue() != 1) continue;
            int ch = ((Number) d.get("channelId")).intValue();
            double amt = ((Number) d.get("actualAmount")).doubleValue();
            agg.computeIfAbsent(ch, k -> new double[]{0, 0});
            agg.get(ch)[0] += amt;
            agg.get(ch)[1] += 1;
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<Integer, double[]> e : agg.entrySet()) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("channelId", e.getKey());
            r.put("channelName", channelName(e.getKey()));
            r.put("amount", Math.round(e.getValue()[0] * 100) / 100.0);
            r.put("count", (long) e.getValue()[1]);
            rows.add(r);
        }
        double total = rows.stream().mapToDouble(r -> ((Number) r.get("amount")).doubleValue()).sum();
        return Map.of("code", 200, "msg", "success", "rows", rows,
                      "totalAmount", Math.round(total * 100) / 100.0);
    }

    // ══════════════════════════════════════════════════════
    // 三方代收 / 代付
    // ══════════════════════════════════════════════════════
    @GetMapping("/thirdCollect/list")
    public Map<String, Object> thirdCollectList(@RequestHeader(value = "Authorization", required = false) String auth,
                                                @RequestParam(defaultValue = "1") int pageNum,
                                                @RequestParam(defaultValue = "10") int pageSize) {
        if (unauth(auth)) return deny("/finance/thirdCollect/list");
        return page(THIRD_COLLECT, pageNum, pageSize);
    }

    @GetMapping("/thirdPayment/list")
    public Map<String, Object> thirdPaymentList(@RequestHeader(value = "Authorization", required = false) String auth,
                                                @RequestParam(defaultValue = "1") int pageNum,
                                                @RequestParam(defaultValue = "10") int pageSize) {
        if (unauth(auth)) return deny("/finance/thirdPayment/list");
        return page(THIRD_PAYMENT, pageNum, pageSize);
    }

    // ── helpers ───────────────────────────────────────────
    private boolean unauth(String auth) {
        return auth == null || !auth.startsWith("Bearer ") || jwtUtil.parseUsername(auth.substring(7).trim()) == null;
    }

    private Map<String, Object> deny(String path) {
        return Map.of("msg", "请求访问：" + path + "，认证失败，无法访问系统资源，请登录后重试!", "code", 401);
    }

    private static String channelName(int ch) {
        return switch (ch) {
            case 63 -> "TRC20-direct";
            case 22 -> "C2C-wallet";
            case 19 -> "C2C-wallet-2";
            case 93 -> "C2C-wallet-3";
            default -> "unknown";
        };
    }

    private static String merchantName(int m) {
        return switch (m) {
            case 72 -> "ChannelA";
            case 100 -> "ChannelB";
            case 22 -> "ChannelC";
            default -> "unknown";
        };
    }

    private static String randomBase58(int len) {
        String alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
        Random r = new Random();
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) sb.append(alphabet.charAt(r.nextInt(alphabet.length())));
        return sb.toString();
    }

    private static Map<String, Object> page(List<Map<String, Object>> rows, int pageNum, int pageSize) {
        int from = Math.max(0, (pageNum - 1) * pageSize);
        int to = Math.min(rows.size(), from + pageSize);
        List<Map<String, Object>> slice = from >= rows.size() ? List.of() : rows.subList(from, to);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", 200);
        m.put("msg", "success");
        m.put("total", rows.size());
        m.put("rows", slice);
        return m;
    }

    private static String now() {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
    }

    private static Map<String, Object> row(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }
}
