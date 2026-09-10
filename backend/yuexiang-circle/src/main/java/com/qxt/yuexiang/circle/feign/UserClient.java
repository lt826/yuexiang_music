package com.qxt.yuexiang.circle.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * 用户服务客户端：昵称/头像聚合（内部接口裸返回约定）
 * 注意：JSON 反序列化后 Map key 均为 String，故统一用 String key 承接
 */
@FeignClient(name = "yuexiang-user")
public interface UserClient {

    /** 批量用户信息：{"1": {nickname, avatar, ...}}，单次上限 200 */
    @GetMapping("/user/batch-info")
    Map<String, Map<String, Object>> batchInfo(@RequestParam("ids") java.util.List<Long> ids);
}
