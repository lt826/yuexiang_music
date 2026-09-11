package com.qxt.yuexiang.user.vo;

import lombok.Data;

/**
 * 关注/粉丝列表条目：仅公开资料 + 查看者关注态（点击条目进用户公开主页）
 */
@Data
public class FollowUserVO {

    private Long id;

    private String nickname;

    private String avatar;

    private String signature;

    /** 查看者是否已关注此人（匿名时为 null） */
    private Boolean followed;

    /** 被关注者角色（USER/SINGER/ADMIN）：前端按 SINGER 统一显示"歌手"标签 */
    private String role;
}
