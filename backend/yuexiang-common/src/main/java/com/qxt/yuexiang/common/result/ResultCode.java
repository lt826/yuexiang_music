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
    REPEAT_OPERATION(3001, "请勿重复操作"),

    // 乐坊模块 4xxx
    CIRCLE_NOT_EXIST(4001, "圈子不存在"),
    CIRCLE_NAME_REVIEW(4002, "圈子名称正在审核中，审核通过后可见"),
    CIRCLE_NAME_REJECTED(4003, "圈子名称未通过审核"),
    CIRCLE_NOT_MEMBER(4004, "请先加入圈子"),
    CIRCLE_ALREADY_MEMBER(4005, "您已是圈内成员"),
    POST_NOT_EXIST(4006, "帖子不存在"),
    COMMENT_NOT_EXIST(4007, "评论不存在"),
    PINNED_LIMIT(4008, "置顶帖子最多 2 条"),
    ROOM_NOT_EXIST(4009, "一起听房间不存在"),
    ROOM_RUNNING(4010, "该圈子已有一场一起听正在进行"),
    MUTED(4011, "您已被禁言");

    private final Integer code;
    private final String message;
}
