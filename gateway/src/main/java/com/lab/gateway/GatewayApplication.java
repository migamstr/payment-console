package com.lab.gateway;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 聚合支付网关 —— 示例第二模块。
 *
 * 复刻的是「声明了鉴权但一个都没装配」这一类问题：
 *   · Swagger 给 75 个 operation 全标了 x-auth-token
 *   · 但 SecurityFilter 一条都没建
 *   · 而且出款接口的收款地址字段是裸的（不校验归属）
 *
 * 另外一个关键点：Shiro 用的是 1.4.2，
 * 这一版的 AesCipherService 默认是 AES/GCM（不是 CBC）。
 * 见 ShiroConfig 的注释。
 */
@SpringBootApplication
@MapperScan("com.lab.gateway.mapper")
public class GatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
