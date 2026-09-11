package com.qxt.yuexiang.gateway.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qxt.yuexiang.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;

/**
 * 网关异常统一兜底：下游服务未启动（Nacos 无实例 → 503）、响应超时（504）等场景，
 * 返回统一 Result 结构，前端 axios 拦截器直接展示 message。
 * 此前走 Spring 默认错误体（无 message 字段），前端只能弹兜底的"网络异常，请稍后重试"，
 * 用户看不出是"某个微服务没启动"，排查成本高。
 */
@Slf4j
@Order(-2)   // 需早于 Spring 默认的 DefaultErrorWebExceptionHandler（order -1）才能接管
@Component
public class GatewayExceptionHandler implements ErrorWebExceptionHandler {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        // 归类异常：路由/负载均衡抛的都是 ResponseStatusException（503 无实例、404 无路由）
        HttpStatus status;
        if (ex instanceof ResponseStatusException rse) {
            HttpStatus resolved = HttpStatus.resolve(rse.getStatusCode().value());
            status = resolved == null ? HttpStatus.INTERNAL_SERVER_ERROR : resolved;
        } else if (ex instanceof TimeoutException) {
            status = HttpStatus.GATEWAY_TIMEOUT;
        } else {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        // 面向用户的提示语（技术细节只进日志）
        String message = switch (status) {
            case SERVICE_UNAVAILABLE -> "服务暂时不可用，请稍后重试或联系管理员";
            case GATEWAY_TIMEOUT -> "服务响应超时，请稍后重试";
            case NOT_FOUND -> "接口不存在";
            default -> "服务异常，请稍后重试";
        };
        log.warn("网关兜底: {} {} -> {} {} ({})", exchange.getRequest().getMethod(),
                exchange.getRequest().getURI().getPath(), status.value(), message, ex.getMessage());

        byte[] bytes;
        try {
            bytes = JSON.writeValueAsString(Result.fail(status.value(), message))
                    .getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            bytes = ("{\"code\":" + status.value() + ",\"message\":\"" + message + "\",\"data\":null}")
                    .getBytes(StandardCharsets.UTF_8);
        }
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
    }
}
