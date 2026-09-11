package com.qxt.yuexiang.circle.dto;

import lombok.Data;

/**
 * 发帖：文字 + 可选歌曲卡片/图片
 */
@Data
public class PostCreateDTO {

    /** 帖子文字内容 */
    private String content;

    /** 插入的歌曲ID（可空=纯文字帖） */
    private Long songId;

    /** 帖子图片URL（可空，最多1张，file 服务 /file/upload type=IMAGE 返回） */
    private String image;
}
