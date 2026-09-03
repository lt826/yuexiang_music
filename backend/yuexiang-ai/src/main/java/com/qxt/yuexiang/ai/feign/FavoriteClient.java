package com.qxt.yuexiang.ai.feign;

import com.qxt.yuexiang.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;

/**
 * 收藏服务 Feign 客户端：AI 听歌周报工具取数
 */
@FeignClient(name = "yuexiang-favorite")
public interface FavoriteClient {

    /** 听歌周报：最近 7 天播放统计（内部直连，需显式透传用户身份） */
    @GetMapping("/favorite/report/weekly")
    Result<Map<String, Object>> weeklyReport(@RequestHeader("X-User-Id") Long userId);
}
