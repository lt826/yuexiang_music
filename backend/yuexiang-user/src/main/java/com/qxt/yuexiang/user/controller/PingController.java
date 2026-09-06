package com.qxt.yuexiang.user.controller;

import com.qxt.yuexiang.common.result.Result;
import com.qxt.yuexiang.user.config.WelcomeProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 链路验证用
 */
@RestController
@RequiredArgsConstructor
public class PingController {

    private final WelcomeProperties welcomeProperties;

    @GetMapping("/user/ping")
    public Result<String> ping() {
        return Result.success("pong from yuexiang-user");
    }

    /** 查看当前从 Nacos 拉到的 user.welcome 值（演示热刷新） */
    @GetMapping("/user/config")
    public Result<String> config() {
        return Result.success(welcomeProperties.getWelcome());
    }
}
