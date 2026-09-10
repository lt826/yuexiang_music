package com.qxt.yuexiang.circle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 乐坊帖子：文字 + 可选歌曲卡片/图片；置顶最多 2 条由 service 校验
 */
@Data
@TableName("circle_post")
public class CirclePost {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long circleId;

    /** 发帖人用户ID */
    private Long userId;

    /** 帖子文字内容 */
    private String content;

    /** 插入的歌曲ID（可空=纯文字帖） */
    private Long songId;

    /** 帖子图片URL（可空，最多1张，file 服务上传返回） */
    private String image;

    /** 置顶 0普通 1置顶 */
    private Integer pinned;

    /** 点赞数 */
    private Integer likeCount;

    /** 评论数 */
    private Integer commentCount;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
