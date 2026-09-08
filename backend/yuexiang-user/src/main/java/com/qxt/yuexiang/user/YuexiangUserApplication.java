package com.qxt.yuexiang.user;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 登录注册与用户信息
 */
@SpringBootApplication(scanBasePackages = "com.qxt.yuexiang")
@EnableDiscoveryClient
@EnableFeignClients
public class YuexiangUserApplication {

    public static void main(String[] args) {
        SpringApplication.run(YuexiangUserApplication.class, args);
    }
}
