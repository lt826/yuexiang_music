package com.qxt.yuexiang.playlist.controller;

import com.qxt.yuexiang.common.result.Result;
import com.qxt.yuexiang.playlist.dto.PlaylistSaveDTO;
import com.qxt.yuexiang.playlist.service.PlaylistService;
import com.qxt.yuexiang.playlist.vo.PlaylistVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 歌单接口（FUN-07：创建/编辑/删除、加歌/删歌、列表/详情）
 */
@RestController
@RequestMapping("/playlist")
@RequiredArgsConstructor
public class PlaylistController {

    private final PlaylistService playlistService;

    /** GET /playlist/recommend 官方推荐歌单（首页，匿名） */
    @GetMapping("/recommend")
    public Result<List<PlaylistVO>> recommend(@RequestParam(defaultValue = "10") int limit) {
        return Result.success(playlistService.recommend(limit));
    }

    /** GET /playlist/hot 热门歌单（歌单广场，匿名） */
    @GetMapping("/hot")
    public Result<List<PlaylistVO>> hot(@RequestParam(defaultValue = "20") int limit) {
        return Result.success(playlistService.hot(limit));
    }

    /** GET /playlist/user-created 最新用户歌单（歌单广场「乐友歌单」区，匿名） */
    @GetMapping("/user-created")
    public Result<List<PlaylistVO>> userCreated(@RequestParam(defaultValue = "20") int limit) {
        return Result.success(playlistService.userCreated(limit));
    }

    /** GET /playlist/all 可见歌单（官方 + 自己的，登录） */
    @GetMapping("/all")
    public Result<List<PlaylistVO>> all(@RequestHeader("X-User-Id") Long userId) {
        return Result.success(playlistService.allVisible(userId));
    }

    /** GET /playlist/my 我的歌单（登录） */
    @GetMapping("/my")
    public Result<List<PlaylistVO>> my(@RequestHeader("X-User-Id") Long userId) {
        return Result.success(playlistService.my(userId));
    }

    /** GET /playlist/{id} 歌单详情（含有序歌曲列表，匿名可看；viewerId 由网关透传，匿名为空） */
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable("id") Long playlistId,
                                              @RequestHeader(value = "X-User-Id", required = false) Long viewerId) {
        playlistService.incrPlay(playlistId);
        return Result.success(playlistService.detail(playlistId, viewerId));
    }

    /** POST /playlist/create 创建歌单（登录），返回新歌单 ID */
    @PostMapping("/create")
    public Result<Long> create(@RequestHeader("X-User-Id") Long userId,
                               @RequestBody PlaylistSaveDTO dto) {
        return Result.success(playlistService.create(userId, dto));
    }

    /** PUT /playlist/{id} 编辑歌单（仅创建者） */
    @PutMapping("/{id}")
    public Result<Void> update(@RequestHeader("X-User-Id") Long userId,
                               @PathVariable("id") Long playlistId,
                               @RequestBody PlaylistSaveDTO dto) {
        playlistService.update(userId, playlistId, dto);
        return Result.success();
    }

    /** DELETE /playlist/{id} 删除歌单（仅创建者，级联清理） */
    @DeleteMapping("/{id}")
    public Result<Void> remove(@RequestHeader("X-User-Id") Long userId,
                               @PathVariable("id") Long playlistId) {
        playlistService.remove(userId, playlistId);
        return Result.success();
    }

    /** POST /playlist/{id}/songs/{songId} 添加歌曲（仅创建者，Feign 校验歌曲存在） */
    @PostMapping("/{id}/songs/{songId}")
    public Result<Void> addSong(@RequestHeader("X-User-Id") Long userId,
                                @PathVariable("id") Long playlistId,
                                @PathVariable("songId") Long songId) {
        playlistService.addSong(userId, playlistId, songId);
        return Result.success();
    }

    /** PUT /playlist/{id}/songs/order 调整歌曲顺序（仅创建者，传全量 songId 列表） */
    @PutMapping("/{id}/songs/order")
    public Result<Void> reorderSongs(@RequestHeader("X-User-Id") Long userId,
                                     @PathVariable("id") Long playlistId,
                                     @RequestBody List<Long> songIds) {
        playlistService.reorderSongs(userId, playlistId, songIds);
        return Result.success();
    }

    /** DELETE /playlist/{id}/songs/{songId} 移除歌曲（仅创建者） */
    @DeleteMapping("/{id}/songs/{songId}")
    public Result<Void> removeSong(@RequestHeader("X-User-Id") Long userId,
                                   @PathVariable("id") Long playlistId,
                                   @PathVariable("songId") Long songId) {
        playlistService.removeSong(userId, playlistId, songId);
        return Result.success();
    }

    // ===== 管理端（/playlist/admin/**：网关角色矩阵限 ADMIN，admin 服务 Feign 转发）=====

    /** GET /playlist/admin/list 管理端歌单列表（官方 + 用户公开） */
    @GetMapping("/admin/list")
    public Result<List<PlaylistVO>> adminList() {
        return Result.success(playlistService.adminList());
    }

    /** GET /playlist/admin/{id}/songs 歌单内歌曲（按节目顺序，复用 detail 的歌曲聚合） */
    @GetMapping("/admin/{id}/songs")
    public Result<List<Map<String, Object>>> adminSongs(@PathVariable("id") Long playlistId) {
        Map<String, Object> detail = playlistService.detail(playlistId, null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> songs = (List<Map<String, Object>>) detail.get("songs");
        return Result.success(songs);
    }

    /** POST /playlist/admin/{id}/songs/{songId} 管理员加歌（官方歌单/电台节目单维护） */
    @PostMapping("/admin/{id}/songs/{songId}")
    public Result<Void> adminAddSong(@PathVariable("id") Long playlistId,
                                     @PathVariable("songId") Long songId) {
        playlistService.adminAddSong(playlistId, songId);
        return Result.success();
    }

    /** DELETE /playlist/admin/{id}/songs/{songId} 管理员删歌 */
    @DeleteMapping("/admin/{id}/songs/{songId}")
    public Result<Void> adminRemoveSong(@PathVariable("id") Long playlistId,
                                        @PathVariable("songId") Long songId) {
        playlistService.adminRemoveSong(playlistId, songId);
        return Result.success();
    }
}
