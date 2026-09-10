package com.qxt.yuexiang.circle.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 帖子 VO：发帖人信息聚合 + 歌曲卡片聚合
 */
@Data
public class PostVO {

    private Long id;

    private Long circleId;

    private Long userId;

    private String nickname;

    private String avatar;

    private String content;

    /** 插入的歌曲ID（可空） */
    private Long songId;

    /** 歌曲卡片信息（song 服务 batch 聚合：name/singerName/cover 等） */
    private Map<String, Object> song;

    /** 帖子图片URL（可空，最多1张） */
    private String image;

    /** 置顶 0普通 1置顶 */
    private Integer pinned;

    private Integer likeCount;

    private Integer commentCount;

    /** 当前用户是否已点赞 */
    private Boolean liked;

    /** 是否可删（发帖人本人/圈主/圈管理员/平台管理员） */
    private Boolean canDelete;

    /** 是否可置顶（圈主/圈管理员/平台管理员） */
    private Boolean canPin;

    private LocalDateTime createTime;
}
