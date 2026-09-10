package com.qxt.yuexiang.circle.listen;

import com.qxt.yuexiang.common.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 一起听握手拦截器：WebSocket 握手阶段校验 JWT（浏览器 WebSocket 无法自定义 Authorization 头，
 * token 由前端通过 URL 参数携带：/ws/listen/{roomId}?token=xxx）。
 * 校验通过后把 userId/username/role 放入握手 attributes，供 Handler 会话使用。
 */
@Slf4j
@Component
public class ListenHandshakeInterceptor implements HandshakeInterceptor {

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String query = request.getURI().getQuery();
        String token = null;
        if (query != null) {
            for (String pair : query.split("&")) {
                int eq = pair.indexOf('=');
                if (eq > 0 && "token".equals(pair.substring(0, eq))) {
                    token = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
                    break;
                }
            }
        }
        if (token == null || token.isBlank()) {
            log.warn("一起听握手拒绝：缺少 token");
            return false;
        }
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        try {
            Claims claims = JwtUtil.parseToken(token);
            Long userId = claims.get("userId", Number.class) != null
                    ? claims.get("userId", Number.class).longValue() : null;
            String role = claims.get("role", String.class);
            if (userId == null || role == null) {
                return false;
            }
            attributes.put("userId", userId);
            attributes.put("username", claims.get("username", String.class));
            attributes.put("role", role);
            return true;
        } catch (Exception e) {
            log.warn("一起听握手拒绝：token 无效（{}）", e.getMessage());
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 无需处理
    }
}
