package com.qxt.yuexiang.favorite.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 歌手服务 Feign 客户端：新歌上架粉丝通知时合并"关注该歌手"的用户
 */
@FeignClient(name = "yuexiang-singer")
public interface SingerClient {

    /** 关注某歌手的用户ID列表：GET /singer/follower-ids?singerId=（内部接口） */
    @GetMapping("/singer/follower-ids")
    List<Long> followerIds(@RequestParam("singerId") Long singerId);
}
