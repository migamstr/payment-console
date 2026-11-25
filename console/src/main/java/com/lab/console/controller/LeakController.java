package com.lab.console.controller;

import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 静态泄漏面 —— 复刻真实系统里最常见的几类"忘了删"：
 *   · 备份 SQL / 数据库导出
 *   · .env / .git 配置
 *   · 上传目录开了 autoindex
 *
 * 这些是扫描器（dirsearch / ffuf / nuclei）能直接命中的入口。
 */
@RestController
public class LeakController {

    private static final String BACKUP_SQL = String.join("\n",
            "-- payment_lab backup 2026-01-01 02:00:00",
            "-- mysqldump --single-transaction --routines payment_lab",
            "",
            "CREATE TABLE `sys_user` (",
            "  `user_id` bigint NOT NULL AUTO_INCREMENT,",
            "  `user_name` varchar(64) NOT NULL,",
            "  `password` varchar(64) DEFAULT NULL,",
            "  PRIMARY KEY (`user_id`)",
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;",
            "",
            "INSERT INTO `sys_user` VALUES (1,'admin','21232f297a57a5a743894a0e4a801fc3');",
            "",
            "CREATE TABLE `sys_config` (",
            "  `config_id` bigint NOT NULL AUTO_INCREMENT,",
            "  `config_key` varchar(128) DEFAULT NULL,",
            "  `config_value` text,",
            "  PRIMARY KEY (`config_id`)",
            ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;",
            "",
            "INSERT INTO `sys_config` VALUES (104,'lab_jwt_secret','abcdefghijklmnopqrstuvwxyz');",
            "INSERT INTO `sys_config` VALUES (101,'pay_channel_config','{\"channels\":[{\"merchantId\":72,\"mchKey\":\"flag{lab_backup_mchkey}\"}]}');",
            "");

    private static final String DOT_ENV = String.join("\n",
            "# payment-console 环境变量",
            "SPRING_PROFILES_ACTIVE=prod",
            "SPRING_DATASOURCE_URL=jdbc:mysql://10.254.61.210:3306/payment_lab",
            "SPRING_DATASOURCE_USERNAME=root",
            "SPRING_DATASOURCE_PASSWORD=flag{lab_env_db_password}",
            "LAB_JWT_SECRET=abcdefghijklmnopqrstuvwxyz",
            "LAB_GATEWAY_NO_AUTH=true",
            "REDIS_HOST=10.254.211.187",
            "REDIS_PASSWORD=flag{lab_env_redis}",
            "");

    private static final String GIT_CONFIG = String.join("\n",
            "[core]",
            "\trepositoryformatversion = 0",
            "\tfilemode = true",
            "\tbare = false",
            "\tlogallrefupdates = true",
            "[remote \"origin\"]",
            "\turl = https://github_pat_REDACTED_EXAMPLE@github.com/ops/payment-console.git",
            "\tfetch = +refs/heads/*:refs/remotes/origin/*",
            "[branch \"main\"]",
            "\tremote = origin",
            "\tmerge = refs/heads/main",
            "");

    private static final Map<String, String> UPLOAD_FILES = new LinkedHashMap<>();
    static {
        UPLOAD_FILES.put("avatar_159.png", "2025-12-28 10:02:11  18K");
        UPLOAD_FILES.put("avatar_160.png", "2025-12-28 10:02:13  17K");
        UPLOAD_FILES.put("contract_2026.pdf", "2026-01-01 09:00:02  248K");
        UPLOAD_FILES.put("pay_channel_export_20251231.xlsx", "2025-12-31 23:59:58  1.2M");
        UPLOAD_FILES.put("db_backup_20251231.sql.gz", "2025-12-31 23:00:01  8.4M");
        UPLOAD_FILES.put("settle_report_202512.xlsx", "2026-01-01 01:00:00  412K");
    }

    // ── 备份文件 ──────────────────────────────────────────
    @GetMapping(value = "/backup.sql", produces = "text/plain;charset=UTF-8")
    public String backupSql() {
        return BACKUP_SQL;
    }

    @GetMapping(value = "/db.sql", produces = "text/plain;charset=UTF-8")
    public String dbSql() {
        return BACKUP_SQL;
    }

    @GetMapping(value = "/payment_lab.sql", produces = "text/plain;charset=UTF-8")
    public String paymentLabSql() {
        return BACKUP_SQL;
    }

    // ── 配置文件 ──────────────────────────────────────────
    @GetMapping(value = "/.env", produces = "text/plain;charset=UTF-8")
    public String dotEnv() {
        return DOT_ENV;
    }

    @GetMapping(value = "/.env.prod", produces = "text/plain;charset=UTF-8")
    public String dotEnvProd() {
        return DOT_ENV;
    }

    @GetMapping(value = "/.git/config", produces = "text/plain;charset=UTF-8")
    public String gitConfig() {
        return GIT_CONFIG;
    }

    @GetMapping(value = "/.git/HEAD", produces = "text/plain;charset=UTF-8")
    public String gitHead() {
        return "ref: refs/heads/main\n";
    }

    @GetMapping(value = "/.gitignore", produces = "text/plain;charset=UTF-8")
    public String gitignore() {
        return "target/\n*.log\n.env\n*.sql\nupload/\n";
    }

    // ── 上传目录（autoindex）──────────────────────────────
    @GetMapping(value = "/upload/", produces = "text/html;charset=UTF-8")
    public String uploadIndex() {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><head><title>Index of /upload/</title></head><body>\n");
        sb.append("<h1>Index of /upload/</h1><hr><pre><a href=\"../\">../</a>\n");
        for (Map.Entry<String, String> e : UPLOAD_FILES.entrySet()) {
            sb.append("<a href=\"").append(e.getKey()).append("\">")
              .append(e.getKey()).append("</a>")
              .append(" ".repeat(Math.max(1, 52 - e.getKey().length())))
              .append(e.getValue()).append("\n");
        }
        sb.append("</pre><hr></body></html>\n");
        return sb.toString();
    }

    @GetMapping(value = "/upload", produces = "text/html;charset=UTF-8")
    public String uploadIndexNoSlash() {
        return uploadIndex();
    }

    @GetMapping(value = "/upload/{name}", produces = "text/plain;charset=UTF-8")
    public String uploadFile(@PathVariable String name) {
        if (name.endsWith(".sql.gz")) {
            return "-- 需要解压：gzip -d " + name + "（示例环境未提供真实文件）\n";
        }
        if (name.endsWith(".xlsx") || name.endsWith(".pdf")) {
            return "binary file placeholder: " + name + "\n";
        }
        return "binary file placeholder: " + name + "\n";
    }
}
