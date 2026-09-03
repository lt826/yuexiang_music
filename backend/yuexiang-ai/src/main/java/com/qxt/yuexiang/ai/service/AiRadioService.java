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
 * 误差控制：单曲用模拟时长（SEGMENT_SECONDS），与真实时长有出入；
 * 客户端每首歌播完不本地续播，而是重新调 tune 对时，误差不跨首累积。
 */
@Slf4j
@Service
public class AiRadioService {

    /** 模拟单曲时长（秒）：节目单时间轴的最小刻度 */
    private static final int SEGMENT_SECONDS = 240;

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

    public AiRadioService(PlaylistClient playlistClient) {
        this.playlistClient = playlistClient;
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

        // 时间轴：epoch = 当天零点 → 循环总时长取模 → 定位第几首第几秒
        // （elapsed 毫秒，total 乘 1000 对齐单位）
        long epoch = LocalDate.now(ZoneId.of("Asia/Shanghai"))
                .atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli();
        long elapsed = System.currentTimeMillis() - epoch;
        long total = (long) songs.size() * SEGMENT_SECONDS * 1000L;
        long offset = elapsed % total;

        RadioTuneVO vo = new RadioTuneVO();
        vo.setChannel(channel);
        vo.setFreq(meta.freq());
        vo.setPlaylistId(meta.playlistId());
        vo.setPlaylistTitle("悦享电台 · " + channelLabel(channel) + " FM " + meta.freq());
        vo.setSongs(songs);
        vo.setCurrentIndex((int) (offset / (SEGMENT_SECONDS * 1000L)));
        vo.setOffsetSeconds((int) ((offset % (SEGMENT_SECONDS * 1000L)) / 1000));
        vo.setSegmentSeconds(SEGMENT_SECONDS);
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
