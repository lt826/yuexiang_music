package com.qxt.yuexiang.circle.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评论 VO：楼中楼结构（replies 为该评论下的回复列表）
 */
@Data
public class CommentVO {

    private Long id;

    private Long postId;

    private Long userId;

    private String nickname;

    private String avatar;

    private String content;

    /** 楼中楼回复 */
    private List<CommentVO> replies;

    /** 是否可删（发评人本人/圈主/圈管理员/平台管理员） */
    private Boolean canDelete;

    private LocalDateTime createTime;
}
