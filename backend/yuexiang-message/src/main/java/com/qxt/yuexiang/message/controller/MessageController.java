package com.qxt.yuexiang.message.controller;

import com.qxt.yuexiang.common.result.Result;
import com.qxt.yuexiang.message.service.MessageService;
import com.qxt.yuexiang.message.vo.MessageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 消息接口（FUN-11：发布/查看/已读）
 */
@RestController
@RequestMapping("/message")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;

    /** GET /message/list 我的消息（分页，附未读数） */
    @GetMapping("/list")
    public Result<Map<String, Object>> list(@RequestHeader("X-User-Id") Long userId,
                                            @RequestHeader(value = "X-User-Role", required = false) String role,
                                            @RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        // 防串号：管理员身份的 X-User-Id 是 adminId（与用户表 ID 同空间），
        // 不允许其读取用户消息面；管理员走 /message/admin/* 专用收件箱
        if ("ADMIN".equals(role)) {
            return Result.success(new HashMap<>());
        }
        return Result.success(messageService.myList(userId, page, size));
    }

    /** GET /message/unread 未读数（导航铃铛轮询） */
    @GetMapping("/unread")
    public Result<Long> unread(@RequestHeader("X-User-Id") Long userId,
                               @RequestHeader(value = "X-User-Role", required = false) String role) {
        if ("ADMIN".equals(role)) {
            return Result.success(0L);
        }
        return Result.success(messageService.unreadCount(userId));
    }

    /** POST /message/read/{id} 单条已读；POST /message/read/all 全部已读 */
    @PostMapping("/read/{messageId}")
    public Result<Void> read(@RequestHeader("X-User-Id") Long userId,
                             @PathVariable("messageId") Long messageId) {
        messageService.markRead(userId, messageId);
        return Result.success();
    }

    @PostMapping("/read/all")
    public Result<Void> readAll(@RequestHeader("X-User-Id") Long userId) {
        messageService.markRead(userId, null);
        return Result.success();
    }

    /** POST /message/broadcast 群发系统公告（管理员，经 admin 服务转发或直调） */
    @PostMapping("/broadcast")
    public Result<Void> broadcast(@RequestParam String title, @RequestParam String content) {
        messageService.broadcast(title, content);
        return Result.success();
    }

    /** POST /message/send 定向发送（内部接口：审核结果通知） */
    @PostMapping("/send")
    public Result<Void> send(@RequestParam Long userId,
                             @RequestParam String title,
                             @RequestParam String content,
                             @RequestParam(defaultValue = "AUDIT") String type) {
        messageService.send(userId, title, content, type);
        return Result.success();
    }

    /** POST /message/notify-admins 通知管理员（内部接口：新待审事项） */
    @PostMapping("/notify-admins")
    public Result<Void> notifyAdmins(@RequestParam String title, @RequestParam String content) {
        messageService.notifyAdmins(title, content);
        return Result.success();
    }

    // ===== 管理员收件箱（user_id=0，只暴露给 ADMIN，经 admin 服务转发访问） =====

    /** GET /message/admin/list */
    @GetMapping("/admin/list")
    public Result<Map<String, Object>> adminList(@RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        Map<String, Object> result = new HashMap<>();
        result.put("items", messageService.adminList(page, size));
        result.put("unread", messageService.adminUnreadCount());
        return Result.success(result);
    }

    /** GET /message/admin/unread */
    @GetMapping("/admin/unread")
    public Result<Long> adminUnread() {
        return Result.success(messageService.adminUnreadCount());
    }

    /** POST /message/admin/read/{id} 单条已读 */
    @PostMapping("/admin/read/{messageId}")
    public Result<Void> adminRead(@PathVariable("messageId") Long messageId) {
        messageService.adminMarkRead(messageId);
        return Result.success();
    }

    /** POST /message/admin/read/all 全部已读 */
    @PostMapping("/admin/read/all")
    public Result<Void> adminReadAll() {
        messageService.adminMarkRead(null);
        return Result.success();
    }
}
