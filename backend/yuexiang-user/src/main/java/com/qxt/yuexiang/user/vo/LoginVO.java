package com.qxt.yuexiang.user.vo;

import lombok.Data;

/**
 * 登录成功返回：token 与用户信息（对齐详细设计 12.2 接口契约：
 * token、userId、nickname、avatar、role）
 */
@Data
public class LoginVO {

    private String token;

    private Long userId;

    private String username;

    private String nickname;

    private String avatar;

    /** USER普通用户 SINGER歌手 ADMIN管理员 */
    private String role;
}
