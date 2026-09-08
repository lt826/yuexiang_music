package com.qxt.yuexiang.file;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 文件上传下载
 */
@SpringBootApplication(scanBasePackages = "com.qxt.yuexiang")
@EnableDiscoveryClient
@EnableFeignClients
public class YuexiangFileApplication {

    public static void main(String[] args) {
        SpringApplication.run(YuexiangFileApplication.class, args);
    }
}
