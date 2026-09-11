package com.qxt.yuexiang.circle.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;
import java.util.Map;

/**
 * 歌曲服务客户端：帖子歌曲卡片校验与信息聚合（内部接口裸返回 Map 约定）
 * 注意：/song/batch 为 POST + body 列表；JSON 反序列化后 key 为 String
 */
@FeignClient(name = "yuexiang-song")
public interface SongClient {

    /** 批量查歌曲（仅返回已发布歌曲）：{songId字符串: {name, singerName, cover, ...}} */
    @PostMapping("/song/batch")
    Map<String, Map<String, Object>> batch(@RequestBody List<Long> songIds);
}
