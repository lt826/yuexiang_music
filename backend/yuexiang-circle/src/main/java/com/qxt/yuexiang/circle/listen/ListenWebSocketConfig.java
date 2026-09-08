package com.qxt.yuexiang.circle.listen;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 一起听 WebSocket 配置：/ws/listen/{roomId} 端点
 * setAllowedOriginPatterns("*")：开发期 vite 代理与直连网关均可；鉴权由握手拦截器完成
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class ListenWebSocketConfig implements WebSocketConfigurer {

    private final ListenRoomHandler listenRoomHandler;
    private final ListenHandshakeInterceptor listenHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(listenRoomHandler, "/ws/listen/{roomId}")
                .addInterceptors(listenHandshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }
}
