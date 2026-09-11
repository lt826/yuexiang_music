package com.qxt.yuexiang.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户互关：follower 关注 following；取消关注物理 DELETE（无逻辑删，对齐 singer_follow 惯例）
 */
@Data
@TableName("user_follow")
public class UserFollow {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关注者用户ID */
    private Long followerId;

    /** 被关注用户ID */
    private Long followingId;

    private LocalDateTime createTime;
}
