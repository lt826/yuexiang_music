package com.qxt.yuexiang.song.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 歌曲点赞表：唯一约束 uk_user_song 保证一个用户对一首歌只能点赞一次；
 * 取消点赞即物理 DELETE，无 deleted 字段（对齐 favorite_song 惯例）
 */
@Data
@TableName("song_like")
public class SongLike {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long songId;

    private LocalDateTime createTime;
}
