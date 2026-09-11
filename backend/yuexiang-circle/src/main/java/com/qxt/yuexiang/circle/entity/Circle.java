package com.qxt.yuexiang.circle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 乐坊圈子：所有人可创建，名称需管理员审核
 */
@Data
@TableName("circle")
public class Circle {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 圈主用户ID */
    private Long creatorId;

    /** 圈子名称（需审核） */
    private String name;

    /** 圈子简介 */
    private String description;

    /** 封面图URL */
    private String cover;

    /** 审核状态 0待审核 1正常 2驳回 */
    private Integer status;

    /** 驳回原因 */
    private String rejectReason;

    /** 成员数（圈主算第1个） */
    private Integer memberCount;

    /** 帖子数 */
    private Integer postCount;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
