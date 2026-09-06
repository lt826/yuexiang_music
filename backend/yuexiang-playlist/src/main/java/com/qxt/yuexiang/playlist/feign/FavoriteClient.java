package com.qxt.yuexiang.playlist.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 收藏服务 Feign 客户端：查用户收藏的歌单 ID 集（歌单列表标注收藏态）
 */
@FeignClient(name = "yuexiang-favorite")
public interface FavoriteClient {

    /** 我收藏的歌单ID：GET /favorite/playlist/ids → 裸 List */
    @GetMapping("/favorite/playlist/ids")
    List<Long> favoredPlaylistIds(@RequestParam("userId") Long userId);
}
