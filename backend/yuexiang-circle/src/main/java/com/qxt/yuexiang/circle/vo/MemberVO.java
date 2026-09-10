package com.qxt.yuexiang.circle.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 圈内成员 VO
 */
@Data
public class MemberVO {

    private Long userId;

    private String nickname;

    private String avatar;

    /** OWNER圈主 ADMIN管理员 MEMBER成员 */
    private String role;

    private LocalDateTime joinTime;
}
