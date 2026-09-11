package com.qxt.yuexiang.circle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 帖子评论：parent_id 支持楼中楼（NULL=评论帖子，非空=回复某条评论）
 */
@Data
@TableName("circle_comment")
public class CircleComment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long postId;

    /** 评论人用户ID */
    private Long userId;

    /** 父评论ID（NULL=直接评论帖子） */
    private Long parentId;

    /** 被回复人用户ID（楼中楼展示"@某人"） */
    private Long replyToUserId;

    /** 评论内容 */
    private String content;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
