package com.qxt.yuexiang.song.service;

import com.qxt.yuexiang.song.dto.SongPublishDTO;
import com.qxt.yuexiang.song.vo.SearchResultVO;
import com.qxt.yuexiang.song.vo.SongVO;

import java.util.List;
import java.util.Map;

/**
 * 歌曲服务接口
 */
public interface SongService {

    /** 热门榜单：已发布歌曲按 play_count 倒序（对齐详细设计 6.3 GET /music/chart?type=hot） */
    List<SongVO> hotChart(int limit);

    /** 新歌首发：已发布歌曲按上架时间倒序（首页"新歌首发"模块） */
    List<SongVO> newSongs(int limit);

    /** 推荐歌曲：登录用户按偏好、未登录走热门兜底（详细设计 6.2 三步推荐） */
    List<SongVO> recommend(Long userId, int limit);

    /** 猜你喜欢：按收听历史逐首歌手的其它歌推荐，耗尽后随机兜底（首页轮播图下板块） */
    List<SongVO> guessYouLike(Long userId, int limit);

    /** 搜索歌曲：FULLTEXT ngram 主路，无结果时 LIKE 兜底（FUN-03）；登录用户计入热搜与历史 */
    SearchResultVO search(String keyword, Long userId, int page, int size);

    /** 播放：下发歌曲详情（含歌词），按状态拦截（详细设计 6.1 状态机）；
     *  归属歌手本人（userId 命中歌曲 singerId 的关联用户）可试听任意状态的自己的作品 */
    SongVO play(Long songId, Long userId);

    /** 播放上报：发 MQ 播放事件（异步写计数与历史；播完才计口径由前端触发） */
    void reportPlay(Long songId, Long userId);

    /** 实时热歌榜：Redis ZSet 维护的播放热度榜，榜空回退 MySQL 播放量排序 */
    List<SongVO> realtimeChart(int limit);

    /** 热搜词 TopN（Redis ZSet 维护） */
    List<SongRedisService.HotKeyword> hotKeywords(int limit);

    /** 记录用户搜索历史（去重、最近在前、最多 10 条） */
    void recordSearchHistory(Long userId, String keyword);

    /** 读取用户搜索历史 */
    List<String> searchHistory(Long userId);

    /** 清空用户搜索历史 */
    void clearSearchHistory(Long userId);

    /** 把 Redis 缓冲的播放计数批量回写 MySQL（定时任务入口） */
    void flushPlayCount();

    /** 批量查询：供收藏/历史服务聚合歌曲信息，返回 Map<songId, SongVO> */
    Map<Long, SongVO> batchByIds(List<Long> songIds);

    // ===== 歌手作品管理（FUN-08/09） =====

    /** 发布歌曲：校验歌手身份，入审核队列（status=0，对齐详细设计 6.1 默认审核制） */
    void publish(Long userId, SongPublishDTO dto);

    /** 我的歌曲列表（含各状态与播放数据） */
    List<SongVO> mySongs(Long userId);

    /** 修改歌曲信息（仅本人；已发布/已下架均可改，待审核不可改） */
    void updateSong(Long userId, Long songId, SongPublishDTO dto);

    /** 重新提交上架审核（仅本人且已下架，status→0） */
    void resubmit(Long userId, Long songId);

    /** 下架歌曲（仅本人，status→2） */
    void offline(Long userId, Long songId);

    /** 待审核列表（admin 聚合用），聚合歌手名 */
    List<SongVO> pendingList();

    /** 管理端全量歌曲列表：含待审/已发布/已下架，分页+关键字，聚合歌手名 */
    Map<String, Object> adminList(String keyword, int page, int size);

    /** 管理员强制下架：绕过归属校验，须附原因（通知歌手） */
    void adminOffline(Long songId, String reason);

    /** 歌曲审核：通过 status→1，驳回记原因（管理员） */
    void audit(Long songId, boolean approve, String reason);

    /** 按歌手查已发布歌曲（歌手主页用） */
    List<SongVO> bySinger(Long singerId);

    /** 歌手已发布歌曲ID列表（favorite 服务粉丝通知用） */
    List<Long> publishedIdsBySinger(Long singerId);

    /** 热门歌手ID（按已发布歌曲总播放量倒序，singer 服务推荐排序用） */
    List<Long> hotSingerIds(int limit);

    /** 平台统计：歌曲总数/已发布数/总播放量（门户首页展示，admin 服务 Feign 聚合用） */
    Map<String, Long> stats();

    /**
     * 批量回填歌曲时长（秒）。来源：file 服务上传时解析的 duration（管理端一键回填历史数据用）。
     * @param durationBySongId songId → 时长秒数
     * @return 实际更新的歌曲数
     */
    int backfillDurations(Map<Long, Integer> durationBySongId);
}
