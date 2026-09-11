package com.qxt.yuexiang.message.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息项
 */
@Data
public class MessageVO {

    private Long id;

    private String title;

    private String content;

    /** SYSTEM系统 AUDIT审核 FAN粉丝 */
    private String type;

    private Integer isRead;

    private LocalDateTime createTime;
}
