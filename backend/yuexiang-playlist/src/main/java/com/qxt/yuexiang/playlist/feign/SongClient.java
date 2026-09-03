package com.qxt.yuexiang.playlist.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.Map;

/**
 * 歌曲服务 Feign 客户端：加歌前校验存在性 + 歌单详情聚合歌曲信息
 */
@FeignClient(name = "yuexiang-song")
public interface SongClient {

    /** 批量查歌曲：POST /song/batch → Map<songId, SongVO 裸Map>（返回已过滤未发布的） */
    @PostMapping("/song/batch")
    Map<Long, Map<String, Object>> batch(@RequestBody List<Long> songIds);
}
