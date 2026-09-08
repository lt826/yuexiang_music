package com.qxt.yuexiang.singer.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 我的关注列表项：歌手简要资料 + 关注时间
 */
@Data
public class SingerFollowVO {

    private Long singerId;

    private String name;

    private String avatar;

    private String introduction;

    /** 关注时间 */
    private LocalDateTime followTime;
}
