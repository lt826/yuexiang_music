package com.qxt.yuexiang.favorite.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 歌单收藏表（无逻辑删除字段）
 */
@Data
@TableName("favorite_playlist")
public class FavoritePlaylist {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long playlistId;

    private LocalDateTime createTime;
}
