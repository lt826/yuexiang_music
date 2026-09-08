package com.qxt.yuexiang.circle;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 乐坊（圈子 & 一起听）
 */
@SpringBootApplication(scanBasePackages = "com.qxt.yuexiang")
@EnableDiscoveryClient
@EnableFeignClients
public class YuexiangCircleApplication {

    public static void main(String[] args) {
        SpringApplication.run(YuexiangCircleApplication.class, args);
    }
}
