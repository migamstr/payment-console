package com.lab.console;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 支付控制台 —— 示例主应用。
 *
 * 复刻的是「声明了鉴权但装配错位」这一类问题：
 *   · Spring Security 配了，但 permitAll 列表把关键端点漏出去了
 *   · JWT 校验配了，但密钥用的是框架默认值
 *   · IP 白名单配了，但判定顺序在账号校验之前
 *   · Actuator / Druid 配了，但忘了鉴权
 */
@SpringBootApplication
@MapperScan("com.lab.console.mapper")
public class ConsoleApplication {
    public static void main(String[] args) {
        SpringApplication.run(ConsoleApplication.class, args);
    }
}
