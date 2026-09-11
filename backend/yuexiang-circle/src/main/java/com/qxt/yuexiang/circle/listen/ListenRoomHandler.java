package com.qxt.yuexiang.circle.listen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qxt.yuexiang.circle.entity.Circle;
import com.qxt.yuexiang.circle.entity.CircleMember;
import com.qxt.yuexiang.circle.entity.ListenRoom;
import com.qxt.yuexiang.circle.entity.ListenRoomMember;
import com.qxt.yuexiang.circle.feign.SongClient;
import com.qxt.yuexiang.circle.feign.UserClient;
import com.qxt.yuexiang.circle.mapper.CircleMapper;
import com.qxt.yuexiang.circle.mapper.CircleMemberMapper;
import com.qxt.yuexiang.circle.mapper.ListenRoomMapper;
import com.qxt.yuexiang.circle.mapper.ListenRoomMemberMapper;
import com.qxt.yuexiang.common.exception.BusinessException;
import com.qxt.yuexiang.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 一起听房间 WebSocket 处理器
 * <p>
 * 协议（对齐需求文档 WebSocket 消息协议）：
 * 客户端 → 服务端：JOIN_ROOM / SEND_MESSAGE / PLAYBACK_CTRL / SYNC_STATE / HEARTBEAT
 * 服务端 → 客户端：ROOM_STATE / PLAYBACK_CTRL / CHAT_MESSAGE / USER_JOIN / USER_LEAVE /
 *                   MEMBER_MUTED / MEMBER_UNMUTED / KICKED / ROOM_CLOSED / SYSTEM / ERROR
 * <p>
 * 权限：加入房间须为圈内成员；播放控制/禁言/踢人/解散须圈主（发起人）/圈管理员/平台 ADMIN。
 * 播放时间轴：actionAt（服务器时刻）+ position（落定位置）下发，客户端按本地时钟推算应播进度。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ListenRoomHandler extends TextWebSocketHandler {

    private final ListenRoomRedisService redisService;
    private final ListenRoomMapper roomMapper;
    private final ListenRoomMemberMapper roomMemberMapper;
    private final CircleMapper circleMapper;
    private final CircleMemberMapper memberMapper;
    private final SongClient songClient;
    private final UserClient userClient;
    private final ObjectMapper objectMapper;

    /** roomId → (sessionId → session)；跨房间广播用 */
    private final Map<Long, Map<String, WebSocketSession>> roomSessions = new ConcurrentHashMap<>();

    // ===== 生命周期 =====

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // 等待客户端发送 JOIN_ROOM（房间归属在握手路径里，但成员校验要在 JOIN 时做）
        log.debug("一起听连接建立: sessionId={}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long roomId = sessionOf(session).roomId;
        Long userId = sessionOf(session).userId;
        if (roomId == null || userId == null) {
            return;
        }
        leaveRoom(session, roomId, userId, false);
        log.info("一起听连接关闭: roomId={}, userId={}, status={}", roomId, userId, status);
    }

    // ===== 消息入口 =====

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            JsonNode node = objectMapper.readTree(message.getPayload());
            String type = node.path("type").asText("");
            SessionCtx ctx = sessionOf(session);
            switch (type) {
                case "JOIN_ROOM" -> handleJoin(session, ctx, node);
                case "SEND_MESSAGE" -> handleChat(session, ctx, node);
                case "PLAYBACK_CTRL" -> handlePlayback(session, ctx, node);
                case "SYNC_STATE" -> sendRoomState(session, ctx.roomId);
                case "HEARTBEAT" -> reply(session, "SYSTEM", Map.of("event", "heartbeat", "serverTime", System.currentTimeMillis()));
                default -> reply(session, "ERROR", Map.of("message", "未知消息类型: " + type));
            }
        } catch (Exception e) {
            log.warn("一起听消息处理失败: sessionId={}, {}", session.getId(), e.getMessage());
            try {
                reply(session, "ERROR", Map.of("message", "消息处理失败"));
            } catch (IOException ignore) {
                // 会话已断开
            }
        }
    }

    // ===== JOIN_ROOM =====

    private void handleJoin(WebSocketSession session, SessionCtx ctx, JsonNode node) throws IOException {
        long roomId = node.path("roomId").asLong(0);
        Long userId = ctx.userId;
        String username = ctx.username;
        String role = ctx.role;

        // 1. 房间存在且进行中（Redis 状态兜底：数据库已结束则拒绝）
        ListenRoom room = roomMapper.selectById(roomId);
        if (room == null || room.getStatus() == null || room.getStatus() != 0) {
            reply(session, "ERROR", Map.of("message", "房间不存在或已结束"));
            return;
        }
        RoomState state = redisService.loadState(roomId);
        if (state == null) {
            // Redis 状态丢失（服务重启）：从数据库重建
            state = rebuildState(room);
            if (state == null) {
                reply(session, "ERROR", Map.of("message", "房间已结束"));
                return;
            }
        }

        // 2. 成员校验：圈内成员才能加入
        if (!isCircleMember(userId, room.getCircleId()) && !"ADMIN".equals(role)) {
            reply(session, "ERROR", Map.of("message", "请先加入圈子"));
            return;
        }

        // 3. 同一用户旧会话踢下线（重复打开页面）
        String[] prev = redisService.previousSession(userId);
        if (prev != null) {
            Long prevRoom = Long.valueOf(prev[1]);
            Map<String, WebSocketSession> sessions = roomSessions.get(prevRoom);
            WebSocketSession old = sessions != null ? sessions.remove(prev[0]) : null;
            if (old != null && old.isOpen()) {
                try {
                    reply(old, "ERROR", Map.of("message", "您的账号在其他页面重新加入了房间"));
                    old.close();
                } catch (IOException ignore) {
                    // 旧会话关闭失败无碍
                }
            }
            redisService.removeMember(prevRoom, userId);
            broadcastUserLeave(prevRoom, userId);
        }

        // 4. 入房：登记会话 + Redis 成员 + 流水表
        ctx.roomId = roomId;
        roomSessions.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>()).put(session.getId(), session);
        redisService.addMember(roomId, userId);
        redisService.bindSession(userId, session.getId(), roomId);
        recordJoin(roomId, userId);

        // 5. 下发房间全量状态 + 广播进房通知
        sendRoomState(session, roomId);
        Map<String, Object> user = userBrief(userId);
        broadcast(roomId, "USER_JOIN", mapOf(
                "userId", userId,
                "username", str(user.get("nickname"), username),
                "avatar", str(user.get("avatar"), ""),
                "online", redisService.memberCount(roomId)));
        broadcastSystem(roomId, str(user.get("nickname"), username) + " 加入了房间");
        log.info("加入一起听: roomId={}, userId={}", roomId, userId);
    }

    // ===== SEND_MESSAGE（聊天） =====

    private void handleChat(WebSocketSession session, SessionCtx ctx, JsonNode node) throws IOException {
        Long roomId = ctx.roomId;
        Long userId = ctx.userId;
        if (roomId == null) {
            reply(session, "ERROR", Map.of("message", "请先加入房间"));
            return;
        }
        // 禁言校验
        if (redisService.isMuted(roomId, userId)) {
            reply(session, "ERROR", Map.of("message", "您已被禁言"));
            return;
        }
        String content = node.path("content").asText("").trim();
        if (content.isEmpty()) {
            return;
        }
        if (content.length() > 300) {
            content = content.substring(0, 300);
        }
        Map<String, Object> user = userBrief(userId);
        Map<String, Object> data = new HashMap<>();
        data.put("userId", userId);
        data.put("username", str(user.get("nickname"), ctx.username));
        data.put("avatar", str(user.get("avatar"), ""));
        data.put("content", content);
        data.put("time", System.currentTimeMillis());
        broadcast(roomId, "CHAT_MESSAGE", data);
    }

    // ===== PLAYBACK_CTRL（播放控制，仅管理员） =====

    private void handlePlayback(WebSocketSession session, SessionCtx ctx, JsonNode node) throws IOException {
        Long roomId = ctx.roomId;
        Long userId = ctx.userId;
        if (roomId == null) {
            reply(session, "ERROR", Map.of("message", "请先加入房间"));
            return;
        }
        RoomState state = redisService.loadState(roomId);
        if (state == null) {
            reply(session, "ERROR", Map.of("message", "房间已结束"));
            return;
        }
        if (!isManager(userId, ctx.role, state)) {
            reply(session, "ERROR", Map.of("message", "仅圈主/管理员可控制播放"));
            return;
        }
        String action = node.path("action").asText("");
        JsonNode payload = node.path("payload");
        long now = System.currentTimeMillis();
        switch (action) {
            case "play" -> {
                state.setStatus("playing");
                state.setActionAt(now);
                state.setPosition(payload.path("time").asDouble(state.getPosition()));
            }
            case "pause" -> {
                // 暂停时按已流逝时长推算真实停点，避免快进漂移
                double drifted = driftPosition(state, now);
                state.setStatus("paused");
                state.setActionAt(now);
                state.setPosition(drifted);
            }
            case "seek" -> {
                state.setActionAt(now);
                state.setPosition(payload.path("time").asDouble(0d));
            }
            case "playSong" -> {
                // 切歌：客户端发歌曲ID，服务端校验后更新快照并从头播
                long songId = payload.path("songId").asLong(0);
                Map<String, Object> song = fetchSong(songId);
                if (song == null) {
                    reply(session, "ERROR", Map.of("message", "歌曲不存在或不可播放"));
                    return;
                }
                state.setCurrentSongId(songId);
                state.setCurrentSongJson(redisService.songJson(song));
                state.setStatus("playing");
                state.setActionAt(now);
                state.setPosition(0d);
            }
            case "stopSong" -> {
                state.setStatus("idle");
                state.setCurrentSongId(null);
                state.setCurrentSongJson(null);
                state.setActionAt(now);
                state.setPosition(0d);
            }
            default -> {
                reply(session, "ERROR", Map.of("message", "未知播放指令: " + action));
                return;
            }
        }
        redisService.saveState(state);
        // 广播控制指令（带 actionAt 服务器时间戳，客户端自行对时）
        Map<String, Object> data = new HashMap<>();
        data.put("action", action);
        data.put("timestamp", now);
        data.put("position", state.getPosition());
        data.put("status", state.getStatus());
        if (state.getCurrentSongId() != null) {
            data.put("songId", state.getCurrentSongId());
            Map<String, Object> song = redisService.parseSongJson(state.getCurrentSongJson());
            if (song != null) {
                data.put("song", song);
            }
        }
        broadcast(roomId, "PLAYBACK_CTRL", data);
    }

    // ===== 服务端 → 客户端 =====

    private void sendRoomState(WebSocketSession session, Long roomId) throws IOException {
        if (roomId == null) {
            return;
        }
        RoomState state = redisService.loadState(roomId);
        if (state == null) {
            reply(session, "ERROR", Map.of("message", "房间已结束"));
            return;
        }
        Set<Long> online = redisService.members(roomId);
        Map<String, Map<String, Object>> userMap = batchUsers(new ArrayList<>(online));

        List<Map<String, Object>> memberList = online.stream().map(uid -> {
            Map<String, Object> m = new HashMap<>();
            Map<String, Object> u = userMap.get(String.valueOf(uid));
            m.put("userId", uid);
            m.put("nickname", u != null ? str(u.get("nickname"), "") : "");
            m.put("avatar", u != null ? str(u.get("avatar"), "") : "");
            m.put("muted", redisService.isMuted(roomId, uid));
            // 发起人/圈主与管理员标识（前端展示禁言/踢人按钮）
            m.put("isManager", isManagerByState(uid, state));
            return m;
        }).collect(Collectors.toList());

        Map<String, Object> data = new HashMap<>();
        data.put("roomId", roomId);
        data.put("circleId", state.getCircleId());
        data.put("theme", state.getTheme());
        data.put("creatorId", state.getCreatorId());
        data.put("status", state.getStatus());
        data.put("actionAt", state.getActionAt());
        data.put("position", state.getPosition());
        if (state.getCurrentSongId() != null) {
            data.put("songId", state.getCurrentSongId());
            Map<String, Object> song = redisService.parseSongJson(state.getCurrentSongJson());
            if (song != null) {
                data.put("song", song);
            }
        }
        data.put("members", memberList);
        data.put("memberCount", online.size());
        reply(session, "ROOM_STATE", data);
    }

    private void broadcastUserLeave(Long roomId, Long userId) {
        Map<String, Object> user = userBrief(userId);
        broadcast(roomId, "USER_LEAVE", mapOf(
                "userId", userId,
                "username", str(user.get("nickname"), ""),
                "online", redisService.memberCount(roomId)));
        broadcastSystem(roomId, str(user.get("nickname"), "有人") + " 离开了房间");
    }

    private void broadcastSystem(Long roomId, String text) {
        broadcast(roomId, "SYSTEM", Map.of(
                "content", text,
                "time", System.currentTimeMillis()));
    }

    /** 向房间内所有会话广播（断开的会话顺手清理） */
    private void broadcast(Long roomId, String type, Object data) {
        Map<String, WebSocketSession> sessions = roomSessions.get(roomId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        String payload = toJson(Map.of("type", type, "data", data));
        sessions.values().removeIf(s -> {
            if (!s.isOpen()) {
                return true;
            }
            try {
                synchronized (s) {
                    s.sendMessage(new TextMessage(payload));
                }
                return false;
            } catch (IOException e) {
                return true;
            }
        });
    }

    private void reply(WebSocketSession session, String type, Object data) throws IOException {
        if (session.isOpen()) {
            synchronized (session) {
                session.sendMessage(new TextMessage(toJson(Map.of("type", type, "data", data))));
            }
        }
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{\"type\":\"ERROR\",\"data\":{\"message\":\"序列化失败\"}}";
        }
    }

    // ===== 离开房间 =====

    private void leaveRoom(WebSocketSession session, Long roomId, Long userId, boolean kicked) {
        Map<String, WebSocketSession> sessions = roomSessions.get(roomId);
        if (sessions != null) {
            sessions.remove(session.getId());
            if (sessions.isEmpty()) {
                roomSessions.remove(roomId);
            }
        }
        redisService.removeMember(roomId, userId);
        // 该用户没有其他在线会话时才解绑会话与写离房流水
        String[] still = redisService.previousSession(userId);
        if (still != null && still[0].equals(session.getId())) {
            redisService.unbindSession(userId);
            recordLeave(roomId, userId);
        }
        if (!kicked) {
            broadcastUserLeave(roomId, userId);
        }
        // 房间空了且发起人也已离开：自动结束房间（释放圈子的"进行中"名额）
        if (redisService.memberCount(roomId) == 0) {
            endRoom(roomId, null);
        }
    }

    /** 结束房间：数据库置已结束 + 清 Redis + 广播解散（kicked 场景用） */
    public void endRoom(Long roomId, Long operatorId) {
        ListenRoom room = roomMapper.selectById(roomId);
        if (room != null && room.getStatus() != null && room.getStatus() == 0) {
            room.setStatus(1);
            roomMapper.updateById(room);
        }
        // Redis 清理降级：实时层挂了也要保证 DB 里的房间正常结束（TTL 12h 兜底清残留 key）
        try {
            redisService.deleteState(roomId);
            if (room != null) {
                redisService.unmarkLive(room.getCircleId());
            }
        } catch (Exception e) {
            log.warn("结束房间清 Redis 失败（降级）: roomId={}, {}", roomId, e.getMessage());
        }
        broadcast(roomId, "ROOM_CLOSED", Map.of(
                "message", "一起听已结束",
                "by", operatorId == null ? 0 : operatorId,
                "time", System.currentTimeMillis()));
        Map<String, WebSocketSession> sessions = roomSessions.remove(roomId);
        if (sessions != null) {
            sessions.values().forEach(s -> {
                try {
                    if (s.isOpen()) {
                        s.close();
                    }
                } catch (IOException ignore) {
                    // 关闭失败无碍
                }
            });
        }
        log.info("一起听房间已结束: roomId={}, by={}", roomId, operatorId);
    }

    // ===== REST 联动（ListenRoomService 调用）=====

    /** 创建房间：写库 + 初始化 Redis 状态（REST 层已校验权限） */
    public RoomState createRoom(Long circleId, Long creatorId, String theme, Long initialSongId) {
        // 一个圈子同时只允许一场一起听（Redis 查重失败按"实时服务不可用"处理，不静默放行）
        Long live;
        try {
            live = redisService.liveRoomOf(circleId);
        } catch (Exception e) {
            log.error("发起一起听失败（Redis 不可用）: circleId={}, {}", circleId, e.getMessage());
            throw new BusinessException(ResultCode.ERROR.getCode(), "实时服务暂不可用，请稍后重试");
        }
        if (live != null) {
            RoomState liveState = redisService.loadState(live);
            if (liveState != null) {
                throw new BusinessException(ResultCode.ROOM_RUNNING);
            }
        }
        ListenRoom room = new ListenRoom();
        room.setCircleId(circleId);
        room.setCreatorId(creatorId);
        room.setTheme(theme);
        room.setInitialSongId(initialSongId);
        room.setStatus(0);
        roomMapper.insert(room);

        long now = System.currentTimeMillis();
        RoomState state = new RoomState();
        state.setRoomId(room.getId());
        state.setCircleId(circleId);
        state.setTheme(theme);
        state.setCreatorId(creatorId);
        state.setStatus("idle");
        state.setActionAt(now);
        state.setPosition(0d);
        state.setMemberCount(0);
        state.setCreatedAt(now);
        if (initialSongId != null) {
            Map<String, Object> song = fetchSong(initialSongId);
            if (song != null) {
                state.setCurrentSongId(initialSongId);
                state.setCurrentSongJson(redisService.songJson(song));
                state.setStatus("paused");
            }
        }
        // 实时状态初始化失败必须回滚房间：无 Redis 状态的房间成员无法加入
        try {
            redisService.saveState(state);
            redisService.markLive(circleId, room.getId());
        } catch (Exception e) {
            roomMapper.deleteById(room.getId());
            log.error("初始化房间实时状态失败，已回滚: roomId={}, {}", room.getId(), e.getMessage());
            throw new BusinessException(ResultCode.ERROR.getCode(), "实时服务暂不可用，请稍后重试");
        }
        log.info("一起听房间已创建: roomId={}, circleId={}, creator={}", room.getId(), circleId, creatorId);
        return state;
    }

    /** REST 踢人（网关 HTTP 侧禁言/踢人入口也走这里） */
    public void kick(Long roomId, Long targetUserId) {
        Map<String, WebSocketSession> sessions = roomSessions.get(roomId);
        if (sessions != null) {
            sessions.values().stream()
                    .filter(s -> userIdOf(s).equals(targetUserId))
                    .forEach(s -> {
                        try {
                            reply(s, "KICKED", Map.of("message", "您已被移出房间"));
                            s.close();
                        } catch (IOException ignore) {
                            // 关闭失败无碍
                        }
                    });
        }
        redisService.removeMember(roomId, targetUserId);
    }

    /** REST 禁言/解除禁言并广播 */
    public void setMuted(Long roomId, Long targetUserId, boolean muted) {
        if (muted) {
            redisService.mute(roomId, targetUserId);
        } else {
            redisService.unmute(roomId, targetUserId);
        }
        Map<String, Object> user = userBrief(targetUserId);
        broadcast(roomId, muted ? "MEMBER_MUTED" : "MEMBER_UNMUTED", mapOf(
                "userId", targetUserId,
                "username", str(user.get("nickname"), "")));
        broadcastSystem(roomId, str(user.get("nickname"), "成员")
                + (muted ? " 已被禁言" : " 已被解除禁言"));
    }

    // ===== 内部工具 =====

    private RoomState rebuildState(ListenRoom room) {
        if (room.getStatus() == null || room.getStatus() != 0) {
            return null;
        }
        long now = System.currentTimeMillis();
        RoomState state = new RoomState();
        state.setRoomId(room.getId());
        state.setCircleId(room.getCircleId());
        state.setTheme(room.getTheme());
        state.setCreatorId(room.getCreatorId());
        state.setStatus("idle");
        state.setActionAt(now);
        state.setPosition(0d);
        state.setCreatedAt(now);
        if (room.getInitialSongId() != null) {
            Map<String, Object> song = fetchSong(room.getInitialSongId());
            if (song != null) {
                state.setCurrentSongId(room.getInitialSongId());
                state.setCurrentSongJson(redisService.songJson(song));
                state.setStatus("paused");
            }
        }
        redisService.saveState(state);
        redisService.markLive(room.getCircleId(), room.getId());
        return state;
    }

    private boolean isCircleMember(Long userId, Long circleId) {
        Long count = memberMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CircleMember>()
                        .eq(CircleMember::getCircleId, circleId)
                        .eq(CircleMember::getUserId, userId));
        return count != null && count > 0;
    }

    /** 圈主（发起人）/圈管理员/平台 ADMIN 才能控播 */
    private boolean isManager(Long userId, String platformRole, RoomState state) {
        if ("ADMIN".equals(platformRole)) {
            return true;
        }
        return isManagerByState(userId, state);
    }

    private boolean isManagerByState(Long userId, RoomState state) {
        if (userId.equals(state.getCreatorId())) {
            return true;
        }
        Circle circle = circleMapper.selectById(state.getCircleId());
        if (circle != null && userId.equals(circle.getCreatorId())) {
            return true;
        }
        CircleMember member = memberMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CircleMember>()
                        .eq(CircleMember::getCircleId, state.getCircleId())
                        .eq(CircleMember::getUserId, userId)
                        .last("LIMIT 1"));
        return member != null && "ADMIN".equals(member.getRole());
    }

    private Map<String, Object> fetchSong(Long songId) {
        try {
            Map<String, Map<String, Object>> batch = songClient.batch(List.of(songId));
            return batch.get(String.valueOf(songId));
        } catch (Exception e) {
            log.warn("拉取歌曲信息失败: songId={}, {}", songId, e.getMessage());
            return null;
        }
    }

    private Map<String, Object> userBrief(Long userId) {
        try {
            Map<String, Map<String, Object>> users = userClient.batchInfo(List.of(userId));
            Map<String, Object> u = users.get(String.valueOf(userId));
            if (u != null) {
                return u;
            }
        } catch (Exception e) {
            log.warn("拉取用户信息失败（降级）: userId={}, {}", userId, e.getMessage());
        }
        Map<String, Object> fallback = new HashMap<>();
        fallback.put("nickname", "乐友" + userId);
        fallback.put("avatar", "");
        return fallback;
    }

    /** userId → {nickname, avatar}；key 为 userId 字符串（Feign JSON 反序列化后均为 String key） */
    private Map<String, Map<String, Object>> batchUsers(List<Long> userIds) {
        try {
            return userClient.batchInfo(userIds);
        } catch (Exception e) {
            Map<String, Map<String, Object>> fallback = new HashMap<>();
            userIds.forEach(id -> {
                Map<String, Object> u = new HashMap<>();
                u.put("nickname", "乐友" + id);
                u.put("avatar", "");
                fallback.put(String.valueOf(id), u);
            });
            return fallback;
        }
    }

    /** null 安全取字符串 */
    private String str(Object v, String def) {
        return v != null ? String.valueOf(v) : def;
    }

    /** null 值安全的 Map 构造（Map.of 遇 null 值会抛 NPE） */
    private Map<String, Object> mapOf(Object... kv) {
        Map<String, Object> m = new HashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    private void recordJoin(Long roomId, Long userId) {
        try {
            ListenRoomMember m = new ListenRoomMember();
            m.setRoomId(roomId);
            m.setUserId(userId);
            m.setJoinTime(LocalDateTime.now());
            roomMemberMapper.insert(m);
        } catch (Exception e) {
            log.warn("写进房流水失败: {}", e.getMessage());
        }
    }

    private void recordLeave(Long roomId, Long userId) {
        try {
            ListenRoomMember last = roomMemberMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ListenRoomMember>()
                            .eq(ListenRoomMember::getRoomId, roomId)
                            .eq(ListenRoomMember::getUserId, userId)
                            .isNull(ListenRoomMember::getLeaveTime)
                            .orderByDesc(ListenRoomMember::getJoinTime)
                            .last("LIMIT 1"));
            if (last != null) {
                last.setLeaveTime(LocalDateTime.now());
                roomMemberMapper.updateById(last);
            }
        } catch (Exception e) {
            log.warn("写离房流水失败: {}", e.getMessage());
        }
    }

    /** 播放位置漂移推算：paused 落定时刻的应播位置 */
    private double driftPosition(RoomState state, long now) {
        if (!"playing".equals(state.getStatus())) {
            return state.getPosition() == null ? 0d : state.getPosition();
        }
        double elapsed = Math.max(0, now - state.getActionAt()) / 1000.0;
        return (state.getPosition() == null ? 0d : state.getPosition()) + elapsed;
    }

    private Long userIdOf(WebSocketSession session) {
        return sessionOf(session).userId;
    }

    private SessionCtx sessionOf(WebSocketSession session) {
        SessionCtx ctx = (SessionCtx) session.getAttributes().get("ctx");
        if (ctx == null) {
            Long userId = (Long) session.getAttributes().get("userId");
            String username = (String) session.getAttributes().get("username");
            String role = (String) session.getAttributes().get("role");
            ctx = new SessionCtx(userId, username, role, null);
            session.getAttributes().put("ctx", ctx);
        }
        return ctx;
    }

    /** 会话上下文（握手时注入身份，JOIN 后补 roomId） */
    static class SessionCtx {
        final Long userId;
        final String username;
        final String role;
        volatile Long roomId;

        SessionCtx(Long userId, String username, String role, Long roomId) {
            this.userId = userId;
            this.username = username;
            this.role = role;
            this.roomId = roomId;
        }
    }
}
