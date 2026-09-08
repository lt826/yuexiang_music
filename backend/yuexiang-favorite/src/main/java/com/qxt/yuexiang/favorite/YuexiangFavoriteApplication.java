package com.qxt.yuexiang.favorite;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 音乐收藏与收听历史
 */
@SpringBootApplication(scanBasePackages = "com.qxt.yuexiang")
@EnableDiscoveryClient
@EnableFeignClients
@EnableRabbit
public class YuexiangFavoriteApplication {

    public static void main(String[] args) {
        SpringApplication.run(YuexiangFavoriteApplication.class, args);
    }
}
