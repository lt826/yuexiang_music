package com.qxt.yuexiang.song.feign;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;

/**
 * UserClient 熔断降级：user 服务故障时返回空 Map，
 * 评论列表降级为显示"用户{id}"，主流程不报错（对齐 SingerClientFallback 风格）。
 */
@Slf4j
@Component
public class UserClientFallback implements UserClient {

    @Override
    public Map<Long, Map<String, Object>> batchInfo(String ids) {
        log.warn("UserClient 熔断降级: batchInfo(ids={})", ids);
        return Collections.emptyMap();
    }
}
