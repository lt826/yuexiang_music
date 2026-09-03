package com.qxt.yuexiang.favorite.service;

import com.qxt.yuexiang.favorite.vo.FavoriteItemVO;
import com.qxt.yuexiang.favorite.vo.WeeklyReportVO;

import java.util.List;

/**
 * 收藏与收听历史服务
 */
public interface FavoriteService {

    /** 收藏歌曲：唯一键 (user_id, song_id) 防重（FUN-05） */
    void addFavorite(Long userId, Long songId);

    /** 取消收藏 */
    void removeFavorite(Long userId, Long songId);

    /** 我的收藏列表，按收藏时间倒序 */
    List<FavoriteItemVO> favoriteList(Long userId);

    /** 我收藏的歌曲ID集合（前端标注收藏态） */
    List<Long> favoriteSongIdList(Long userId);

    /** 记录收听历史（FUN-06，播放时前端触发） */
    void recordHistory(Long userId, Long songId);

    /** 收听历史分页查询，按时间倒序 */
    List<FavoriteItemVO> historyList(Long userId, int page, int size);

    /** 最近收听的歌曲ID（按时间倒序去重，猜你喜欢画像用） */
    List<Long> latestHistorySongIds(Long userId, int limit);

    /** 听歌周报：最近 7 天播放统计 */
    WeeklyReportVO weeklyReport(Long userId);

    // ===== 歌单收藏（FUN-05/FUN-07） =====

    /** 收藏歌单（唯一键防重） */
    void addFavoritePlaylist(Long userId, Long playlistId);

    /** 取消收藏歌单 */
    void removeFavoritePlaylist(Long userId, Long playlistId);

    /** 我收藏的歌单ID列表 */
    List<Long> favoritePlaylistIds(Long userId);
}
