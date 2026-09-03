package com.qxt.yuexiang.singer.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 歌手信息（列表/主页/申请状态共用）
 */
@Data
public class SingerVO {

    private Long id;

    private Long userId;

    private String name;

    private String avatar;

    private String introduction;

    /** 0待审核 1正常(已通过) 2驳回 3冻结 */
    private Integer status;

    /** 驳回原因（status=2 时有值） */
    private String rejectReason;

    private LocalDateTime createTime;
}
