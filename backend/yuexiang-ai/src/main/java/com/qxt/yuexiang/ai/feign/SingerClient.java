package com.qxt.yuexiang.ai.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 歌手服务 Feign 客户端：AI 先把歌手名转成 ID，再查其作品
 */
@FeignClient(name = "yuexiang-singer")
public interface SingerClient {

    /** 子串模糊搜歌手名 → 命中歌手ID列表 */
    @GetMapping("/singer/search-ids")
    List<Long> searchIds(@RequestParam("keyword") String keyword);

    /** 批量查歌手名：ids=1,2,3 → Map<singerId, name> */
    @GetMapping("/singer/names")
    java.util.Map<Long, String> names(@RequestParam("ids") String ids);
}
