package com.qxt.yuexiang.user.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

/**
 * 演示 @RefreshScope 热刷新：Nacos 上改 user.welcome 并发布后，
 * 不重启服务，本 Bean 自动重建为新值
 */
@Data
@RefreshScope
@Component
@ConfigurationProperties(prefix = "user")
public class WelcomeProperties {

    private String welcome = "default-local-value";
}
