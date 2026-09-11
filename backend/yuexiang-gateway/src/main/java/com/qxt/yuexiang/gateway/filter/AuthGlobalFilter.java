package com.qxt.yuexiang.gateway.filter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qxt.yuexiang.common.result.Result;
import com.qxt.yuexiang.common.result.ResultCode;
import com.qxt.yuexiang.common.utils.JwtUtil;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 网关统一鉴权：白名单放行，其余请求校验 JWT 后透传用户身份（对齐详细设计 4.2 请求处理流程）。
 * 校验通过后查 Redis JWT 黑名单（登出即时失效），黑名单命中按 401 拒绝。
 */
@Slf4j
@Component
public class AuthGlobalFilter implements GlobalFilter, Ordered {

    private final ReactiveStringRedisTemplate redisTemplate;

    public AuthGlobalFilter(ReactiveStringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /** JWT 黑名单 key 前缀（token 的 SHA-256 摘要作 key，TTL = token 剩余有效期） */
    private static final String BLACKLIST_PREFIX = "auth:blacklist:";

    /** token 摘要（避免把完整 token 当 key，key 过长且泄露原文） */
    private String tokenDigest(String token) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            // JVM 必带 SHA-256，理论不可达
            throw new IllegalStateException(e);
        }
    }

    /** 白名单：登录注册、首页只读展示、搜索与播放（匿名可听）、链路探活、文件访问 */
    private static final List<String> WHITE_LIST = List.of(
            "/user/login", "/user/register", "/user/config",
            "/admin/login",
            // 门户首页实时统计（匿名可看，只读聚合计数）
            "/admin/stats",
            // 首页轮播图当前上架项（匿名可看）
            "/admin/banner/active",
            "/song/chart", "/song/recommend", "/song/new",
            "/song/guess",
            "/song/chart/realtime", "/song/search/hot",
            "/song/search", "/song/play/**",
            "/singer/hot", "/singer/names",
            // 歌手主页闭环：主页聚合/歌手搜索/歌手作品/TA的公开歌单（匿名可看，只读）
            // virtual-follows：用户公开主页聚合"关注的虚拟歌手"（匿名可看，只读）
            "/singer/*/home", "/singer/search", "/song/by-singer/*", "/playlist/by-user/*",
            "/singer/virtual-follows",
            "/playlist/recommend", "/playlist/hot", "/playlist/user-created",
            // 心情电台调频：只读公共节目单与播放位置，匿名可看（起播登录由前端播放器拦截）
            "/ai/radio/tune",
            // 一起听 WebSocket 握手：浏览器 WS 无法带 Authorization 头，token 在 URL 参数，
            // 鉴权由 circle 服务握手拦截器完成（网关放行透传）
            "/circle/ws/**",
            // 乐坊浏览：广场/进行中的一起听列表匿名可看；
            // 圈子详情/帖子流/评论列表走方法级白名单（见 isWhiteListed，仅 GET 数字路径）；
            // /circle/review/** 不在白名单（角色矩阵限 ADMIN）
            // 文件下载/在线访问：/file/{type}/{storedName}；/file/upload 不在白名单，仍需登录
            "/file/*/*",
            "/**/ping",
            "/error"
    );

    /** OPTIONS 预检请求放行（跨域） */
    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 全局过滤器在 StripPrefix 之前执行，看到的是 /api/user/login 这种完整路径，
        // 去掉 /api 前缀后再匹配白名单（与下游服务内的接口路径保持一致）
        String servicePath = path.startsWith("/api/") ? path.substring(4) : path;

        // 预检请求与白名单直接放行；白名单路径若带了合法 token，则顺手注入身份（收藏态等需要）
        if (request.getMethod() == HttpMethod.OPTIONS || isWhiteListed(servicePath, request.getMethod())) {
            ServerHttpRequest withIdentity = tryInjectIdentity(request);
            return chain.filter(withIdentity == null ? exchange
                    : exchange.mutate().request(withIdentity).build());
        }

        // 提取 Authorization 头（前端约定不带 Bearer 前缀）
        String token = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (token == null || token.isBlank()) {
            return unauthorized(exchange, ResultCode.UNAUTHORIZED); 
        }
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        // 校验 token，解析出用户身份
        Claims claims;
        try {
            claims = JwtUtil.parseToken(token);
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("网关鉴权失败: path={}, 原因={}", path, e.getMessage());
            return unauthorized(exchange, ResultCode.UNAUTHORIZED);
        }

        Long userId = claims.get("adminId", Number.class) != null
                ? claims.get("adminId", Number.class).longValue()
                : (claims.get("userId", Number.class) != null
                        ? claims.get("userId", Number.class).longValue()
                        : null);
        String username = claims.get("username", String.class);
        String role = claims.get("role", String.class);
        if (userId == null || role == null) {
            return unauthorized(exchange, ResultCode.UNAUTHORIZED);
        }

        // 角色校验（对齐详细设计 3.3 双层权限：网关层做角色准入，服务内做归属校验）
        if (!hasRequiredRole(servicePath, role)) {
            log.warn("网关角色拦截: path={}, role={}", path, role);
            return forbidden(exchange);
        }

        // JWT 黑名单校验（登出即时失效）；Redis 故障时放行（可用性优先，token 本身签名仍有效）
        String tokenDigest = tokenDigest(token);
        return redisTemplate.hasKey(BLACKLIST_PREFIX + tokenDigest)
                .onErrorResume(e -> {
                    log.warn("黑名单查询失败（放行）: {}", e.getMessage());
                    return Mono.just(false);
                })
                .flatMap(blacklisted -> {
                    if (Boolean.TRUE.equals(blacklisted)) {
                        log.info("黑名单 token 拦截: path={}, userId={}", path, userId);
                        return unauthorized(exchange, ResultCode.UNAUTHORIZED);
                    }
                    return chain.filter(exchange.mutate().request(buildMutated(request, userId, username, role)).build());
                });
    }

    private ServerHttpRequest buildMutated(ServerHttpRequest request, Long userId, String username, String role) {
        return request.mutate()
                .header("X-User-Id", String.valueOf(userId))
                .header("X-Username", username == null ? "" : username)
                .header("X-User-Role", role)
                .build();
    }

    /**
     * 角色矩阵：
     * /admin/**（除登录）需要 ADMIN；
     * /message/admin/** 管理员收件箱仅 ADMIN（防普通用户直读 user_id=0 的待审通知）；
     * /message/send、/message/notify-admins、/message/broadcast 为服务间内部接口，
     * 不对前端暴露（Nacos 内网调用），网关侧再兜底拦截；
     * /playlist/admin/** 管理端歌单维护（官方歌单/电台节目单加删歌）仅 ADMIN；
     * /song/publish、/song/my、PUT /song/{id}、/song/{id}/offline 需要 SINGER 或 ADMIN
     * （歌曲归属校验由 song 服务内完成）
     */
    private boolean hasRequiredRole(String servicePath, String role) {
        if (servicePath.startsWith("/admin/") && !"/admin/login".equals(servicePath)) {
            return "ADMIN".equals(role);
        }
        if (servicePath.startsWith("/message/admin/")
                || servicePath.startsWith("/playlist/admin/")
                // 乐坊审核：圈子名称审核仅平台 ADMIN（走管理端页面）
                || servicePath.startsWith("/circle/review/")
                || "/message/send".equals(servicePath)
                || "/message/notify-admins".equals(servicePath)
                || "/message/broadcast".equals(servicePath)) {
            return "ADMIN".equals(role);
        }
        boolean singerApi = "/song/publish".equals(servicePath)
                || "/song/my".equals(servicePath)
                || "/singer/my-profile".equals(servicePath)
                || servicePath.matches("^/song/\\d+/offline$")
                || servicePath.matches("^/song/\\d+/resubmit$")
                // PUT /song/{id} 修改歌曲信息，与发布/下架同为歌手专属接口
                || servicePath.matches("^/song/\\d+$");
        if (singerApi) {
            return "SINGER".equals(role) || "ADMIN".equals(role);
        }
        return true;
    }

    private boolean isWhiteListed(String path, HttpMethod method) {
        // 歌单详情匿名可看：仅 GET /playlist/{数字}；DELETE/PUT 同样路径必须走鉴权
        if (path.matches("^/playlist/\\d+$") && method == HttpMethod.GET) {
            return true;
        }
        // 用户公开主页匿名可看：仅 GET /user/{数字}（昵称/头像/签名，不含隐私字段）；其余 /user/** 仍走鉴权
        if (path.matches("^/user/\\d+$") && method == HttpMethod.GET) {
            return true;
        }
        // 用户互关浏览匿名可看：关注/粉丝计数与列表（条目点击进公开主页）；POST 关注操作仍需登录
        if (method == HttpMethod.GET
                && (path.matches("^/user/\\d+/follow-summary$")
                || path.matches("^/user/\\d+/following$")
                || path.matches("^/user/\\d+/followers$"))) {
            return true;
        }
        // 乐坊匿名浏览（仅 GET）：
        // 广场 GET /circle/square；圈子详情 GET /circle/{数字}；帖子流 GET /circle/{数字}/posts；
        // 评论列表 GET /circle/post/{数字}/comments；进行中的一起听 GET /circle/listen/live、GET /circle/{数字}/listen/live
        if (method == HttpMethod.GET) {
            if ("/circle/square".equals(path)
                    || path.matches("^/circle/\\d+$")
                    || path.matches("^/circle/\\d+/posts$")
                    || path.matches("^/circle/post/\\d+/comments$")
                    || "/circle/listen/live".equals(path)
                    || path.matches("^/circle/\\d+/listen/live$")) {
                return true;
            }
        }
        // 歌曲互动数据匿名可看：仅 GET 查询（互动计数/评论列表/回复列表）；点赞/发评/删评等写操作必须走鉴权
        if (path.matches("^/song/\\d+/interaction$") && method == HttpMethod.GET) {
            return true;
        }
        if (path.matches("^/song/\\d+/comments$") && method == HttpMethod.GET) {
            return true;
        }
        if (path.matches("^/song/comment/\\d+/replies$") && method == HttpMethod.GET) {
            return true;
        }
        return WHITE_LIST.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
    }

    /** 白名单路径可选身份注入：token 合法则透传身份，无/无效 token 返回 null 走匿名 */
    private ServerHttpRequest tryInjectIdentity(ServerHttpRequest request) {
        String token = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (token == null || token.isBlank()) {
            return null;
        }
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        try {
            Claims claims = JwtUtil.parseToken(token);
            Long userId = claims.get("userId", Number.class) != null
                    ? claims.get("userId", Number.class).longValue()
                    : (claims.get("adminId", Number.class) != null
                            ? claims.get("adminId", Number.class).longValue()
                            : null);
            String role = claims.get("role", String.class);
            if (userId == null || role == null) {
                return null;
            }
            return request.mutate()
                    .header("X-User-Id", String.valueOf(userId))
                    .header("X-Username", claims.get("username", String.class) == null ? "" : claims.get("username", String.class))
                    .header("X-User-Role", role)
                    .build();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    /** 403：已登录但角色不足（2007 权限不足语义） */
    private Mono<Void> forbidden(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body;
        try {
            body = OBJECT_MAPPER.writeValueAsString(Result.fail(ResultCode.FORBIDDEN));
        } catch (Exception e) {
            body = "{\"code\":403,\"message\":\"无权限访问\"}";
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        return response.writeWith(
                Mono.just(response.bufferFactory().wrap(bytes)));
    }

    /** 以统一响应体返回 401，前端 axios 拦截器按 code===401 踢回登录页 */
    private Mono<Void> unauthorized(ServerWebExchange exchange, ResultCode code) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String body;
        try {
            body = OBJECT_MAPPER.writeValueAsString(Result.fail(code));
        } catch (Exception e) {
            body = "{\"code\":401,\"message\":\"未登录或 token 已失效\"}";
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        return response.writeWith(
                Mono.just(response.bufferFactory().wrap(bytes)));
    }

    @Override
    public int getOrder() {
        // 限流之后、路由转发之前（数值越小优先级越高）
        return -100;
    }
}
