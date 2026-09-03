package com.qxt.yuexiang.file.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 上传结果：前端拿 url 直接访问文件
 */
@Data
public class FileVO {

    private Long id;

    private String originalName;

    private String url;

    /** 类型 SONG歌曲 COVER封面 AVATAR头像 */
    private String type;

    private Long size;

    private LocalDateTime createTime;
}
