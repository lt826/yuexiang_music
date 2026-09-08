package com.qxt.yuexiang.circle.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 圈子 VO：广场列表 / 详情
 */
@Data
public class CircleVO {

    private Long id;

    private Long creatorId;

    /** 圈主昵称（聚合） */
    private String creatorName;

    private String name;

    private String description;

    private String cover;

    /** 审核状态 0待审核 1正常 2驳回 */
    private Integer status;

    /** 驳回原因 */
    private String rejectReason;

    private Integer memberCount;

    private Integer postCount;

    /** 当前用户是否已加入 */
    private Boolean joined;

    /** 当前用户在圈内的角色（OWNER/ADMIN/MEMBER，未加入为空） */
    private String myRole;

    /** 当前是否有一场一起听正在进行 */
    private Boolean liveRoom;

    /** 进行中一起听房间ID */
    private Long liveRoomId;

    private LocalDateTime createTime;
}
