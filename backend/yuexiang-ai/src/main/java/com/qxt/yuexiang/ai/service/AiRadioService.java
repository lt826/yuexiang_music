package com.qxt.yuexiang.ai.service;

import com.qxt.yuexiang.ai.feign.PlaylistClient;
import com.qxt.yuexiang.ai.vo.AiSongVO;
import com.qxt.yuexiang.ai.vo.RadioTuneVO;
import com.qxt.yuexiang.common.exception.BusinessException;
import com.qxt.yuexiang.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 心情电台：共享时间轴调频。
 *
 * 播放进度 = 服务器时间的纯函数——
 *   epoch 取"当天零点"（Asia/Shanghai），elapsed 对节目单总时长取模，
 *   得到当前第几首 + 第几秒。无任何运行时状态：服务重启无感、多实例天然一致，
 *   N 个用户并发调频拿到完全相同的位置，"B 从 A 听到的地方继续"自然成立。
 *
 * 节目单是固定官方歌单（频道 key → 歌单 ID 映射见 CHANNEL_PLAYLISTS，
 * 由 SQL 种子一次性固化，AI 策展的产物），tune 只读不算 AI，秒级响应。
 *
 * 误差控制：时间轴按每首歌的真实时长（song 表 duration，上传时由 file 服务解析）排列；
 * 个别歌时长未知时按兜底 240s 排轴。客户端每首歌播完不本地续播，
 * 而是重新调 tune 对时——时长真实后，seek 落点必在歌的中途。
 */
@Slf4j
@Service
public class AiRadioService {

    /** 真实时长不可得时的兜底模拟时长（秒）：时间轴不能因个别歌缺时长而中断 */
    private static final int FALLBACK_SEGMENT_SECONDS = 240;

    /** 短于该时长的歌视为解析失败（防止 5 秒残曲把时间轴切碎），按兜底时长排轴 */
    private static final int MIN_VALID_DURATION = 30;

    /** 频道元数据（与前端 CHANNELS 严格对齐）：key → 官方歌单 ID */
    private static final Map<String, ChannelMeta> CHANNEL_PLAYLISTS = Map.ofEntries(
            Map.entry("sleep",   new ChannelMeta("88.4", 26L)),
            Map.entry("study",   new ChannelMeta("91.7", 27L)),
            Map.entry("commute", new ChannelMeta("94.0", 28L)),
            Map.entry("sport",   new ChannelMeta("97.5", 29L)),
            Map.entry("emo",     new ChannelMeta("101.3", 30L)),
            Map.entry("happy",   new ChannelMeta("103.6", 31L)),
            Map.entry("rain",    new ChannelMeta("106.2", 32L)),
            Map.entry("coffee",  new ChannelMeta("108.9", 33L)));

    private record ChannelMeta(String freq, Long playlistId) {}

    private final PlaylistClient playlistClient;
    private final com.qxt.yuexiang.ai.feign.SongClient songClient;

    public AiRadioService(PlaylistClient playlistClient, com.qxt.yuexiang.ai.feign.SongClient songClient) {
        this.playlistClient = playlistClient;
        this.songClient = songClient;
    }

    /** 拉节目单歌曲的真实时长（song 服务 batch）；Feign 失败返回空 Map（全部走兜底时长，行为等同旧版） */
    private Map<Long, Integer> loadDurations(List<AiSongVO> songs) {
        try {
            Map<Long, Map<String, Object>> batch = songClient.batch(songs.stream().map(AiSongVO::getId).toList());
            Map<Long, Integer> out = new java.util.HashMap<>();
            for (AiSongVO s : songs) {
                Map<String, Object> raw = batch.get(s.getId());
                if (raw != null && raw.get("duration") != null) {
                    out.put(s.getId(), Integer.parseInt(String.valueOf(raw.get("duration"))));
                }
            }
            return out;
        } catch (Exception e) {
            log.warn("电台时长批量查询失败，全部按兜底时长排轴: {}", e.getMessage());
            return Map.of();
        }
    }

    /**
     * 调频：返回频道节目单与当前应播位置。
     * 匿名可调（位置是公共信息）；真正起播时由前端播放器的登录拦截兜底。
     */
    public RadioTuneVO tune(String channel) {
        ChannelMeta meta = CHANNEL_PLAYLISTS.get(channel);
        if (meta == null) {
            throw new BusinessException(400, "没有这个频道，请从频道列表中选择");
        }

        List<AiSongVO> songs = loadPlaylistSongs(meta.playlistId());
        if (songs.isEmpty()) {
            throw new BusinessException(404, "该频道节目单暂时为空，请稍后再试");
        }

        // 每首歌的排轴时长：song 服务下发的真实 duration；缺失/异常按兜底时长
        Map<Long, Integer> durations = loadDurations(songs);
        List<Integer> segments = songs.stream()
                .map(s -> {
                    int d = durations.getOrDefault(s.getId(), 0);
                    return d >= MIN_VALID_DURATION ? d : FALLBACK_SEGMENT_SECONDS;
                })
                .collect(java.util.stream.Collectors.toList());

        // 时间轴：epoch = 当天零点 → 累计真实时长循环取模 → 定位第几首第几秒。
        // 时长与真实一致后，seek 落点必在歌的中途而非结尾，"播完卡几十秒"的空窗消失。
        long epoch = LocalDate.now(ZoneId.of("Asia/Shanghai"))
                .atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();
        long elapsed = System.currentTimeMillis() - epoch;
        long totalMillis = 0;
        for (Integer seg : segments) {
            totalMillis += seg * 1000L;
        }
        long offset = elapsed % totalMillis;

        int index = 0;
        while (index < segments.size() - 1 && offset >= segments.get(index) * 1000L) {
            offset -= segments.get(index) * 1000L;
            index++;
        }

        RadioTuneVO vo = new RadioTuneVO();
        vo.setChannel(channel);
        vo.setFreq(meta.freq());
        vo.setPlaylistId(meta.playlistId());
        vo.setPlaylistTitle("悦享电台 · " + channelLabel(channel) + " FM " + meta.freq());
        vo.setSongs(songs);
        vo.setCurrentIndex(index);
        vo.setOffsetSeconds((int) (offset / 1000));
        vo.setSegmentSeconds(segments.get(index));
        return vo;
    }

    /** 读官方节目单（playlist 服务按 sort 保序聚合歌曲信息），过滤已下架/审核中的歌 */
    @SuppressWarnings("unchecked")
    private List<AiSongVO> loadPlaylistSongs(Long playlistId) {
        Result<Map<String, Object>> resp;
        try {
            resp = playlistClient.detail(playlistId);
        } catch (Exception e) {
            log.warn("电台节目单读取失败: playlistId={}, {}", playlistId, e.getMessage());
            throw new BusinessException(503, "电台节目单暂时取不到，请稍后再试");
        }
        Object songs = resp == null || resp.getData() == null ? null : resp.getData().get("songs");
        List<AiSongVO> out = new ArrayList<>();
        if (songs instanceof List<?> rawList) {
            for (Object o : rawList) {
                if (!(o instanceof Map<?, ?> raw)) {
                    continue;
                }
                AiSongVO vo = new AiSongVO();
                try {
                    vo.setId(Long.valueOf(String.valueOf(((Map<String, Object>) raw).get("id"))));
                    Object name = ((Map<String, Object>) raw).get("name");
                    if (name == null || String.valueOf(name).isBlank()) {
                        continue;
                    }
                    vo.setName(String.valueOf(name));
                    Object singerId = ((Map<String, Object>) raw).get("singerId");
                    vo.setSingerId(singerId == null ? null : Long.valueOf(String.valueOf(singerId)));
                    vo.setSingerName((String) ((Map<String, Object>) raw).get("singerName"));
                    vo.setStyle((String) ((Map<String, Object>) raw).get("style"));
                    Object pc = ((Map<String, Object>) raw).get("playCount");
                    vo.setPlayCount(pc == null ? 0L : Long.parseLong(String.valueOf(pc)));
                    out.add(vo);
                } catch (Exception ignored) {
                    // 脏数据项跳过，不进节目单
                }
            }
        }
        return out;
    }

    private static String channelLabel(String key) {
        return switch (key) {
            case "sleep" -> "睡前";
            case "study" -> "学习";
            case "commute" -> "通勤";
            case "sport" -> "运动";
            case "emo" -> "emo";
            case "happy" -> "开心";
            case "rain" -> "雨天";
            case "coffee" -> "咖啡";
            default -> key;
        };
    }
}
