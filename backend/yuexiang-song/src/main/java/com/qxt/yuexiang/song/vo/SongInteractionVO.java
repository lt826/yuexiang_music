package com.qxt.yuexiang.song.vo;

import lombok.Data;

/**
 * 歌曲互动聚合：点赞数/当前用户是否已赞/评论数（播放页顶栏展示）
 */
@Data
public class SongInteractionVO {

    /** 点赞总数 */
    private Long likeCount;

    /** 当前登录用户是否已点赞（匿名恒 false） */
    private Boolean liked;

    /** 评论总数（不含已删除） */
    private Long commentCount;
}
