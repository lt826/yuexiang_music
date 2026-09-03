package com.qxt.yuexiang.song.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 歌曲表
 */
@Data
@TableName("song")
public class Song {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long singerId;

    private String name;

    private String cover;

    private String url;

    private String lyric;

    /** 风格（流行/摇滚/民谣等） */
    private String style;

    private Long playCount;

    /** 审核状态 0待审核 1已发布 2已下架 */
    private Integer status;

    /** 审核驳回原因 */
    private String rejectReason;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
