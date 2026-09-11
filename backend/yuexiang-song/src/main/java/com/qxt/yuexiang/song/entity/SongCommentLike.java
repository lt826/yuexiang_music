package com.qxt.yuexiang.song.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 评论点赞表：唯一约束保证一个用户对一条评论只能点赞一次；
 * 取消点赞即物理 DELETE（对齐 song_like / singer_follow 惯例）
 */
@Data
@TableName("song_comment_like")
public class SongCommentLike {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long commentId;

    private LocalDateTime createTime;
}
