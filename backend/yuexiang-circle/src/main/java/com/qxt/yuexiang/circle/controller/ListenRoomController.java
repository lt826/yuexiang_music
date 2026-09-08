package com.qxt.yuexiang.circle.controller;

import com.qxt.yuexiang.circle.service.ListenRoomService;
import com.qxt.yuexiang.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 一起听房间 REST 接口（发起/房间信息/禁言/踢人/结束）
 * WebSocket 实时通道：/ws/listen/{roomId}?token=jwt（网关 /api/circle/ws/** 转发）
 */
@RestController
@RequestMapping("/circle")
@RequiredArgsConstructor
public class ListenRoomController {

    private final ListenRoomService listenRoomService;

    /** POST /circle/{circleId}/listen 发起一起听（圈主/圈管理员/平台管理员） */
    @PostMapping("/{circleId}/listen")
    public Result<Object> create(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String viewerRole,
            @PathVariable("circleId") Long circleId,
            @RequestParam(required = false) String theme,
            @RequestParam(required = false) Long songId) {
        return Result.success(listenRoomService.create(userId, viewerRole, circleId, theme, songId));
    }

    /** GET /circle/{circleId}/listen/live 圈子当前进行中的房间（圈内入口，无则 data 为空） */
    @GetMapping("/{circleId}/listen/live")
    public Result<Map<String, Object>> liveOfCircle(@PathVariable("circleId") Long circleId) {
        return Result.success(listenRoomService.liveOfCircle(circleId));
    }

    /** GET /circle/listen/live 全站进行中的一起听（乐坊广场"正在一起听"区） */
    @GetMapping("/listen/live")
    public Result<List<Map<String, Object>>> liveRooms(@RequestParam(defaultValue = "20") int limit) {
        return Result.success(listenRoomService.liveRooms(limit));
    }

    /** POST /circle/listen/{roomId}/end 结束一起听（圈主/圈管理员/平台管理员） */
    @PostMapping("/listen/{roomId}/end")
    public Result<Void> end(@RequestHeader("X-User-Id") Long userId,
                            @RequestHeader(value = "X-User-Role", required = false) String viewerRole,
                            @PathVariable("roomId") Long roomId) {
        listenRoomService.end(userId, viewerRole, roomId);
        return Result.success();
    }

    /** POST /circle/listen/{roomId}/mute 禁言/解除禁言（仅管理员） */
    @PostMapping("/listen/{roomId}/mute")
    public Result<Void> mute(@RequestHeader("X-User-Id") Long userId,
                             @RequestHeader(value = "X-User-Role", required = false) String viewerRole,
                             @PathVariable("roomId") Long roomId,
                             @RequestParam Long targetUserId,
                             @RequestParam boolean muted) {
        listenRoomService.mute(userId, viewerRole, roomId, targetUserId, muted);
        return Result.success();
    }

    /** DELETE /circle/listen/{roomId}/member/{targetUserId} 踢出成员（仅管理员） */
    @DeleteMapping("/listen/{roomId}/member/{targetUserId}")
    public Result<Void> kick(@RequestHeader("X-User-Id") Long userId,
                             @RequestHeader(value = "X-User-Role", required = false) String viewerRole,
                             @PathVariable("roomId") Long roomId,
                             @PathVariable("targetUserId") Long targetUserId) {
        listenRoomService.kick(userId, viewerRole, roomId, targetUserId);
        return Result.success();
    }
}
