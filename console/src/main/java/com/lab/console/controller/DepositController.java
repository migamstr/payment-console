package com.lab.console.controller;

import com.lab.console.security.JwtUtil;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 充值与对账：充值单 / 三方代收 / 三方代付 / 回调。
 *
 * 充值单走数据库；批次对账数据量小，暂留内存。
 */
@RestController
@RequestMapping("/prod-api/finance")
public class DepositController {

    private final JwtUtil jwtUtil;
    private final JdbcTemplate jdbc;

    public DepositController(JwtUtil jwtUtil, JdbcTemplate jdbc) {
        this.jwtUtil = jwtUtil;
        this.jdbc = jdbc;
    }

    private static final String DEPOSIT_COLS =
            "order_no AS \"orderNo\", member_id AS \"memberId\", user_name AS \"userName\", " +
            "amount, actual_amount AS \"actualAmount\", fee, channel_id AS \"channelId\", " +
            "channel_name AS \"channelName\", merchant_id AS \"merchantId\", merchant_name AS \"merchantName\", " +
            "address, txid, status, status_text AS \"statusText\", " +
            "create_time AS \"createTime\", finish_time AS \"finishTime\"";

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
        StringBuilder sql = new StringBuilder("SELECT " + DEPOSIT_COLS + " FROM deposit WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (orderNo != null && !orderNo.isBlank()) {
            sql.append(" AND order_no LIKE ?");
            args.add("%" + orderNo + "%");
        }
        if (memberId != null) {
            sql.append(" AND member_id = ?");
            args.add(memberId);
        }
        if (status != null) {
            sql.append(" AND status = ?");
            args.add(status);
        }
        if (beginTime != null && !beginTime.isBlank()) {
            sql.append(" AND create_time >= ?");
            args.add(beginTime);
        }
        if (endTime != null && !endTime.isBlank()) {
            sql.append(" AND create_time <= ?");
            args.add(endTime);
        }
        sql.append(" ORDER BY create_time DESC");
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());
        return page(rows, pageNum, pageSize);
    }

    @GetMapping("/deposit/detail/{orderNo}")
    public Map<String, Object> depositDetail(@RequestHeader(value = "Authorization", required = false) String auth,
                                             @PathVariable String orderNo) {
        if (unauth(auth)) return deny("/finance/deposit/detail/" + orderNo);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT " + DEPOSIT_COLS + " FROM deposit WHERE order_no = ?", orderNo);
        if (rows.isEmpty()) return Map.of("code", 500, "msg", "订单不存在");
        return Map.of("code", 200, "data", rows.get(0));
    }

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
        String address = "T" + randomBase58(33);

        try {
            jdbc.update("INSERT INTO deposit(order_no, member_id, user_name, amount, actual_amount, fee, " +
                        "channel_id, channel_name, merchant_id, merchant_name, address, txid, status, status_text, " +
                        "create_time, finish_time) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    orderNo,
                    body.getOrDefault("memberId", 0),
                    body.getOrDefault("userName", ""),
                    amount, 0.00, 0.00,
                    channelId, channelName(channelId),
                    merchantId, merchantName(merchantId),
                    address, null, 0, "待支付", now(), null);
        } catch (Exception e) {
            return Map.of("code", 500, "msg", "创建失败: " + e.getMessage());
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("orderNo", orderNo);
        data.put("amount", amount);
        data.put("address", address);
        data.put("expireAt", System.currentTimeMillis() + 30 * 60 * 1000);
        return Map.of("code", 200, "msg", "success", "data", data);
    }

    /**
     * 通道回调。
     *
     * ⚠️ 目前不校验签名，只靠 orderNo 匹配。生产环境必须补验签（见 docs/CONFIG.md）。
     */
    @PostMapping("/deposit/callback")
    public Map<String, Object> depositCallback(@RequestBody Map<String, Object> body) {
        Object orderNoObj = body.get("orderNo");
        if (orderNoObj == null) return Map.of("code", 500, "msg", "orderNo 不能为空");
        String orderNo = orderNoObj.toString();

        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT status FROM deposit WHERE order_no = ?", orderNo);
        if (rows.isEmpty()) return Map.of("code", 500, "msg", "订单不存在");
        if (((Number) rows.get(0).get("status")).intValue() != 0) {
            return Map.of("code", 200, "msg", "订单已处理");
        }

        double actual = body.get("actualAmount") instanceof Number n
                ? n.doubleValue()
                : jdbc.queryForObject("SELECT amount FROM deposit WHERE order_no = ?", Double.class, orderNo);
        jdbc.update("UPDATE deposit SET actual_amount=?, txid=?, status=1, status_text='已到账', finish_time=? WHERE order_no=?",
                actual,
                body.getOrDefault("txid", null),
                now(), orderNo);
        return Map.of("code", 200, "msg", "success");
    }

    @GetMapping("/deposit/summary")
    public Map<String, Object> depositSummary(@RequestHeader(value = "Authorization", required = false) String auth,
                                              @RequestParam(required = false) String beginTime,
                                              @RequestParam(required = false) String endTime) {
        if (unauth(auth)) return deny("/finance/deposit/summary");
        StringBuilder sql = new StringBuilder(
                "SELECT channel_id AS \"channelId\", channel_name AS \"channelName\", " +
                "SUM(actual_amount) AS \"amount\", COUNT(*) AS \"count\" " +
                "FROM deposit WHERE status = 1");
        List<Object> args = new ArrayList<>();
        if (beginTime != null && !beginTime.isBlank()) { sql.append(" AND create_time >= ?"); args.add(beginTime); }
        if (endTime != null && !endTime.isBlank()) { sql.append(" AND create_time <= ?"); args.add(endTime); }
        sql.append(" GROUP BY channel_id, channel_name");
        List<Map<String, Object>> rows = jdbc.queryForList(sql.toString(), args.toArray());

        double total = rows.stream()
                .mapToDouble(r -> r.get("amount") instanceof Number n ? n.doubleValue() : 0)
                .sum();
        return Map.of("code", 200, "msg", "success", "rows", rows,
                      "totalAmount", Math.round(total * 100) / 100.0);
    }

    // ══════════════════════════════════════════════════════
    // 三方代收 / 代付（批次对账）
    // ══════════════════════════════════════════════════════
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
