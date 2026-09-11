package com.qxt.yuexiang.user.vo;

import lombok.Data;

/**
 * 关注/粉丝计数汇总（个人中心与用户公开主页共用）
 */
@Data
public class FollowSummaryVO {

    private Long userId;

    /** 该用户关注了多少人 */
    private Long followingCount;

    /** 该用户有多少粉丝 */
    private Long followerCount;

    /** 当前查看者是否已关注该用户（匿名时为 null） */
    private Boolean following;
}
