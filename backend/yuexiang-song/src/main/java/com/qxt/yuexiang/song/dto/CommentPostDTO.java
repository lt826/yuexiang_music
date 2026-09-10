package com.qxt.yuexiang.song.dto;

import lombok.Data;

/**
 * 发表评论入参（rootId 为空 = 发根评论；有值 = 回复该根评论下的某人）
 */
@Data
public class CommentPostDTO {

    /** 评论内容（1~500 字，service 层校验） */
    private String content;

    /** 根评论ID（回复时传根评论ID，楼中楼两级扁平化） */
    private Long rootId;

    /** 被回复的评论ID（用于解析被回复人；为空表示直接回复根评论） */
    private Long replyToId;
}
