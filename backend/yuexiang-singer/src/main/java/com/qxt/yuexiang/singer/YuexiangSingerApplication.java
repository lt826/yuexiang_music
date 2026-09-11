package com.qxt.yuexiang.singer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 歌手管理
 */
@SpringBootApplication(scanBasePackages = "com.qxt.yuexiang")
@EnableDiscoveryClient
@EnableFeignClients
public class YuexiangSingerApplication {

    public static void main(String[] args) {
        SpringApplication.run(YuexiangSingerApplication.class, args);
    }
}
