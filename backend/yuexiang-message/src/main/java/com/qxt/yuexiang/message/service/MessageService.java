package com.qxt.yuexiang.message.service;

import java.util.List;
import java.util.Map;

/**
 * 消息服务接口（FUN-11）
 */
public interface MessageService {

    /** 管理员收件人标识：普通用户 ID 自 1 起，0 保留为管理员收件箱，永不撞号 */
    Long ADMIN_INBOX_USER_ID = 0L;

    /** 发送消息（admin 公告走 type=SYSTEM；审核结果通知走 type=AUDIT） */
    void send(Long userId, String title, String content, String type);

    /** 群发系统公告（admin） */
    void broadcast(String title, String content);

    /** 我的消息列表（时间倒序分页） */
    Map<String, Object> myList(Long userId, int page, int size);

    /** 未读数 */
    long unreadCount(Long userId);

    /** 标记已读（单条或全部：messageId 为 null 表示全部） */
    void markRead(Long userId, Long messageId);

    /** 通知全部管理员：新待审事项（type=ADMIN，写入管理员收件箱） */
    void notifyAdmins(String title, String content);

    /** 管理员收件箱列表 */
    List<Map<String, Object>> adminList(int page, int size);

    /** 管理员收件箱未读数 */
    long adminUnreadCount();

    /** 管理员收件箱标记已读（messageId 为 null 表示全部） */
    void adminMarkRead(Long messageId);
}
