package com.qxt.yuexiang.playlist.dto;

import lombok.Data;

/**
 * 创建/编辑歌单入参
 */
@Data
public class PlaylistSaveDTO {

    /** 歌单标题（必填） */
    private String title;

    /** 封面 URL（可选，走 file 服务） */
    private String cover;

    /** 描述 */
    private String description;

    /** 标签（如 民谣/流行） */
    private String tag;

    /** 是否投稿到歌单广场（0 私藏 1 上传公开，编辑时可切换） */
    private Integer published;
}
