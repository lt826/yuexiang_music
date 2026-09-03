package com.qxt.yuexiang.song.controller;

import com.qxt.yuexiang.common.result.Result;
import com.qxt.yuexiang.song.dto.SongPublishDTO;
import com.qxt.yuexiang.song.service.SongRedisService;
import com.qxt.yuexiang.song.service.SongService;
import com.qxt.yuexiang.song.vo.SearchResultVO;
import com.qxt.yuexiang.song.vo.SongVO;
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
 * 歌曲接口：榜单、推荐、搜索、播放、批量查询
 */
@RestController
@RequestMapping("/song")
@RequiredArgsConstructor
public class SongController {

    private final SongService songService;

    /** GET /song/chart 全站热门榜（type 预留 hot/rise） */
    @GetMapping("/chart")
    public Result<List<SongVO>> chart(@RequestParam(defaultValue = "hot") String type,
                                      @RequestParam(defaultValue = "10") int limit) {
        return Result.success(songService.hotChart(limit));
    }

    /** GET /song/chart/realtime 实时热歌榜：Redis ZSet 播放热度实时排序 */
    @GetMapping("/chart/realtime")
    public Result<List<SongVO>> realtimeChart(@RequestParam(defaultValue = "10") int limit) {
        return Result.success(songService.realtimeChart(limit));
    }

    /** GET /song/search/hot 热搜词 TopN（"大家都在搜"） */
    @GetMapping("/search/hot")
    public Result<List<SongRedisService.HotKeyword>> hotKeywords(
            @RequestParam(defaultValue = "10") int limit) {
        return Result.success(songService.hotKeywords(limit));
    }

    /** GET /song/new 新歌首发：按上架时间倒序（首页轮播下第一模块） */
    @GetMapping("/new")
    public Result<List<SongVO>> newSongs(@RequestParam(defaultValue = "10") int limit) {
        return Result.success(songService.newSongs(limit));
    }

    /** GET /song/recommend 个性化推荐：风格标签画像 + 加权混采，未登录走热门兜底 */
    @GetMapping("/recommend")
    public Result<List<SongVO>> recommend(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestParam(defaultValue = "10") int limit) {
        return Result.success(songService.recommend(userId, limit));
    }

    /** GET /song/guess 猜你喜欢：登录用户按收听历史歌手画像推荐，未登录/无历史随机兜底 */
    @GetMapping("/guess")
    public Result<List<SongVO>> guess(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestParam(defaultValue = "10") int limit) {
        return Result.success(songService.guessYouLike(userId, limit));
    }

    /** GET /song/search 关键字搜索（FUN-03），分页；登录用户计入热搜榜与搜索历史 */
    @GetMapping("/search")
    public Result<SearchResultVO> search(@RequestParam("keyword") String keyword,
                                         @RequestHeader(value = "X-User-Id", required = false) Long userId,
                                         @RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return Result.success(songService.search(keyword, userId, page, size));
    }

    /** GET /song/search/history 我的搜索历史（最近在前，最多 10 条） */
    @GetMapping("/search/history")
    public Result<List<String>> searchHistory(
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return Result.success(songService.searchHistory(userId));
    }

    /** DELETE /song/search/history 清空我的搜索历史 */
    @DeleteMapping("/search/history")
    public Result<Void> clearSearchHistory(
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        songService.clearSearchHistory(userId);
        return Result.success();
    }

    /** GET /song/play/{id} 播放：下发详情与歌词，状态拦截（详细设计 12.2 核心契约）；
     *  例外：归属歌手本人可试听任意状态的自己的作品（歌手中心预览） */
    @GetMapping("/play/{id}")
    public Result<SongVO> play(@PathVariable("id") Long songId,
                               @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return Result.success(songService.play(songId, userId));
    }

    /** POST /song/play/{id}/report 播放上报（发 MQ 事件异步落计数与历史） */
    @PostMapping("/play/{id}/report")
    public Result<Void> reportPlay(@PathVariable("id") Long songId,
                                   @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        songService.reportPlay(songId, userId);
        return Result.success();
    }

    /** POST /song/batch 批量查歌曲（收藏/历史服务聚合用）
     *  内部接口直接返回裸 Map，不包 Result（对齐 singer/names 的内部约定，方便 Feign 调用方解码） */
    @PostMapping("/batch")
    public Map<Long, SongVO> batch(@RequestBody List<Long> songIds) {
        return songService.batchByIds(songIds);
    }

    // ===== 歌手作品管理（FUN-08/09） =====

    /** POST /song/publish 发布歌曲（音频先传 file 服务，此处收 URL；歌手角色） */
    @PostMapping("/publish")
    public Result<Void> publish(@RequestHeader("X-User-Id") Long userId,
                                @RequestBody SongPublishDTO dto) {
        songService.publish(userId, dto);
        return Result.success();
    }

    /** GET /song/my 我的歌曲列表（歌手角色） */
    @GetMapping("/my")
    public Result<List<SongVO>> mySongs(@RequestHeader("X-User-Id") Long userId) {
        return Result.success(songService.mySongs(userId));
    }

    /** PUT /song/{id} 修改歌曲信息（仅本人） */
    @PutMapping("/{id}")
    public Result<Void> update(@RequestHeader("X-User-Id") Long userId,
                               @PathVariable("id") Long songId,
                               @RequestBody SongPublishDTO dto) {
        songService.updateSong(userId, songId, dto);
        return Result.success();
    }

    /** POST /song/{id}/offline 下架（仅本人） */
    @PostMapping("/{id}/offline")
    public Result<Void> offline(@RequestHeader("X-User-Id") Long userId,
                                @PathVariable("id") Long songId) {
        songService.offline(userId, songId);
        return Result.success();
    }

    /** POST /song/{id}/resubmit 已下架歌曲修改后重新送审（仅本人） */
    @PostMapping("/{id}/resubmit")
    public Result<Void> resubmit(@RequestHeader("X-User-Id") Long userId,
                                 @PathVariable("id") Long songId) {
        songService.resubmit(userId, songId);
        return Result.success();
    }

    /** GET /song/pending 待审核列表（admin 聚合用） */
    @GetMapping("/pending")
    public Result<List<SongVO>> pending() {
        return Result.success(songService.pendingList());
    }

    /** GET /song/admin/list 管理端全量歌曲（含待审/下架；admin 服务转发，网关限 ADMIN） */
    @GetMapping("/admin/list")
    public Result<Map<String, Object>> adminList(@RequestParam(required = false) String keyword,
                                                 @RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "10") int size) {
        return Result.success(songService.adminList(keyword, page, size));
    }

    /** POST /song/admin/offline 管理员强制下架（admin 服务转发，网关限 ADMIN） */
    @PostMapping("/admin/offline")
    public Result<Void> adminOffline(@RequestParam Long songId,
                                     @RequestParam(required = false) String reason) {
        songService.adminOffline(songId, reason);
        return Result.success();
    }

    /** POST /song/audit/{id} 审核（管理员） */
    @PostMapping("/audit/{id}")
    public Result<Void> audit(@PathVariable("id") Long songId,
                              @RequestParam boolean approve,
                              @RequestParam(required = false) String reason) {
        songService.audit(songId, approve, reason);
        return Result.success();
    }

    /** GET /song/by-singer/{id} 歌手主页歌曲（匿名可看） */
    @GetMapping("/by-singer/{id}")
    public Result<List<SongVO>> bySinger(@PathVariable("id") Long singerId) {
        return Result.success(songService.bySinger(singerId));
    }

    /** GET /song/ids-by-singer/{id} 歌手已发布歌曲ID（内部接口返回裸 List，粉丝通知用） */
    @GetMapping("/ids-by-singer/{id}")
    public List<Long> idsBySinger(@PathVariable("id") Long singerId) {
        return songService.publishedIdsBySinger(singerId);
    }

    /** GET /song/hot-singers 热门歌手ID（按已发布歌曲总播放量倒序；singer 服务聚合用）
     *  内部接口返回裸 List，不包 Result（对齐 batch 的内部约定） */
    @GetMapping("/hot-singers")
    public List<Long> hotSingers(@RequestParam(defaultValue = "10") int limit) {
        return songService.hotSingerIds(limit);
    }

    /** GET /song/stats 平台歌曲统计（admin 服务聚合门户展示用；内部接口返回裸 Map） */
    @GetMapping("/stats")
    public Map<String, Long> stats() {
        return songService.stats();
    }
}
