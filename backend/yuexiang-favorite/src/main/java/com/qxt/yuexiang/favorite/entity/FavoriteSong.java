package com.qxt.yuexiang.favorite.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 歌曲收藏表
 */
@Data
@TableName("favorite_song")
public class FavoriteSong {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long songId;

    private LocalDateTime createTime;
}
