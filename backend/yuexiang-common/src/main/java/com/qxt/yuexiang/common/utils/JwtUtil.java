package com.qxt.yuexiang.common.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

/**
 * JWT 工具：生成/解析 token
 */
public class JwtUtil {

    /** 签名密钥（HS256 要求至少 32 字节），生产环境应放到配置中心 */
    private static final String SECRET = "yuexiang-music-dreamer-secret-key-2026";

    /** 有效期 7 天（毫秒） */
    private static final long EXPIRATION = 7 * 24 * 60 * 60 * 1000L;

    private static SecretKey getKey() {
        return Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 生成 token，自定义声明（如 userId、username、role）放入 claims
     */
    public static String generateToken(Map<String, Object> claims) {
        return Jwts.builder()
                .claims(claims)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION))
                .signWith(getKey())
                .compact();
    }

    /**
     * 解析 token，失败（过期/篡改）抛出 JwtException 子类
     */
    public static Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
