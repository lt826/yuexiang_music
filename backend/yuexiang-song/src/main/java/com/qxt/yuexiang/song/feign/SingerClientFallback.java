package com.qxt.yuexiang.song.feign;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * SingerClient 熔断降级（feign.sentinel.enabled=true 时生效）：
 * singer 服务故障/慢调用触发熔断时走这里，返回空数据让上层走"无歌手名"降级逻辑，
 * 榜单/搜索等主流程照常返回（宁可少显示歌手名，不可整页报错）。
 */
@Slf4j
@Component
public class SingerClientFallback implements SingerClient {

    @Override
    public Map<Long, String> names(String ids) {
        log.warn("SingerClient 熔断降级: names(ids={})", ids);
        return Collections.emptyMap();
    }

    @Override
    public List<Long> searchIds(String keyword) {
        log.warn("SingerClient 熔断降级: searchIds(keyword={})", keyword);
        return Collections.emptyList();
    }

    @Override
    public Map<String, Object> byUser(Long userId) {
        log.warn("SingerClient 熔断降级: byUser(userId={})", userId);
        return Collections.emptyMap();
    }

    @Override
    public Map<String, Object> userOf(Long singerId) {
        log.warn("SingerClient 熔断降级: userOf(singerId={})", singerId);
        return Collections.emptyMap();
    }
}
