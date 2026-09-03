package com.qxt.yuexiang.file.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文件元数据表
 */
@Data
@TableName("file_info")
public class FileInfo {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String originalName;

    /** 存储文件名（UUID + 扩展名） */
    private String storedName;

    private String url;

    /** 类型 SONG歌曲 COVER封面 AVATAR头像 */
    private String type;

    private Long size;

    private Long uploaderId;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createTime;
}
