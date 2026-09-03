package com.qxt.yuexiang.playlist.controller;

import com.qxt.yuexiang.common.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 链路验证用
 */
@RestController
public class PingController {

    @GetMapping("/ping")
    public Result<String> ping() {
        return Result.success("pong from yuexiang-playlist");
    }
}
