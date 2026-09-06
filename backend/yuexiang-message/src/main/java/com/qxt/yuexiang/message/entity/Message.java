package com.qxt.yuexiang.message.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息通知表
 */
@Data
@TableName("message")
public class Message {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String title;

    private String content;

    /** 类型 SYSTEM系统 AUDIT审核 FAN粉丝 */
    private String type;

    /** 是否已读 0未读 1已读 */
    private Integer isRead;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createTime;
}
