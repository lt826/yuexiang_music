package com.qxt.yuexiang.ai.vo;

import lombok.Data;

/**
 * 歌曲结果项（AI 服务侧轻量 SongVO，字段从 song/singer Feign 响应中提取）
 */
@Data
public class AiSongVO {

    private Long id;

    private String name;

    /** 歌手名 */
    private String singerName;

    private Long singerId;

    private String style;

    private Long playCount;
}
