package com.qxt.yuexiang.admin.vo;

import lombok.Data;

/**
 * 管理员登录返回：token 与管理员信息，role 固定 ADMIN
 */
@Data
public class AdminLoginVO {

    private String token;

    private Long adminId;

    private String username;

    private String nickname;

    private String role;
}
