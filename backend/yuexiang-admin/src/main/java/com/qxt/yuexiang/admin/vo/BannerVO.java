package com.qxt.yuexiang.admin.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 轮播图项（管理端与门户共用）
 */
@Data
public class BannerVO {

    private Long id;

    /** 主标题（如歌星名） */
    private String title;

    /** 副标题/推广语 */
    private String subtitle;

    /** 海报图片URL（上传或歌曲封面） */
    private String imageUrl;

    /** 关联歌曲ID（可选） */
    private Long songId;

    /** 排序（小的在前） */
    private Integer sortOrder;

    /** 状态 0下架 1上架 */
    private Integer status;

    private LocalDateTime createTime;
}
