package com.qxt.yuexiang.circle.service.impl;

import com.qxt.yuexiang.circle.entity.Circle;
import com.qxt.yuexiang.circle.entity.CircleMember;
import com.qxt.yuexiang.circle.entity.ListenRoom;
import com.qxt.yuexiang.circle.feign.UserClient;
import com.qxt.yuexiang.circle.listen.ListenRoomHandler;
import com.qxt.yuexiang.circle.listen.ListenRoomRedisService;
import com.qxt.yuexiang.circle.listen.RoomState;
import com.qxt.yuexiang.circle.mapper.CircleMapper;
import com.qxt.yuexiang.circle.mapper.CircleMemberMapper;
import com.qxt.yuexiang.circle.mapper.ListenRoomMapper;
import com.qxt.yuexiang.circle.service.ListenRoomService;
import com.qxt.yuexiang.common.exception.BusinessException;
import com.qxt.yuexiang.common.result.ResultCode;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 一起听房间 REST 服务实现：权限校验 + 委托 WebSocket 层（ListenRoomHandler）
 * 权限：发起/结束/禁言/踢人 = 圈主（发起人）/圈管理员/平台 ADMIN；加入/聊天 = 圈内成员。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ListenRoomServiceImpl implements ListenRoomService {

    private final ListenRoomMapper roomMapper;
    private final CircleMapper circleMapper;
    private final CircleMemberMapper memberMapper;
    private final ListenRoomHandler roomHandler;
    private final ListenRoomRedisService redisService;
    private final UserClient userClient;

    private static final String PLATFORM_ADMIN = "ADMIN";

    @Override
    public RoomState create(Long userId, String viewerRole, Long circleId, String theme, Long initialSongId) {
        Circle circle = requireCircle(circleId);
        requireManager(userId, viewerRole, circle);
        // 发起人须为圈内成员（圈主天然是）
        RoomState state = roomHandler.createRoom(circleId, userId, theme, initialSongId);
        return state;
    }

    @Override
    public Map<String, Object> liveOfCircle(Long circleId) {
        Long liveId = redisService.liveRoomOf(circleId);
        if (liveId == null) {
            return null;
        }
        RoomState state = redisService.loadState(liveId);
        if (state == null) {
            return null;
        }
        return toMap(state);
    }

    @Override
    public List<Map<String, Object>> liveRooms(int limit) {
        // 扫描进行中房间（数据库 status=0），Redis 有状态的才算真正在线
        List<ListenRoom> rooms = roomMapper.selectList(new LambdaQueryWrapper<ListenRoom>()
                .eq(ListenRoom::getStatus, 0)
                .orderByDesc(ListenRoom::getCreateTime)
                .last("LIMIT " + Math.min(Math.max(limit, 1), 50)));
        List<Map<String, Object>> result = new ArrayList<>();
        for (ListenRoom room : rooms) {
            RoomState state = redisService.loadState(room.getId());
            if (state == null) {
                continue;
            }
            Map<String, Object> m = toMap(state);
            m.put("circleName", circleName(room.getCircleId()));
            result.add(m);
        }
        return result;
    }

    @Override
    public void end(Long userId, String viewerRole, Long roomId) {
        ListenRoom room = requireRoom(roomId);
        Circle circle = requireCircle(room.getCircleId());
        requireManager(userId, viewerRole, circle);
        // 仅发起人/圈主/圈管理员/平台管理员
        roomHandler.endRoom(roomId, userId);
    }

    @Override
    public void mute(Long userId, String viewerRole, Long roomId, Long targetUserId, boolean muted) {
        ListenRoom room = requireRoom(roomId);
        Circle circle = requireCircle(room.getCircleId());
        requireManager(userId, viewerRole, circle);
        roomHandler.setMuted(roomId, targetUserId, muted);
    }

    @Override
    public void kick(Long userId, String viewerRole, Long roomId, Long targetUserId) {
        ListenRoom room = requireRoom(roomId);
        Circle circle = requireCircle(room.getCircleId());
        // 发起人不能踢自己（无意义操作拦截）
        if (userId.equals(targetUserId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "不能移出自己");
        }
        requireManager(userId, viewerRole, circle);
        roomHandler.kick(roomId, targetUserId);
    }

    // ===== 内部 =====

    private Circle requireCircle(Long circleId) {
        Circle circle = circleMapper.selectById(circleId);
        if (circle == null) {
            throw new BusinessException(ResultCode.CIRCLE_NOT_EXIST);
        }
        return circle;
    }

    private ListenRoom requireRoom(Long roomId) {
        ListenRoom room = roomMapper.selectById(roomId);
        if (room == null) {
            throw new BusinessException(ResultCode.ROOM_NOT_EXIST);
        }
        return room;
    }

    private void requireManager(Long userId, String viewerRole, Circle circle) {
        if (PLATFORM_ADMIN.equals(viewerRole)) {
            return;
        }
        if (userId.equals(circle.getCreatorId())) {
            return;
        }
        CircleMember member = memberMapper.selectOne(new LambdaQueryWrapper<CircleMember>()
                .eq(CircleMember::getCircleId, circle.getId())
                .eq(CircleMember::getUserId, userId)
                .last("LIMIT 1"));
        if (member == null || !"ADMIN".equals(member.getRole())) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "需要圈主或圈管理员权限");
        }
    }

    private String circleName(Long circleId) {
        Circle circle = circleMapper.selectById(circleId);
        return circle != null ? circle.getName() : "";
    }

    private Map<String, Object> toMap(RoomState state) {
        Map<String, Object> m = new HashMap<>();
        m.put("roomId", state.getRoomId());
        m.put("circleId", state.getCircleId());
        m.put("theme", state.getTheme());
        m.put("creatorId", state.getCreatorId());
        m.put("status", state.getStatus());
        m.put("actionAt", state.getActionAt());
        m.put("position", state.getPosition());
        m.put("memberCount", redisService.memberCount(state.getRoomId()));
        if (state.getCurrentSongId() != null) {
            m.put("songId", state.getCurrentSongId());
            Map<String, Object> song = redisService.parseSongJson(state.getCurrentSongJson());
            if (song != null) {
                m.put("song", song);
            }
        }
        return m;
    }
}
