package com.qxt.yuexiang.playlist.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 歌单-歌曲关联表（注意：此表无逻辑删除字段）
 */
@Data
@TableName("playlist_song")
public class PlaylistSong {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long playlistId;

    private Long songId;

    /** 排序序号（追加时取最大值+1 保序） */
    private Integer sort;

    private LocalDateTime createTime;
}
