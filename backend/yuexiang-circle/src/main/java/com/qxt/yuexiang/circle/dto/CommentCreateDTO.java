package com.qxt.yuexiang.circle.dto;

import lombok.Data;

/**
 * 发表评论：parent 为空 = 评论帖子；非空 = 回复某条评论（楼中楼）
 */
@Data
public class CommentCreateDTO {

    /** 评论内容 */
    private String content;

    /** 父评论ID（可空） */
    private Long parentId;
}
