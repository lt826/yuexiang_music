package com.qxt.yuexiang.user.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户基本信息（不含密码）
 */
@Data
public class UserVO {

    private Long id;

    private String username;

    private String nickname;

    private String avatar;

    private String phone;

    private String email;

    private Integer gender;

    private LocalDate birthday;

    private String signature;

    private String role;

    /** 状态 0禁用 1正常（管理端展示） */
    private Integer status;

    private LocalDateTime createTime;
}
