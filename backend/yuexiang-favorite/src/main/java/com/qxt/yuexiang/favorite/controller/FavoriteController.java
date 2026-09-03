package com.qxt.yuexiang.favorite.controller;

import com.qxt.yuexiang.common.result.Result;
import com.qxt.yuexiang.favorite.service.FavoriteService;
import com.qxt.yuexiang.favorite.vo.FavoriteItemVO;
import com.qxt.yuexiang.favorite.vo.WeeklyReportVO;
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
 * 收藏与收听历史接口（FUN-05/FUN-06，身份经网关透传）
 */
@RestController
@RequestMapping("/favorite")
@RequiredArgsConstructor
public class FavoriteController {

    private final FavoriteService favoriteService;

    /** POST /favorite/song/{songId} 收藏歌曲 */
    @PostMapping("/song/{songId}")
    public Result<Void> addFavorite(@PathVariable("songId") Long songId,
                                    @RequestHeader("X-User-Id") Long userId) {
        favoriteService.addFavorite(userId, songId);
        return Result.success();
    }

    /** DELETE /favorite/song/{songId} 取消收藏 */
    @DeleteMapping("/song/{songId}")
    public Result<Void> removeFavorite(@PathVariable("songId") Long songId,
                                       @RequestHeader("X-User-Id") Long userId) {
        favoriteService.removeFavorite(userId, songId);
        return Result.success();
    }

    /** GET /favorite/song/list 我的收藏列表 */
    @GetMapping("/song/list")
    public Result<List<FavoriteItemVO>> favoriteList(@RequestHeader("X-User-Id") Long userId) {
        return Result.success(favoriteService.favoriteList(userId));
    }

    /** GET /favorite/song/ids 我收藏的歌曲ID集合（前端标注收藏态） */
    @GetMapping("/song/ids")
    public Result<List<Long>> favoriteSongIds(@RequestHeader("X-User-Id") Long userId) {
        return Result.success(favoriteService.favoriteSongIdList(userId));
    }

    /** POST /favorite/history/{songId} 记录收听历史（播放时触发） */
    @PostMapping("/history/{songId}")
    public Result<Void> recordHistory(@PathVariable("songId") Long songId,
                                      @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        // 未登录不记录（FUN-06：登录后可同步收听记录）
        if (userId != null) {
            favoriteService.recordHistory(userId, songId);
        }
        return Result.success();
    }

    /** GET /favorite/history/list 收听历史分页 */
    @GetMapping("/history/list")
    public Result<Map<String, Object>> historyList(@RequestHeader("X-User-Id") Long userId,
                                                   @RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        List<FavoriteItemVO> items = favoriteService.historyList(userId, page, size);
        return Result.success(Map.of("items", items, "page", page, "size", size));
    }

    // ===== 歌单收藏（FUN-05/FUN-07） =====

    /** POST /favorite/playlist/{playlistId} 收藏歌单 */
    @PostMapping("/playlist/{playlistId}")
    public Result<Void> addFavoritePlaylist(@PathVariable("playlistId") Long playlistId,
                                            @RequestHeader("X-User-Id") Long userId) {
        favoriteService.addFavoritePlaylist(userId, playlistId);
        return Result.success();
    }

    /** DELETE /favorite/playlist/{playlistId} 取消收藏歌单 */
    @DeleteMapping("/playlist/{playlistId}")
    public Result<Void> removeFavoritePlaylist(@PathVariable("playlistId") Long playlistId,
                                               @RequestHeader("X-User-Id") Long userId) {
        favoriteService.removeFavoritePlaylist(userId, playlistId);
        return Result.success();
    }

    /** GET /favorite/playlist/list 我收藏的歌单ID列表（前端跳转歌单详情用） */
    @GetMapping("/playlist/list")
    public Result<List<Long>> favoritePlaylistList(@RequestHeader("X-User-Id") Long userId) {
        return Result.success(favoriteService.favoritePlaylistIds(userId));
    }

    /** GET /favorite/playlist/ids 我收藏的歌单ID（内部接口：playlist 服务标注收藏态；裸 List） */
    @GetMapping("/playlist/ids")
    public List<Long> favoritePlaylistIds(@RequestParam("userId") Long userId) {
        return favoriteService.favoritePlaylistIds(userId);
    }

    /** GET /favorite/report/weekly 听歌周报：最近 7 天播放统计 */
    @GetMapping("/report/weekly")
    public Result<WeeklyReportVO> weeklyReport(@RequestHeader("X-User-Id") Long userId) {
        return Result.success(favoriteService.weeklyReport(userId));
    }

    /** GET /favorite/history/latest-song-ids 最近收听的歌曲ID（内部接口：song 服务猜你喜欢用；裸 List）
     *  按最近收听倒序去重，最多 limit 个 */
    @GetMapping("/history/latest-song-ids")
    public List<Long> latestHistorySongIds(@RequestParam("userId") Long userId,
                                           @RequestParam(defaultValue = "10") int limit) {
        return favoriteService.latestHistorySongIds(userId, limit);
    }

    /** GET /favorite/song/ids-by-user 用户收藏的歌曲ID（内部接口：song 服务推荐画像收藏信号用；裸 List） */
    @GetMapping("/song/ids-by-user")
    public List<Long> favoriteSongIdsByUser(@RequestParam("userId") Long userId) {
        return favoriteService.favoriteSongIdList(userId);
    }
}
