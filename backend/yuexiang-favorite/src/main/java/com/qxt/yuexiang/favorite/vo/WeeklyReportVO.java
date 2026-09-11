package com.qxt.yuexiang.favorite.vo;

import lombok.Data;

import java.util.List;

/**
 * 听歌周报：最近 7 天收听统计（数据口径：listen_history 播放次数，非时长）
 */
@Data
public class WeeklyReportVO {

    /** 播放总次数（每次起播记一条） */
    private long playCount;

    /** 听过的不同歌曲数 */
    private long songCount;

    /** 本周有收听行为的天数 */
    private long activeDays;

    /** 听得最多的歌曲 TOP5（含歌名/歌手/封面） */
    private List<TopSong> topSongs;

    /** 最爱歌手 TOP3（按 topSongs 对应歌手的播放次数聚合） */
    private List<TopSinger> topSingers;

    /** 本次结果是否来自 Redis 缓存（演示/调试用，缓存命中时前端可提示） */
    private transient boolean fromCache;

    @Data
    public static class TopSong {
        private Long songId;
        private String name;
        private String singerName;
        private String cover;
        /** 本周播放次数 */
        private long count;
    }

    @Data
    public static class TopSinger {
        private String name;
        private long count;
    }
}
