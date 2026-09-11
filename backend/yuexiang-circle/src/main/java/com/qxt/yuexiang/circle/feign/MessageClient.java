package com.qxt.yuexiang.circle.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 消息服务客户端：审核结果定向通知 / 管理员待审通知（内部接口，网关兜底拦截前端直调）
 */
@FeignClient(name = "yuexiang-message")
public interface MessageClient {

    /** 定向发送站内信（type 默认 AUDIT） */
    @PostMapping("/message/send")
    void send(@RequestParam("userId") Long userId,
              @RequestParam("title") String title,
              @RequestParam("content") String content,
              @RequestParam("type") String type);

    /** 通知全部管理员（user_id=0 管理员收件箱） */
    @PostMapping("/message/notify-admins")
    void notifyAdmins(@RequestParam("title") String title,
                      @RequestParam("content") String content);
}
