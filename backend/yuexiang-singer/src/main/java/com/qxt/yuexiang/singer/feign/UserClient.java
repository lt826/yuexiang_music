package com.qxt.yuexiang.singer.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 用户服务 Feign 客户端：认证通过后升级角色
 */
@FeignClient(name = "yuexiang-user")
public interface UserClient {

    /** 升级角色：POST /user/role?userId=&role= */
    @PostMapping("/user/role")
    Void upgradeRole(@RequestParam("userId") Long userId, @RequestParam("role") String role);
}
