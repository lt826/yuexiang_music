package com.qxt.yuexiang.circle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 一起听房间：持久化房间元信息；实时播放状态走 Redis
 */
@Data
@TableName("listen_room")
public class ListenRoom {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long circleId;

    /** 发起人用户ID（圈主/管理员） */
    private Long creatorId;

    /** 房间主题（选填） */
    private String theme;

    /** 初始歌曲ID */
    private Long initialSongId;

    /** 房间状态 0进行中 1已结束 */
    private Integer status;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
