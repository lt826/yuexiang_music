package com.qxt.yuexiang.message;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 消息通知
 */
@SpringBootApplication(scanBasePackages = "com.qxt.yuexiang")
@EnableDiscoveryClient
@EnableFeignClients
@EnableRabbit
public class YuexiangMessageApplication {

    public static void main(String[] args) {
        SpringApplication.run(YuexiangMessageApplication.class, args);
    }
}
