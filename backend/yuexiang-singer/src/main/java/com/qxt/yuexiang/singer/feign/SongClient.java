package com.qxt.yuexiang.singer.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 歌曲服务 Feign 客户端：推荐歌手按作品热度排序
 */
@FeignClient(name = "yuexiang-song")
public interface SongClient {

    /** 热门歌手ID：GET /song/hot-singers?limit= → 已发布歌曲总播放量倒序的歌手ID列表 */
    @GetMapping("/song/hot-singers")
    List<Long> hotSingerIds(@RequestParam("limit") int limit);
}
