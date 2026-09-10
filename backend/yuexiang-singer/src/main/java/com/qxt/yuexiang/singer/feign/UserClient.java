package com.qxt.yuexiang.singer.feign;

import com.qxt.yuexiang.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * 用户服务 Feign 客户端：角色升级 + 用户互关（认证歌手的关注/粉丝统一落 user_follow）
 * <p>
 * 注意：/user/{id}/follow、/user/{id}/follow-summary 返回 Result 包装，
 * 声明类型必须带 Result 外壳，否则 Jackson 把整个响应体解成 Boolean/Map 时
 * 关注已落库但响应解码失败 → 前端误报"操作失败"；
 * 其余 follow-ids/follower-ids 为内部接口返回裸 List，保持原样。
 */
@FeignClient(name = "yuexiang-user")
public interface UserClient {

    /** 升级角色：POST /user/role?userId=&role= */
    @PostMapping("/user/role")
    Void upgradeRole(@RequestParam("userId") Long userId, @RequestParam("role") String role);

    /** 关注用户（幂等）：POST /user/{targetId}/follow，data=true=新关注 */
    @PostMapping("/user/{targetId}/follow")
    Result<Boolean> follow(@RequestHeader("X-User-Id") Long userId, @PathVariable("targetId") Long targetId);

    /** 取消关注（幂等）：DELETE /user/{targetId}/follow */
    @DeleteMapping("/user/{targetId}/follow")
    Void unfollow(@RequestHeader("X-User-Id") Long userId, @PathVariable("targetId") Long targetId);

    /** 关注/粉丝计数（data 含 followerCount/following）：GET /user/{targetId}/follow-summary */
    @GetMapping("/user/{targetId}/follow-summary")
    Result<Map<String, Object>> followSummary(@PathVariable("targetId") Long targetId,
                                              @RequestHeader(value = "X-User-Id", required = false) Long viewerId);

    /** 关注该用户的用户ID（粉丝）：GET /user/{targetId}/follower-ids */
    @GetMapping("/user/{targetId}/follower-ids")
    List<Long> followerIdsOf(@PathVariable("targetId") Long targetId);

    /** 该用户关注的用户ID：GET /user/{targetId}/following-ids */
    @GetMapping("/user/{targetId}/following-ids")
    List<Long> followingIdsOf(@PathVariable("targetId") Long targetId);
}
