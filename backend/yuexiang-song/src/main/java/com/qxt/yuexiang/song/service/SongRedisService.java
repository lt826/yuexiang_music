package com.qxt.yuexiang.song.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 歌曲服务 Redis 层：播放计数缓冲、实时热歌榜、热搜词。
 *
 * <p>设计要点：
 * <ul>
 *   <li>播放计数走 Redis INCR 缓冲，定时任务批量回写 MySQL（写合并，消灭每次播放一次 UPDATE）</li>
 *   <li>实时热榜 ZINCRBY；日榜按天分 key + TTL 滚动过期，无需定时清理</li>
 *   <li>所有操作 try-catch 降级：Redis 故障时调用方退回 MySQL 原逻辑，不影响主流程</li>
 * </ul></p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SongRedisService {

    private final StringRedisTemplate redisTemplate;

    /** 播放计数缓冲 key 前缀（Hash：field=songId, value=待回写增量） */
    private static final String PLAY_COUNT_KEY = "song:play:count:buffer";
    /** 实时热歌榜（ZSet：member=songId, score=累计播放） */
    private static final String REALTIME_RANK_KEY = "song:rank:realtime";
    /** 日榜 key 前缀（ZSet，按天分 key，TTL 滚动过期） */
    private static final String DAILY_RANK_PREFIX = "song:rank:daily:";
    /** 热搜词榜（ZSet：member=keyword, score=搜索次数） */
    private static final String SEARCH_HOT_KEY = "song:search:hot";
    /** 用户搜索历史（List：LPUSH + LTRIM 保留最近 N 条） */
    private static final String SEARCH_HISTORY_PREFIX = "song:search:history:";
    private static final long SEARCH_HISTORY_MAX = 10;
    /** 热搜榜保留词条数 */
    private static final long SEARCH_HOT_MAX = 50;
    /** 日榜保留天数（覆盖周报口径，过期自动清理） */
    private static final Duration DAILY_RANK_TTL = Duration.ofDays(8);
    /** 回写任务一次最多搬移的词条数（防止极端热点把单次回写拖死） */
    private static final int FLUSH_BATCH = 200;

    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    // ===== 播放计数缓冲 + 实时/日榜 =====

    /**
     * 播放上报：计数缓冲 +1，同时刷新实时榜与今日榜。
     * Redis 异常仅记日志（上报静默失败，与原 MySQL 版本行为一致）。
     */
    public void recordPlay(Long songId) {
        try {
            String id = String.valueOf(songId);
            redisTemplate.opsForHash().increment(PLAY_COUNT_KEY, id, 1);
            redisTemplate.opsForZSet().incrementScore(REALTIME_RANK_KEY, id, 1);
            String dailyKey = DAILY_RANK_PREFIX + LocalDate.now().format(DAY_FMT);
            redisTemplate.opsForZSet().incrementScore(dailyKey, id, 1);
            redisTemplate.expire(dailyKey, DAILY_RANK_TTL);
        } catch (Exception e) {
            log.warn("播放计数写入 Redis 失败（降级忽略）: songId={}, {}", songId, e.getMessage());
        }
    }

    /**
     * 取实时热歌榜 TopN 的 songId（按播放热度倒序）。
     * 榜空（Redis 重启/新库）返回空列表，调用方应回退 MySQL 播放量排序。
     */
    public List<Long> topPlayedSongIds(int limit) {
        try {
            Set<ZSetOperations.TypedTuple<String>> tuples =
                    redisTemplate.opsForZSet().reverseRangeWithScores(REALTIME_RANK_KEY, 0, limit - 1L);
            if (tuples == null || tuples.isEmpty()) {
                return List.of();
            }
            return tuples.stream().map(t -> Long.valueOf(t.getValue())).toList();
        } catch (Exception e) {
            log.warn("读取实时热榜失败（降级 MySQL）: {}", e.getMessage());
            return List.of();
        }
    }

    /** 取热搜词 TopN（词, 次数），榜空返回空列表 */
    public List<HotKeyword> topSearchKeywords(int limit) {
        try {
            Set<ZSetOperations.TypedTuple<String>> tuples =
                    redisTemplate.opsForZSet().reverseRangeWithScores(SEARCH_HOT_KEY, 0, limit - 1L);
            if (tuples == null || tuples.isEmpty()) {
                return List.of();
            }
            return tuples.stream()
                    .map(t -> new HotKeyword(t.getValue(), t.getScore() == null ? 0 : t.getScore().longValue()))
                    .toList();
        } catch (Exception e) {
            log.warn("读取热搜词失败: {}", e.getMessage());
            return List.of();
        }
    }

    /** 搜索词计入热搜榜 */
    public void recordSearchKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return;
        }
        try {
            redisTemplate.opsForZSet().incrementScore(SEARCH_HOT_KEY, keyword.trim(), 1);
            // 只留热度前 50，防止低频词无限膨胀
            redisTemplate.opsForZSet().removeRange(SEARCH_HOT_KEY, SEARCH_HOT_MAX, -1);
        } catch (Exception e) {
            log.warn("热搜词写入失败（降级忽略）: keyword={}, {}", keyword, e.getMessage());
        }
    }

    /** 记录用户搜索历史：去重后插到队首，只留最近 10 条 */
    public void recordSearchHistory(Long userId, String keyword) {
        if (userId == null || keyword == null || keyword.isBlank()) {
            return;
        }
        String kw = keyword.trim();
        try {
            String key = SEARCH_HISTORY_PREFIX + userId;
            redisTemplate.opsForList().remove(key, 0, kw);
            redisTemplate.opsForList().leftPush(key, kw);
            redisTemplate.opsForList().trim(key, 0, SEARCH_HISTORY_MAX - 1);
        } catch (Exception e) {
            log.warn("搜索历史写入失败（降级忽略）: userId={}, {}", userId, e.getMessage());
        }
    }

    /** 读用户搜索历史（最近在前），无记录返回空列表 */
    public List<String> searchHistory(Long userId) {
        if (userId == null) {
            return List.of();
        }
        try {
            List<String> list = redisTemplate.opsForList().range(SEARCH_HISTORY_PREFIX + userId, 0, -1);
            return list == null ? List.of() : list;
        } catch (Exception e) {
            log.warn("搜索历史读取失败: userId={}, {}", userId, e.getMessage());
            return List.of();
        }
    }

    /** 清空用户搜索历史（搜索页"清空"按钮） */
    public void clearSearchHistory(Long userId) {
        if (userId == null) {
            return;
        }
        try {
            redisTemplate.delete(SEARCH_HISTORY_PREFIX + userId);
        } catch (Exception e) {
            log.warn("搜索历史清空失败: userId={}, {}", userId, e.getMessage());
        }
    }

    /**
     * 把 Redis 里缓冲的播放增量批量回写 MySQL。
     * 由定时任务调用；HGETALL + DEL 非原子，极端情况少量计数可能双写（播放计数允许偏差）。
     *
     * @return 待回写的 (songId → 增量)，空 Map 表示无积压
     */
    public java.util.Map<Long, Long> drainPlayCountBuffer() {
        java.util.Map<Long, Long> result = new java.util.HashMap<>();
        try {
            java.util.Map<Object, Object> entries = redisTemplate.opsForHash().entries(PLAY_COUNT_KEY);
            if (entries.isEmpty()) {
                return result;
            }
            redisTemplate.delete(PLAY_COUNT_KEY);
            int n = 0;
            for (java.util.Map.Entry<Object, Object> e : entries.entrySet()) {
                if (n++ >= FLUSH_BATCH) {
                    break;
                }
                result.put(Long.valueOf(e.getKey().toString()), Long.valueOf(e.getValue().toString()));
            }
        } catch (Exception e) {
            log.warn("读取播放计数缓冲失败: {}", e.getMessage());
        }
        return result;
    }

    /** 热搜词条目（词 + 热度） */
    public record HotKeyword(String keyword, long count) {}

    /** 定时任务健康检查用：实时榜规模 */
    public long realtimeRankSize() {
        try {
            Long size = redisTemplate.opsForZSet().zCard(REALTIME_RANK_KEY);
            return size == null ? 0 : size;
        } catch (Exception e) {
            return 0;
        }
    }
}
