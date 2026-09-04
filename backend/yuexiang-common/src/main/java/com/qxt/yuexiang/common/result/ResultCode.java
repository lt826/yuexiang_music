package com.qxt.yuexiang.common.result;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 响应状态码
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    SUCCESS(200, "操作成功"),
    ERROR(500, "操作失败"),

    // 通用 4xx
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录或 token 已失效"),
    FORBIDDEN(403, "无权限访问"),
    NOT_FOUND(404, "资源不存在"),
    TOO_MANY_REQUESTS(429, "请求过于频繁，请稍后再试"),

    // 用户模块 1xxx
    USER_NOT_EXIST(1001, "用户不存在"),
    USERNAME_EXISTED(1002, "用户名已存在"),
    PASSWORD_ERROR(1003, "用户名或密码错误"),

    // 歌曲/歌手/歌单模块 2xxx
    SONG_NOT_EXIST(2001, "歌曲不存在"),
    SINGER_NOT_EXIST(2002, "歌手不存在"),
    PLAYLIST_NOT_EXIST(2003, "歌单不存在"),

    // 业务通用 3xxx
    REPEAT_OPERATION(3001, "请勿重复操作");

    private final Integer code;
    private final String message;
}
