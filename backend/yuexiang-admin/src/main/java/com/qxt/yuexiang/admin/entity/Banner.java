package com.qxt.yuexiang.admin.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 首页轮播图表
 */
@Data
@TableName("banner")
public class Banner {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 主标题（如歌星名） */
    private String title;

    /** 副标题/推广语 */
    private String subtitle;

    /** 海报图片URL（上传或歌曲封面） */
    private String imageUrl;

    /** 关联歌曲ID（可选，点击轮播跳播放） */
    private Long songId;

    /** 排序（小的在前） */
    private Integer sortOrder;

    /** 状态 0下架 1上架 */
    private Integer status;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
