package com.qxt.yuexiang.circle.service;

import com.qxt.yuexiang.circle.listen.RoomState;

import java.util.Map;

/**
 * 一起听房间 REST 服务：发起/列表/详情/结束/禁言/踢人（WebSocket 实时层见 listen 包）
 */
public interface ListenRoomService {

    /** 发起一起听（圈主/圈管理员/平台管理员）：创建房间并返回初始状态 */
    RoomState create(Long userId, String viewerRole, Long circleId, String theme, Long initialSongId);

    /** 圈子当前进行中的房间（无则返回 null） */
    Map<String, Object> liveOfCircle(Long circleId);

    /** 进行中的一起听房间列表（乐坊广场"正在一起听"） */
    java.util.List<Map<String, Object>> liveRooms(int limit);

    /** 结束一起听（圈主/圈管理员/平台管理员） */
    void end(Long userId, String viewerRole, Long roomId);

    /** 禁言/解除禁言（圈主/圈管理员/平台管理员） */
    void mute(Long userId, String viewerRole, Long roomId, Long targetUserId, boolean muted);

    /** 踢出成员（圈主/圈管理员/平台管理员） */
    void kick(Long userId, String viewerRole, Long roomId, Long targetUserId);
}
