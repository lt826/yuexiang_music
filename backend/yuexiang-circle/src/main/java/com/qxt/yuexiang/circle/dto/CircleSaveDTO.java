package com.qxt.yuexiang.circle.dto;

import lombok.Data;

/**
 * 创建/编辑圈子
 */
@Data
public class CircleSaveDTO {

    /** 圈子名称（需审核） */
    private String name;

    /** 圈子简介 */
    private String description;

    /** 封面图URL */
    private String cover;
}
