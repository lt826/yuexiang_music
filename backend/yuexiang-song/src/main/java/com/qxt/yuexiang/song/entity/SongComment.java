package com.qxt.yuexiang.song.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 歌曲评论表：两级楼中楼（根评论 + 直挂根的回复），逻辑删除（用户删自己的评论）
 */
@Data
@TableName("song_comment")
public class SongComment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long songId;

    private Long userId;

    /** 根评论ID（NULL=根评论；回复直挂根，避免递归无限深） */
    private Long rootId;

    /** 被回复的用户ID（回复场景展示"回复 @xx"） */
    private Long replyUserId;

    /** 评论内容（≤500 字，service 层校验） */
    private String content;

    /** 点赞数（冗余计数，点赞接口维护） */
    private Integer likeCount;

    /** 回复数（冗余计数，回复时维护） */
    private Integer replyCount;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
