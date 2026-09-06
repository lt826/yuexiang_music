package com.qxt.yuexiang.playlist.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 歌单信息
 */
@Data
public class PlaylistVO {

    private Long id;

    private Long userId;

    private String title;

    private String cover;

    private String description;

    private String tag;

    private Integer isOfficial;

    /** 是否投稿到歌单广场 */
    private Integer published;

    private Long playCount;

    /** 歌单内歌曲数（聚合） */
    private Integer songCount;

    /** 是否已被当前用户收藏（详情/列表场景，未登录为 false） */
    private Boolean favored;

    private LocalDateTime createTime;
}
