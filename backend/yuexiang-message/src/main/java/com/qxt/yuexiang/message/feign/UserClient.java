package com.qxt.yuexiang.message.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;
import java.util.Map;

/**
 * 用户服务 Feign 客户端：群发公告时拉取用户列表
 */
@FeignClient(name = "yuexiang-user")
public interface UserClient {

    /** 全部正常用户ID：GET /user/all-ids → 裸 List */
    @GetMapping("/user/all-ids")
    List<Long> allIds();
}
