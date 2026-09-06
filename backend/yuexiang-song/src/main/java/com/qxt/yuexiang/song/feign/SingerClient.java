package com.qxt.yuexiang.song.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * 歌手服务 Feign 客户端：歌曲列表聚合歌手名、发布前查用户歌手身份。
 * fallback = SingerClientFallback：熔断时返回空数据，主流程降级不报错。
 */
@FeignClient(name = "yuexiang-singer", fallback = SingerClientFallback.class)
public interface SingerClient {

    /** 批量查歌手名：GET /singer/names?ids=1,2,3 → Map<id, name> */
    @GetMapping("/singer/names")
    Map<Long, String> names(@RequestParam("ids") String ids);

    /** 查用户的歌手身份：GET /singer/by-user?userId= → 裸 Map（singerId/name/status），非歌手返回空 Map */
    @GetMapping("/singer/by-user")
    Map<String, Object> byUser(@RequestParam("userId") Long userId);

    /** 查歌手的关联用户：GET /singer/user-of?singerId= → 裸 Map（userId），无记录返回空 Map */
    @GetMapping("/singer/user-of")
    Map<String, Object> userOf(@RequestParam("singerId") Long singerId);

    /** 子串模糊搜歌手名：GET /singer/search-ids?keyword= → 命中歌手ID列表 */
    @GetMapping("/singer/search-ids")
    List<Long> searchIds(@RequestParam("keyword") String keyword);
}
