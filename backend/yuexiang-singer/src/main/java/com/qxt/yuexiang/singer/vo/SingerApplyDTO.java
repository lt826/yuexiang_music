package com.qxt.yuexiang.singer.vo;

import lombok.Data;

/**
 * 歌手认证申请入参
 */
@Data
public class SingerApplyDTO {

    /** 艺名（必填） */
    private String name;

    /** 简介 */
    private String introduction;

    /** 头像 URL（可选，走 file 服务上传后传回） */
    private String avatar;
}
