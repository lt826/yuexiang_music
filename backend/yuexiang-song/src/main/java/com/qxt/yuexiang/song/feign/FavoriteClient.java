package com.qxt.yuexiang.song.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 收藏服务 Feign 客户端：猜你喜欢的收听历史画像来源
 */
@FeignClient(name = "yuexiang-favorite")
public interface FavoriteClient {

    /** 最近收听的歌曲ID（按时间倒序去重）：GET /favorite/history/latest-song-ids */
    @GetMapping("/favorite/history/latest-song-ids")
    List<Long> latestSongIds(@RequestParam("userId") Long userId,
                             @RequestParam("limit") int limit);

    /** 用户收藏的歌曲ID集合：GET /favorite/song/ids-by-user（内部接口，裸 List） */
    @GetMapping("/favorite/song/ids-by-user")
    List<Long> favoriteSongIds(@RequestParam("userId") Long userId);
}
