package com.qxt.yuexiang.ai.feign;

import com.qxt.yuexiang.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;

/**
 * 歌单服务 Feign 客户端：AI 生成歌单工具的写操作编排 + 电台节目单读取
 */
@FeignClient(name = "yuexiang-playlist")
public interface PlaylistClient {

    /** 创建歌单，返回新歌单 ID（内部直连，需显式透传用户身份） */
    @PostMapping("/playlist/create")
    Result<Long> create(@RequestHeader("X-User-Id") Long userId, @RequestBody Map<String, Object> dto);

    /** 歌单添加歌曲（仅创建者可操作） */
    @PostMapping("/playlist/{playlistId}/songs/{songId}")
    Result<Void> addSong(@RequestHeader("X-User-Id") Long userId,
                         @PathVariable("playlistId") Long playlistId,
                         @PathVariable("songId") Long songId);

    /** 歌单详情（含有序歌曲列表，按 sort 保序）；playlist 服务匿名可看 */
    @GetMapping("/playlist/{id}")
    Result<Map<String, Object>> detail(@PathVariable("id") Long playlistId);
}
