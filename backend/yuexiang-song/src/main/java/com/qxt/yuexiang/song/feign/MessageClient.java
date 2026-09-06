package com.qxt.yuexiang.song.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 消息服务 Feign 客户端：审核结果通知（失败不阻塞主流程）
 */
@FeignClient(name = "yuexiang-message")
public interface MessageClient {

    /** 定向发送：POST /message/send */
    @PostMapping("/message/send")
    Void send(@RequestParam("userId") Long userId,
              @RequestParam("title") String title,
              @RequestParam("content") String content,
              @RequestParam("type") String type);

    /** 通知管理员新待审：POST /message/notify-admins */
    @PostMapping("/message/notify-admins")
    Void notifyAdmins(@RequestParam("title") String title,
                      @RequestParam("content") String content);
}
