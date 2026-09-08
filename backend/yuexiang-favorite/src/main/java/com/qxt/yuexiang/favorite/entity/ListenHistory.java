package com.qxt.yuexiang.favorite.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 收听历史表
 */
@Data
@TableName("listen_history")
public class ListenHistory {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long songId;

    private LocalDateTime listenTime;
}
