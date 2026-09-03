package com.qxt.yuexiang.song.dto;

import lombok.Data;

/**
 * 歌曲发布入参（文件先经 file 服务上传，此处只收 URL）
 */
@Data
public class SongPublishDTO {

    /** 歌曲名（必填） */
    private String name;

    /** 音频文件 URL（必填，file 服务上传返回） */
    private String url;

    /** 封面 URL（可选） */
    private String cover;

    /** LRC 歌词内容或 URL（可选） */
    private String lyric;

    /** 风格（流行/摇滚/民谣等） */
    private String style;
}
