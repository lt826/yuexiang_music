package com.qxt.yuexiang.playlist.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 歌单表
 */
@Data
@TableName("playlist")
public class Playlist {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String title;

    private String cover;

    private String description;

    /** 标签（推荐歌单的分类） */
    private String tag;

    /** 是否官方推荐歌单 0否 1是 */
    private Integer isOfficial;

    /** 是否投稿到歌单广场（0 私藏 1 已上传公开） */
    private Integer published;

    private Long playCount;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
