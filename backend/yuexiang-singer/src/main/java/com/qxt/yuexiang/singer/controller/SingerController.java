package com.qxt.yuexiang.singer.controller;

import com.qxt.yuexiang.common.result.Result;
import com.qxt.yuexiang.singer.mapper.SingerMapper;
import com.qxt.yuexiang.singer.service.SingerService;
import com.qxt.yuexiang.singer.vo.SingerApplyDTO;
import com.qxt.yuexiang.singer.vo.SingerFollowVO;
import com.qxt.yuexiang.singer.vo.SingerHomeVO;
import com.qxt.yuexiang.singer.vo.SingerVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 歌手接口：推荐、认证申请与审核（FUN-048/F049）
 */
@RestController
@RequiredArgsConstructor
public class SingerController {

    private final SingerService singerService;
    private final SingerMapper singerMapper;

    /** GET /singer/hot 推荐歌手：按已发布歌曲总播放量（作品热度）倒序 */
    @GetMapping("/singer/hot")
    public Result<List<SingerVO>> hot(@RequestParam(defaultValue = "10") int limit) {
        return Result.success(singerService.hot(limit));
    }

    /** GET /singer/names?ids=1,2,3 批量查歌手名（供 song 服务聚合）
     *  内部接口直接返回裸 Map，不包 Result（Feign 调用方直接解码） */
    @GetMapping("/singer/names")
    public Map<Long, String> names(@RequestParam String ids) {
        List<Long> idList = Arrays.stream(ids.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::valueOf)
                .distinct()
                .collect(Collectors.toList());
        if (idList.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, String> nameMap = new LinkedHashMap<>();
        singerMapper.selectBatchIds(idList).forEach(s -> nameMap.put(s.getId(), s.getName()));
        return nameMap;
    }

    /** GET /singer/search-ids?keyword= 子串模糊搜歌手名，返回命中歌手ID列表
     *  供 song 服务聚合搜索：搜"周杰伦"能出周杰伦的歌（对齐主流音乐 App） */
    @GetMapping("/singer/search-ids")
    public List<Long> searchIds(@RequestParam String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return Collections.emptyList();
        }
        String escaped = keyword.trim()
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return singerMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.qxt.yuexiang.singer.entity.Singer>()
                                .eq(com.qxt.yuexiang.singer.entity.Singer::getStatus, 1)
                                .like(com.qxt.yuexiang.singer.entity.Singer::getName, escaped))
                .stream()
                .map(com.qxt.yuexiang.singer.entity.Singer::getId)
                .collect(Collectors.toList());
    }

    /** GET /singer/by-user?userId= 查用户歌手身份（song 服务发布鉴权用）
     *  内部接口返回裸 Map：{id, name, status}，非歌手返回空 Map */
    @GetMapping("/singer/by-user")
    public Map<String, Object> byUser(@RequestParam Long userId) {
        SingerVO vo = singerService.myApplication(userId);
        if (vo == null) {
            return Collections.emptyMap();
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", vo.getId());
        map.put("name", vo.getName());
        map.put("status", vo.getStatus());
        return map;
    }

    /** GET /singer/user-of?singerId= 查歌手关联用户ID（song 服务审核通知用；内部裸 Map） */
    @GetMapping("/singer/user-of")
    public Map<String, Object> userOf(@RequestParam Long singerId) {
        com.qxt.yuexiang.singer.entity.Singer singer = singerMapper.selectById(singerId);
        if (singer == null || singer.getUserId() == null) {
            return Collections.emptyMap();
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("userId", singer.getUserId());
        return map;
    }

    /** GET /singer/profile/{id} 歌手主页（仅正常状态可见） */
    @GetMapping("/singer/profile/{id}")
    public Result<SingerVO> profile(@PathVariable("id") Long singerId) {
        return Result.success(singerService.profile(singerId));
    }

    // ===== 关注（歌手主页闭环） =====

    /** GET /singer/{id}/home 歌手主页聚合：资料+作品数/总播放/粉丝数+关注态（匿名可看） */
    @GetMapping("/singer/{id}/home")
    public Result<SingerHomeVO> home(@PathVariable("id") Long singerId,
                                     @RequestHeader(value = "X-User-Id", required = false) Long viewerId) {
        return Result.success(singerService.home(singerId, viewerId));
    }

    /** POST /singer/{id}/follow 关注歌手（登录） */
    @PostMapping("/singer/{id}/follow")
    public Result<Void> follow(@RequestHeader("X-User-Id") Long userId,
                               @PathVariable("id") Long singerId) {
        singerService.follow(userId, singerId);
        return Result.success();
    }

    /** DELETE /singer/{id}/follow 取消关注（登录） */
    @DeleteMapping("/singer/{id}/follow")
    public Result<Void> unfollow(@RequestHeader("X-User-Id") Long userId,
                                 @PathVariable("id") Long singerId) {
        singerService.unfollow(userId, singerId);
        return Result.success();
    }

    /** GET /singer/my-follows 我的关注列表（登录） */
    @GetMapping("/singer/my-follows")
    public Result<List<SingerFollowVO>> myFollows(@RequestHeader("X-User-Id") Long userId) {
        return Result.success(singerService.myFollows(userId));
    }

    /** GET /singer/virtual-follows?userId= 某用户关注的虚拟歌手（用户公开主页聚合用，匿名可看） */
    @GetMapping("/singer/virtual-follows")
    public Result<List<SingerFollowVO>> virtualFollows(@RequestParam Long userId) {
        return Result.success(singerService.virtualFollowsOf(userId));
    }

    /** GET /singer/my-follow-ids 我关注的歌手ID集合（播放页关注态回显） */
    @GetMapping("/singer/my-follow-ids")
    public Result<List<Long>> myFollowIds(@RequestHeader("X-User-Id") Long userId) {
        return Result.success(singerService.myFollowIds(userId));
    }

    /** GET /singer/search?keyword= 搜歌手（搜索页"相关歌手"区，匿名可看） */
    @GetMapping("/singer/search")
    public Result<List<SingerVO>> search(@RequestParam String keyword,
                                         @RequestParam(defaultValue = "6") int limit) {
        return Result.success(singerService.searchSingers(keyword, limit));
    }

    /** GET /singer/follower-ids?singerId= 关注某歌手的用户ID（内部接口返回裸 List，favorite 粉丝通知用） */
    @GetMapping("/singer/follower-ids")
    public List<Long> followerIds(@RequestParam Long singerId) {
        return singerService.followerIds(singerId);
    }

    /** POST /singer/apply 提交认证申请（登录） */
    @PostMapping("/singer/apply")
    public Result<Void> apply(@RequestHeader("X-User-Id") Long userId,
                              @RequestBody SingerApplyDTO dto) {
        singerService.apply(userId, dto);
        return Result.success();
    }

    /** GET /singer/my-application 我的申请状态（未申请 data 为 null） */
    @GetMapping("/singer/my-application")
    public Result<SingerVO> myApplication(@RequestHeader("X-User-Id") Long userId) {
        return Result.success(singerService.myApplication(userId));
    }

    /** PUT /singer/my-profile 歌手更新自己的资料（艺名/简介/头像，登录 + SINGER） */
    @PutMapping("/singer/my-profile")
    public Result<Void> updateProfile(@RequestHeader("X-User-Id") Long userId,
                                      @RequestBody SingerApplyDTO dto) {
        singerService.updateProfile(userId, dto);
        return Result.success();
    }

    /** GET /singer/pending 待审核列表（admin 聚合调用） */
    @GetMapping("/singer/pending")
    public Result<List<SingerVO>> pending() {
        return Result.success(singerService.pendingList());
    }

    /** POST /singer/audit/{id} 审核（管理员） */
    @PostMapping("/singer/audit/{id}")
    public Result<Void> audit(@PathVariable("id") Long singerId,
                              @RequestParam boolean approve,
                              @RequestParam(required = false) String reason) {
        singerService.audit(singerId, approve, reason);
        return Result.success();
    }
}
