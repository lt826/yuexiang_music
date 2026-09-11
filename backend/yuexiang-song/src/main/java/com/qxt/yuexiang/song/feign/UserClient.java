package com.qxt.yuexiang.song.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * 用户服务 Feign 客户端：评论列表聚合评论人昵称/头像。
 * fallback = UserClientFallback：熔断时返回空 Map，评论降级显示"用户{id}"不报错。
 */
@FeignClient(name = "yuexiang-user", fallback = UserClientFallback.class)
public interface UserClient {

    /** 批量查用户简要信息：GET /user/batch-info?ids=1,2,3 → Map<id, {nickname, avatar}> */
    @GetMapping("/user/batch-info")
    Map<Long, Map<String, Object>> batchInfo(@RequestParam("ids") String ids);
}
