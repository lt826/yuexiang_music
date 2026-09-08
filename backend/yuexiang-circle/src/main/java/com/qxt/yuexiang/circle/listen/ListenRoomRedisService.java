package com.qxt.yuexiang.circle.listen;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 一起听房间实时状态存取（Redis）：
 * - 房间状态 Hash：listen:room:{roomId}（播放状态/歌曲/时间轴）
 * - 在线成员 Set：listen:room:{roomId}:members
 * - 禁言 Set：listen:room:{roomId}:muted
 * - 会话→房间映射：listen:session:{userId}（同一用户多开清理）
 * TTL 12 小时兜底：服务异常退出时状态自动过期，不留僵尸房间。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ListenRoomRedisService {

    private static final String ROOM_KEY = "listen:room:";
    private static final String MEMBERS_KEY = "listen:room:%s:members";
    private static final String MUTED_KEY = "listen:room:%s:muted";
    private static final String SESSION_KEY = "listen:session:";
    private static final String LIVE_ROOM_BY_CIRCLE = "listen:live:%s";

    private static final long TTL_SECONDS = 12 * 3600;

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    // ===== 房间状态 =====

    public void saveState(RoomState state) {
        Map<String, String> map = new java.util.HashMap<>();
        map.put("roomId", String.valueOf(state.getRoomId()));
        map.put("circleId", String.valueOf(state.getCircleId()));
        if (state.getTheme() != null) {
            map.put("theme", state.getTheme());
        }
        map.put("creatorId", String.valueOf(state.getCreatorId()));
        if (state.getCurrentSongId() != null) {
            map.put("currentSongId", String.valueOf(state.getCurrentSongId()));
        }
        if (state.getCurrentSongJson() != null) {
            map.put("currentSongJson", state.getCurrentSongJson());
        }
        map.put("status", state.getStatus());
        map.put("actionAt", String.valueOf(state.getActionAt()));
        map.put("position", String.valueOf(state.getPosition()));
        map.put("createdAt", String.valueOf(state.getCreatedAt()));
        redis.opsForHash().putAll(ROOM_KEY + state.getRoomId(), map);
        redis.expire(ROOM_KEY + state.getRoomId(), java.time.Duration.ofSeconds(TTL_SECONDS));
    }

    public RoomState loadState(Long roomId) {
        Map<Object, Object> raw = redis.opsForHash().entries(ROOM_KEY + roomId);
        if (raw.isEmpty()) {
            return null;
        }
        RoomState state = new RoomState();
        state.setRoomId(Long.valueOf((String) raw.get("roomId")));
        state.setCircleId(Long.valueOf((String) raw.get("circleId")));
        state.setTheme((String) raw.get("theme"));
        state.setCreatorId(Long.valueOf((String) raw.get("creatorId")));
        state.setCurrentSongId(raw.get("currentSongId") != null ? Long.valueOf((String) raw.get("currentSongId")) : null);
        state.setCurrentSongJson((String) raw.get("currentSongJson"));
        state.setStatus((String) raw.get("status"));
        state.setActionAt(raw.get("actionAt") != null ? Long.valueOf((String) raw.get("actionAt")) : 0L);
        state.setPosition(raw.get("position") != null ? Double.valueOf((String) raw.get("position")) : 0d);
        state.setCreatedAt(raw.get("createdAt") != null ? Long.valueOf((String) raw.get("createdAt")) : 0L);
        return state;
    }

    public void deleteState(Long roomId) {
        redis.delete(ROOM_KEY + roomId);
        redis.delete(MEMBERS_KEY.formatted(roomId));
        redis.delete(MUTED_KEY.formatted(roomId));
    }

    /** 圈子当前进行中的房间（一个圈子同时只允许一场一起听） */
    public void markLive(Long circleId, Long roomId) {
        redis.opsForValue().set(LIVE_ROOM_BY_CIRCLE.formatted(circleId), String.valueOf(roomId),
                java.time.Duration.ofSeconds(TTL_SECONDS));
    }

    public void unmarkLive(Long circleId) {
        redis.delete(LIVE_ROOM_BY_CIRCLE.formatted(circleId));
    }

    public Long liveRoomOf(Long circleId) {
        String v = redis.opsForValue().get(LIVE_ROOM_BY_CIRCLE.formatted(circleId));
        try {
            return v != null ? Long.valueOf(v) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ===== 在线成员 =====

    public void addMember(Long roomId, Long userId) {
        redis.opsForSet().add(MEMBERS_KEY.formatted(roomId), String.valueOf(userId));
        redis.expire(MEMBERS_KEY.formatted(roomId), java.time.Duration.ofSeconds(TTL_SECONDS));
    }

    public void removeMember(Long roomId, Long userId) {
        redis.opsForSet().remove(MEMBERS_KEY.formatted(roomId), String.valueOf(userId));
    }

    public Set<Long> members(Long roomId) {
        Set<String> raw = redis.opsForSet().members(MEMBERS_KEY.formatted(roomId));
        if (raw == null || raw.isEmpty()) {
            return Collections.emptySet();
        }
        return raw.stream().map(Long::valueOf).collect(Collectors.toSet());
    }

    public int memberCount(Long roomId) {
        Long size = redis.opsForSet().size(MEMBERS_KEY.formatted(roomId));
        return size == null ? 0 : size.intValue();
    }

    // ===== 禁言 =====

    public void mute(Long roomId, Long userId) {
        redis.opsForSet().add(MUTED_KEY.formatted(roomId), String.valueOf(userId));
        redis.expire(MUTED_KEY.formatted(roomId), java.time.Duration.ofSeconds(TTL_SECONDS));
    }

    public void unmute(Long roomId, Long userId) {
        redis.opsForSet().remove(MUTED_KEY.formatted(roomId), String.valueOf(userId));
    }

    public boolean isMuted(Long roomId, Long userId) {
        Boolean member = redis.opsForSet().isMember(MUTED_KEY.formatted(roomId), String.valueOf(userId));
        return Boolean.TRUE.equals(member);
    }

    // ===== 会话管理（同一用户重复加入时踢掉旧会话） =====

    public void bindSession(Long userId, String sessionId, Long roomId) {
        redis.opsForValue().set(SESSION_KEY + userId, sessionId + ":" + roomId,
                java.time.Duration.ofSeconds(TTL_SECONDS));
    }

    /** 取该用户旧会话（sessionId:roomId），无则返回 null */
    public String[] previousSession(Long userId) {
        String v = redis.opsForValue().get(SESSION_KEY + userId);
        if (v == null || !v.contains(":")) {
            return null;
        }
        return v.split(":", 2);
    }

    public void unbindSession(Long userId) {
        redis.delete(SESSION_KEY + userId);
    }

    /** 歌曲快照 JSON 编解码 */
    public String songJson(Object song) {
        try {
            return objectMapper.writeValueAsString(song);
        } catch (Exception e) {
            log.warn("歌曲快照序列化失败: {}", e.getMessage());
            return null;
        }
    }

    public Map<String, Object> parseSongJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            return null;
        }
    }
}
