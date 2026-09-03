package com.qxt.yuexiang.admin.dto;

import lombok.Data;

/**
 * 轮播图新增/编辑入参
 */
@Data
public class BannerSaveDTO {

    /** 编辑时传，新增不传 */
    private Long id;

    /** 主标题（如歌星名，必填） */
    private String title;

    /** 副标题/推广语 */
    private String subtitle;

    /** 海报图片URL（必填：上传海报或所选歌曲的封面） */
    private String imageUrl;

    /** 关联歌曲ID（可选：点击轮播跳播放该歌曲） */
    private Long songId;

    /** 排序（小的在前，默认 0） */
    private Integer sortOrder;

    /** 状态 0下架 1上架（默认 1） */
    private Integer status;
}
