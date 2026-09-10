package com.qxt.yuexiang.song.vo;

import lombok.Data;

/**
 * 歌曲列表项（首页榜单/推荐用）
 */
@Data
public class SongVO {

    private Long id;

    private String name;

    private String cover;

    private String url;

    /** LRC 歌词（播放接口下发，列表接口为空） */
    private String lyric;

    private String style;

    private Long playCount;

    /** 音频时长（秒；0=未知） */
    private Integer duration;

    private Long singerId;

    /** 审核状态 0待审核 1已发布 2已下架（列表场景下发） */
    private Integer status;

    /** 审核驳回原因（status 相关场景下发） */
    private String rejectReason;

    /** 歌手名（song 服务聚合 singer 服务数据） */
    private String singerName;
}
