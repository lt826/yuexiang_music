package com.qxt.yuexiang.circle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 一起听成员进出流水；禁言状态在 Redis 实时层，不入库
 */
@Data
@TableName("listen_room_member")
public class ListenRoomMember {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long roomId;

    private Long userId;

    private LocalDateTime joinTime;

    /** 离开时间（NULL=仍在房间） */
    private LocalDateTime leaveTime;
}
