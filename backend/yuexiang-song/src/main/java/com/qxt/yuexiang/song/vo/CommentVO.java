package com.qxt.yuexiang.song.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评论条目（已聚合评论人昵称/头像）：
 * 根评论携带回复预览（最新 2 条）与回复总数；回复条目携带被回复人昵称
 */
@Data
public class CommentVO {

    private Long id;

    private Long songId;

    private Long userId;

    /** 评论人昵称（user 服务故障降级时为 "用户{id}"） */
    private String nickname;

    /** 评论人头像（可能为 null，前端用昵称首字兜底） */
    private String avatar;

    private String content;

    /** 根评论ID（NULL=根评论） */
    private Long rootId;

    /** 被回复的用户ID（回复条目有值） */
    private Long replyUserId;

    /** 被回复人昵称（仅回复条目聚合下发） */
    private String replyNickname;

    /** 点赞数 */
    private Long likeCount;

    /** 当前用户是否已赞（未登录恒 false） */
    private Boolean liked;

    /** 回复数（仅根评论下发） */
    private Long replyCount;

    /** 回复预览（仅根评论下发，最新 2 条；展开回复由 replies 接口分页） */
    private List<CommentVO> replyPreview;

    /** 前端展示用格式化时间 "yyyy-MM-dd HH:mm" */
    private String time;

    private LocalDateTime createTime;
}
