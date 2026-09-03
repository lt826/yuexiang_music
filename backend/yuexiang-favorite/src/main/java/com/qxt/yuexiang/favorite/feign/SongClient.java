package com.qxt.yuexiang.favorite.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.Map;

/**
 * 歌曲服务 Feign 客户端：收藏列表/收听历史聚合歌曲信息
 */
@FeignClient(name = "yuexiang-song")
public interface SongClient {

    /** 批量查歌曲：POST /song/batch → Map<songId, SongVO> */
    @PostMapping("/song/batch")
    Map<Long, Map<String, Object>> batch(@RequestBody List<Long> songIds);

    /** 歌手已发布歌曲ID：GET /song/ids-by-singer/{singerId}（内部接口，粉丝通知用） */
    @GetMapping("/song/ids-by-singer/{singerId}")
    List<Long> idsBySinger(@PathVariable("singerId") Long singerId);
}
