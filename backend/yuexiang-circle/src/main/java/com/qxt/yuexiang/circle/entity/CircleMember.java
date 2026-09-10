package com.qxt.yuexiang.circle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 乐坊圈子成员：退出即物理删除（对齐 favorite_song 惯例）
 */
@Data
@TableName("circle_member")
public class CircleMember {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long circleId;

    private Long userId;

    /** 角色 OWNER圈主 ADMIN管理员 MEMBER成员 */
    private String role;

    /** 加入时间 */
    private LocalDateTime createTime;
}
