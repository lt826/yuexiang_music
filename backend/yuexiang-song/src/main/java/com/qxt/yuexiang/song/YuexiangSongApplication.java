package com.qxt.yuexiang.song;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 音乐搜索推荐与歌曲管理
 */
@SpringBootApplication(scanBasePackages = "com.qxt.yuexiang")
@EnableDiscoveryClient
@EnableFeignClients
@EnableScheduling
@EnableRabbit
public class YuexiangSongApplication {

    public static void main(String[] args) {
        SpringApplication.run(YuexiangSongApplication.class, args);
    }
}
