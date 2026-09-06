package com.qxt.yuexiang.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import com.qxt.yuexiang.common.result.Result;
import com.qxt.yuexiang.common.result.ResultCode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

/**
 * Sentinel 网关流控配置：
 * 按路由资源（route id）限流——写接口与贵资源严控，公开只读接口从宽。
 * 代码方式声明规则（重启生效），不依赖 dashboard 常驻；控制台连上后可动态调整。
 */
@Slf4j
@Configuration
public class GatewaySentinelConfig {

    private static final ObjectMapper JSON = new ObjectMapper();

    @PostConstruct
    public void initRules() {
        // 限流响应体：统一 Result 结构，前端 axios 拦截器可识别
        GatewayCallbackManager.setBlockHandler((exchange, t) -> {
            byte[] bytes;
            try {
                bytes = JSON.writeValueAsString(Result.fail(ResultCode.TOO_MANY_REQUESTS))
                        .getBytes(StandardCharsets.UTF_8);
            } catch (Exception e) {
                bytes = "{\"code\":429,\"message\":\"请求过于频繁，请稍后再试\",\"data\":null}"
                        .getBytes(StandardCharsets.UTF_8);
            }
            return ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(bytes);
        });

        Set<GatewayFlowRule> rules = new HashSet<>();
        // AI 问答：成本最高的资源，全局 20 QPS（防刷 DeepSeek token 是核心诉求）
        rules.add(new GatewayFlowRule("ai-route")
                .setCount(20).setIntervalSec(1));
        // 登录注册：防暴力破解，10 QPS 上限
        rules.add(new GatewayFlowRule("user-route")
                .setCount(10).setIntervalSec(1));
        // 歌曲核心接口：突发流量容忍（burst 20，允许瞬间峰值 30）
        rules.add(new GatewayFlowRule("song-route")
                .setCount(10).setBurst(20).setIntervalSec(1));
        // 文件上传下载：上传从严，但 GET 封面图/音频是静态资源——
        // 首页一次并发加载几十张封面，阈值必须远高于单页图片数（曾因 15 QPS 误伤封面加载返回 429）
        rules.add(new GatewayFlowRule("file-route")
                .setCount(200).setIntervalSec(1));
        GatewayRuleManager.loadRules(rules);
        log.info("Sentinel 网关流控规则已加载: {} 条路由规则", rules.size());
    }
}
