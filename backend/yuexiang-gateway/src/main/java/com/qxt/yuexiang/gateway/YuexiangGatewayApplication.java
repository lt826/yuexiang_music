package com.qxt.yuexiang.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * API 网关：统一入口，按 /api/{service}/** 转发到对应微服务
 */
@SpringBootApplication
@EnableDiscoveryClient
public class YuexiangGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(YuexiangGatewayApplication.class, args);
    }
}
