package com.qxt.yuexiang.singer.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 歌手主页聚合信息：基本资料 + 作品数/粉丝数 + 当前用户关注态
 */
@Data
public class SingerHomeVO {

    private Long id;

    private Long userId;

    private String name;

    private String avatar;

    private String introduction;

    /** 已发布歌曲数 */
    private Long songCount;

    /** 总播放量（已发布歌曲 play_count 合计） */
    private Long totalPlayCount;

    /** 粉丝数 */
    private Long followerCount;

    /** 当前登录用户是否已关注（未登录恒 false） */
    private Boolean followed;
}
