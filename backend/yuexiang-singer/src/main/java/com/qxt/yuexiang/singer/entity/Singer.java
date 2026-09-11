package com.qxt.yuexiang.singer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 歌手表
 */
@Data
@TableName("singer")
public class Singer {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String name;

    private String avatar;

    private String introduction;

    /** 状态 0待审核 1正常 2驳回 3冻结 */
    private Integer status;

    /** 驳回原因（status=2 时有值） */
    private String rejectReason;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
