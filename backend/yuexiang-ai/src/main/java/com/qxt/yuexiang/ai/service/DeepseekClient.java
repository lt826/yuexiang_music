package com.qxt.yuexiang.ai.service;

import com.qxt.yuexiang.ai.dto.DeepseekChatRequest;
import com.qxt.yuexiang.ai.vo.DeepseekChatResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

/**
 * DeepSeek OpenAI 兼容接口客户端
 * 文档：https://api-docs.deepseek.com （POST {base-url}/chat/completions，Bearer 鉴权）
 */
@Slf4j
@Component
public class DeepseekClient {

    private final WebClient webClient;
    private final String model;
    private final Duration timeout;

    public DeepseekClient(@Value("${deepseek.base-url}") String baseUrl,
                          @Value("${deepseek.api-key}") String apiKey,
                          @Value("${deepseek.model}") String model,
                          @Value("${deepseek.timeout-seconds:60}") int timeoutSeconds) {
        this.model = model;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /**
     * 同步调用 chat/completions（Function Calling 循环天然串行）。
     * block() 运行在调用线程，service 层保证不进 Netty 事件循环。
     */
    public DeepseekChatResponse chat(DeepseekChatRequest request) {
        request.setModel(model);
        long start = System.currentTimeMillis();
        DeepseekChatResponse response = webClient.post()
                .uri("/chat/completions")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(DeepseekChatResponse.class)
                .timeout(timeout)
                .block();
        log.info("DeepSeek 调用完成: 耗时={}ms, tokens={}",
                System.currentTimeMillis() - start,
                response != null && response.getUsage() != null ? response.getUsage().getTotalTokens() : -1);
        return response;
    }
}
