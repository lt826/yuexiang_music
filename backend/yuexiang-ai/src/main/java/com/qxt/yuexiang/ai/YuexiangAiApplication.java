package com.qxt.yuexiang.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * AI 智能搜歌服务：DeepSeek Function Calling 编排自然语言搜歌
 */
@SpringBootApplication(scanBasePackages = "com.qxt.yuexiang")
@EnableDiscoveryClient
@EnableFeignClients
public class YuexiangAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(YuexiangAiApplication.class, args);
    }
}
