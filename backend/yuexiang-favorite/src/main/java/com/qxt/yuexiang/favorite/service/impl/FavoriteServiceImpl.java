package com.qxt.yuexiang.favorite.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qxt.yuexiang.common.exception.BusinessException;
import com.qxt.yuexiang.common.result.ResultCode;
import com.qxt.yuexiang.favorite.entity.FavoritePlaylist;
import com.qxt.yuexiang.favorite.entity.FavoriteSong;
import com.qxt.yuexiang.favorite.entity.ListenHistory;
import com.qxt.yuexiang.favorite.feign.SongClient;
import com.qxt.yuexiang.favorite.mapper.FavoritePlaylistMapper;
import com.qxt.yuexiang.favorite.mapper.FavoriteSongMapper;
import com.qxt.yuexiang.favorite.mapper.ListenHistoryMapper;
import com.qxt.yuexiang.favorite.service.FavoriteService;
import com.qxt.yuexiang.favorite.vo.FavoriteItemVO;
import com.qxt.yuexiang.favorite.vo.WeeklyReportVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * 收藏与收听历史实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FavoriteServiceImpl implements FavoriteService {

    private final FavoriteSongMapper favoriteSongMapper;
    private final FavoritePlaylistMapper favoritePlaylistMapper;
    private final ListenHistoryMapper listenHistoryMapper;
    private final SongClient songClient;
    private final StringRedisTemplate redisTemplate;

    /** 周报缓存：JSON 序列化用（WeeklyReportVO 含 LocalDateTime 字段） */
    private static final ObjectMapper REPORT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    /** 周报缓存 TTL 基准 10 分钟 + 0~2 分钟随机（防同一时刻集中失效雪崩） */
    private static final Duration REPORT_TTL = Duration.ofMinutes(10);
    private static final long REPORT_TTL_JITTER_MS = 120;

    @Override
    public void addFavorite(Long userId, Long songId) {
        FavoriteSong exist = favoriteSongMapper.selectOne(
                new LambdaQueryWrapper<FavoriteSong>()
                        .eq(FavoriteSong::getUserId, userId)
                        .eq(FavoriteSong::getSongId, songId));
        if (exist != null) {
            throw new BusinessException(ResultCode.REPEAT_OPERATION);
        }
        FavoriteSong favorite = new FavoriteSong();
        favorite.setUserId(userId);
        favorite.setSongId(songId);
        try {
            favoriteSongMapper.insert(favorite);
        } catch (DuplicateKeyException e) {
            // 唯一约束兜底防并发重复收藏
            throw new BusinessException(ResultCode.REPEAT_OPERATION);
        }
    }

    @Override
    public void removeFavorite(Long userId, Long songId) {
        favoriteSongMapper.delete(
                new LambdaQueryWrapper<FavoriteSong>()
                        .eq(FavoriteSong::getUserId, userId)
                        .eq(FavoriteSong::getSongId, songId));
    }

    @Override
    public List<FavoriteItemVO> favoriteList(Long userId) {
        List<FavoriteSong> favorites = favoriteSongMapper.selectList(
                new LambdaQueryWrapper<FavoriteSong>()
                        .eq(FavoriteSong::getUserId, userId)
                        .orderByDesc(FavoriteSong::getCreateTime));
        List<FavoriteItemVO> items = favorites.stream()
                .map(f -> FavoriteItemVO.of(f.getSongId(), f.getCreateTime()))
                .collect(Collectors.toList());
        return fillSongInfo(items);
    }

    @Override
    public List<Long> favoriteSongIdList(Long userId) {
        return favoriteSongMapper.selectList(
                        new LambdaQueryWrapper<FavoriteSong>()
                                .eq(FavoriteSong::getUserId, userId))
                .stream().map(FavoriteSong::getSongId).collect(Collectors.toList());
    }

    @Override
    public void recordHistory(Long userId, Long songId) {
        ListenHistory history = new ListenHistory();
        history.setUserId(userId);
        history.setSongId(songId);
        listenHistoryMapper.insert(history);
    }

    @Override
    public List<FavoriteItemVO> historyList(Long userId, int page, int size) {
        int offset = (page - 1) * size;
        List<ListenHistory> histories = listenHistoryMapper.selectList(
                new LambdaQueryWrapper<ListenHistory>()
                        .eq(ListenHistory::getUserId, userId)
                        .orderByDesc(ListenHistory::getListenTime)
                        .last("LIMIT " + offset + ", " + size));
        List<FavoriteItemVO> items = histories.stream()
                .map(h -> FavoriteItemVO.of(h.getSongId(), h.getListenTime()))
                .collect(Collectors.toList());
        return fillSongInfo(items);
    }

    @Override
    public List<Long> latestHistorySongIds(Long userId, int limit) {
        // 按最近收听倒序取一段后去重（LinkedHashSet 保序），放大取数倍数抵消重复播放
        List<ListenHistory> histories = listenHistoryMapper.selectList(
                new LambdaQueryWrapper<ListenHistory>()
                        .eq(ListenHistory::getUserId, userId)
                        .orderByDesc(ListenHistory::getListenTime)
                        .last("LIMIT " + Math.max(limit, 1) * 5));
        return histories.stream()
                .map(ListenHistory::getSongId)
                .distinct()
                .limit(Math.max(limit, 1))
                .collect(Collectors.toList());
    }

    /** 周报 TOP 榜单长度 */
    private static final int REPORT_TOP_SONGS = 5;
    private static final int REPORT_TOP_SINGERS = 3;

    /**
     * 听歌周报：Redis 缓存包裹的 7 天聚合。
     * 缓存价值在于聚合计算 + Feign 补信息都偏贵，且周内短时间结果基本不变；
     * TTL 加随机抖动防集中失效；缓存命中后用户刚听的歌延迟可见（容忍）。
     */
    @Override
    public WeeklyReportVO weeklyReport(Long userId) {
        String cacheKey = "favorite:report:weekly:" + userId;
        // 1) 查缓存（缓存故障静默降级为直查 DB）
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                WeeklyReportVO vo = REPORT_MAPPER.readValue(cached, WeeklyReportVO.class);
                vo.setFromCache(true);
                return vo;
            }
        } catch (Exception e) {
            log.warn("周报缓存读取失败（降级直查）: userId={}, {}", userId, e.getMessage());
        }

        // 2) 未命中 → 聚合
        WeeklyReportVO vo = buildWeeklyReport(userId);

        // 3) 回填缓存（空结果也缓存，防同一 key 反复穿透打库）
        try {
            String json = REPORT_MAPPER.writeValueAsString(vo);
            long jitterMs = ThreadLocalRandom.current().nextLong(REPORT_TTL_JITTER_MS * 1000);
            redisTemplate.opsForValue().set(cacheKey, json, REPORT_TTL.plusMillis(jitterMs));
        } catch (Exception e) {
            log.warn("周报缓存写入失败: userId={}, {}", userId, e.getMessage());
        }
        return vo;
    }

    /** 周报聚合本体（原逻辑搬移，无缓存） */
    private WeeklyReportVO buildWeeklyReport(Long userId) {
        WeeklyReportVO vo = new WeeklyReportVO();
        LocalDateTime since = LocalDateTime.now().minusDays(7);
        // 走 idx_user_time 索引取最近 7 天原始记录（个人量级小，内存聚合足够）
        List<ListenHistory> histories = listenHistoryMapper.selectList(
                new LambdaQueryWrapper<ListenHistory>()
                        .eq(ListenHistory::getUserId, userId)
                        .ge(ListenHistory::getListenTime, since));
        vo.setPlayCount(histories.size());
        vo.setSongCount(histories.stream().map(ListenHistory::getSongId).distinct().count());
        vo.setActiveDays(histories.stream()
                .map(h -> h.getListenTime().toLocalDate())
                .distinct()
                .count());
        if (histories.isEmpty()) {
            vo.setTopSongs(Collections.emptyList());
            vo.setTopSingers(Collections.emptyList());
            return vo;
        }

        // 按歌曲聚合播放次数，取 TOP5
        Map<Long, Long> songCountMap = histories.stream()
                .collect(Collectors.groupingBy(ListenHistory::getSongId, Collectors.counting()));
        List<Long> topSongIds = songCountMap.entrySet().stream()
                .sorted(Map.Entry.<Long, Long>comparingByValue().reversed())
                .limit(REPORT_TOP_SONGS)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        // 批量补歌曲信息（复用 fillSongInfo 的降级策略：song 服务不可用只留 songId）
        List<FavoriteItemVO> skeletons = topSongIds.stream()
                .map(id -> FavoriteItemVO.of(id, null))
                .collect(Collectors.toList());
        fillSongInfo(skeletons);
        Map<Long, FavoriteItemVO> infoById = skeletons.stream()
                .filter(i -> i.getSongId() != null)
                .collect(Collectors.toMap(FavoriteItemVO::getSongId, i -> i, (a, b) -> a, LinkedHashMap::new));

        List<WeeklyReportVO.TopSong> topSongs = new ArrayList<>();
        for (Long songId : topSongIds) {
            WeeklyReportVO.TopSong ts = new WeeklyReportVO.TopSong();
            ts.setSongId(songId);
            ts.setCount(songCountMap.getOrDefault(songId, 0L));
            FavoriteItemVO info = infoById.get(songId);
            if (info != null) {
                ts.setName(info.getName());
                ts.setSingerName(info.getSingerName());
                ts.setCover(info.getCover());
            }
            topSongs.add(ts);
        }
        vo.setTopSongs(topSongs);

        // 歌手聚合：基于 TOP5 歌曲归属的播放次数（TOP 榜之外的不重复计）
        Map<String, Long> singerCount = new LinkedHashMap<>();
        for (WeeklyReportVO.TopSong ts : topSongs) {
            if (ts.getSingerName() == null || ts.getSingerName().isBlank()) {
                continue;
            }
            singerCount.merge(ts.getSingerName(), ts.getCount(), Long::sum);
        }
        vo.setTopSingers(singerCount.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(REPORT_TOP_SINGERS)
                .map(e -> {
                    WeeklyReportVO.TopSinger tsg = new WeeklyReportVO.TopSinger();
                    tsg.setName(e.getKey());
                    tsg.setCount(e.getValue());
                    return tsg;
                })
                .collect(Collectors.toList()));
        return vo;
    }

    /** 批量聚合歌曲信息；song 服务不可用时降级为只展示 songId */
    private List<FavoriteItemVO> fillSongInfo(List<FavoriteItemVO> items) {
        if (items.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> songIds = items.stream()
                .map(FavoriteItemVO::getSongId)
                .distinct()
                .collect(Collectors.toList());
        try {
            Map<Long, Map<String, Object>> songs = songClient.batch(songIds);
            for (FavoriteItemVO item : items) {
                Map<String, Object> song = songs.get(item.getSongId());
                if (song == null) {
                    continue;
                }
                item.setName((String) song.get("name"));
                item.setCover((String) song.get("cover"));
                item.setUrl((String) song.get("url"));
                item.setStyle((String) song.get("style"));
                Object playCount = song.get("playCount");
                item.setPlayCount(playCount == null ? null : Long.valueOf(playCount.toString()));
                item.setSingerName((String) song.get("singerName"));
            }
        } catch (Exception e) {
            log.warn("聚合歌曲信息失败，降级为仅展示ID: {}", e.getMessage());
        }
        return items;
    }

    // ===== 歌单收藏 =====

    @Override
    public void addFavoritePlaylist(Long userId, Long playlistId) {
        Long exist = favoritePlaylistMapper.selectCount(
                new LambdaQueryWrapper<FavoritePlaylist>()
                        .eq(FavoritePlaylist::getUserId, userId)
                        .eq(FavoritePlaylist::getPlaylistId, playlistId));
        if (exist > 0) {
            throw new BusinessException(ResultCode.REPEAT_OPERATION);
        }
        FavoritePlaylist favorite = new FavoritePlaylist();
        favorite.setUserId(userId);
        favorite.setPlaylistId(playlistId);
        try {
            favoritePlaylistMapper.insert(favorite);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ResultCode.REPEAT_OPERATION);
        }
    }

    @Override
    public void removeFavoritePlaylist(Long userId, Long playlistId) {
        favoritePlaylistMapper.delete(
                new LambdaQueryWrapper<FavoritePlaylist>()
                        .eq(FavoritePlaylist::getUserId, userId)
                        .eq(FavoritePlaylist::getPlaylistId, playlistId));
    }

    @Override
    public List<Long> favoritePlaylistIds(Long userId) {
        return favoritePlaylistMapper.selectList(
                        new LambdaQueryWrapper<FavoritePlaylist>()
                                .eq(FavoritePlaylist::getUserId, userId)
                                .orderByDesc(FavoritePlaylist::getCreateTime))
                .stream().map(FavoritePlaylist::getPlaylistId).collect(Collectors.toList());
    }
}
