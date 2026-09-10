package com.qxt.yuexiang.singer.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 歌手关注表：唯一约束 uk_user_singer 保证一个用户对一个歌手只能关注一次；
 * 取消关注即物理 DELETE，无 deleted 字段（对齐 song_like 惯例）
 */
@Data
@TableName("singer_follow")
public class SingerFollow {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long singerId;

    private LocalDateTime createTime;
}
