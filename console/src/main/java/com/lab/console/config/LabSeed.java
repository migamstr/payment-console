package com.lab.console.config;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 启动时准备一些"运维遗留"的配置文件。
 *
 * 复刻场景：备份/配置文件被放在 web 目录旁边，被文件读取接口翻出来。
 * 这些文件在 /tmp/lab-secrets 下，不在 classpath 里。
 */
@Component
public class LabSeed {

    public static final String SECRETS_DIR = "/tmp/lab-secrets";
    public static final String PROFILE_DIR = "/tmp/lab-profile";

    private static final String APPLICATION_PROD = String.join("\n",
            "server:",
            "  port: 8080",
            "spring:",
            "  datasource:",
            "    url: jdbc:mysql://10.254.61.210:3306/payment_lab",
            "    username: root",
            "    password: flag{lab_lfi_datasource}",
            "  redis:",
            "    host: 10.254.211.187",
            "    port: 6379",
            "    password: flag{lab_lfi_redis}",
            "token:",
            "  secret: abcdefghijklmnopqrstuvwxyz",
            "  expireTime: 720",
            "");

    private static final String PAY_CHANNEL_BACKUP = String.join("\n",
            "{",
            "  \"_comment\": \"支付通道备份（运维遗留）\",",
            "  \"channels\": [",
            "    {\"name\": \"ChannelA\", \"mchId\": \"121138\", \"mchKey\": \"flag{lab_lfi_yfb_key}\", \"dfGateway\": \"https://198.51.100.92/api/pay\"},",
            "    {\"name\": \"ChannelB\", \"mchId\": \"121139\", \"mchKey\": \"flag{lab_lfi_dfuf_key}\", \"dfGateway\": \"https://198.51.100.92/api/pay\"}",
            "  ]",
            "}",
            "");

    private static final String OPS_NOTE = String.join("\n",
            "-- 运维备注 --",
            "1. 数据库口令见 application-prod.yml",
            "2. 支付通道密钥见 pay-channel-backup.json",
            "3. 本目录不要挂到 web 静态目录下",
            "");

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        try {
            Path secrets = Paths.get(SECRETS_DIR);
            Path profile = Paths.get(PROFILE_DIR);
            Files.createDirectories(secrets);
            Files.createDirectories(profile);

            writeIfAbsent(secrets.resolve("application-prod.yml"), APPLICATION_PROD);
            writeIfAbsent(secrets.resolve("pay-channel-backup.json"), PAY_CHANNEL_BACKUP);
            writeIfAbsent(secrets.resolve("README-ops.txt"), OPS_NOTE);
            writeIfAbsent(profile.resolve("readme.txt"), "this is the upload profile dir\n");

            System.err.println("[lab] secrets seeded at " + SECRETS_DIR);
        } catch (Exception e) {
            System.err.println("[lab] seed failed: " + e.getMessage());
        }
    }

    private static void writeIfAbsent(Path p, String content) throws Exception {
        File f = p.toFile();
        if (!f.exists()) {
            Files.write(p, content.getBytes(StandardCharsets.UTF_8));
        }
    }
}
