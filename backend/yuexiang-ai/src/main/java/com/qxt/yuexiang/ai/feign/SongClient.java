package com.qxt.yuexiang.ai.feign;

import com.qxt.yuexiang.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.Map;

/**
 * 歌曲服务 Feign 客户端：AI 工具执行真实检索的数据来源
 */
@FeignClient(name = "yuexiang-song")
public interface SongClient {

    /**
     * 关键字搜索（分页）：song 服务的 Result.data 为 SearchResultVO 结构
     * {total, page, size, songs:[{id,name,singerId,singerName,style,playCount,...}]}
     */
    @GetMapping("/song/search")
    Result<Map<String, Object>> search(@org.springframework.web.bind.annotation.RequestParam("keyword") String keyword,
                                       @org.springframework.web.bind.annotation.RequestParam("page") int page,
                                       @org.springframework.web.bind.annotation.RequestParam("size") int size);

    /** 歌手主页歌曲（仅已发布，含 singerName 聚合） */
    @GetMapping("/song/by-singer/{id}")
    Result<List<Map<String, Object>>> bySinger(@PathVariable("id") Long singerId);

    /** 全站热门榜 */
    @GetMapping("/song/chart")
    Result<List<Map<String, Object>>> chart(@org.springframework.web.bind.annotation.RequestParam("limit") int limit);
}
