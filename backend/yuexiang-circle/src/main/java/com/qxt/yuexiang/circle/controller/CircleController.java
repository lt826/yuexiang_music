package com.qxt.yuexiang.circle.controller;

import com.qxt.yuexiang.circle.dto.CircleSaveDTO;
import com.qxt.yuexiang.circle.service.CircleService;
import com.qxt.yuexiang.circle.vo.CircleVO;
import com.qxt.yuexiang.circle.vo.MemberVO;
import com.qxt.yuexiang.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 乐坊圈子接口
 * <p>
 * 浏览类接口（广场/详情/帖子流）网关白名单放行，匿名可看（viewerId 可为空）；
 * 写操作登录后访问。审核接口 /circle/review/** 网关角色矩阵限平台 ADMIN。
 */
@RestController
@RequestMapping("/circle")
@RequiredArgsConstructor
public class CircleController {

    private final CircleService circleService;

    /** GET /circle/square 乐坊广场（匿名可看；viewerId 可选，带 token 时返回加入态） */
    @GetMapping("/square")
    public Result<List<CircleVO>> square(
            @RequestHeader(value = "X-User-Id", required = false) Long viewerId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "50") int limit) {
        return Result.success(circleService.square(viewerId, keyword, limit));
    }

    /** GET /circle/my 我的圈子（登录） */
    @GetMapping("/my")
    public Result<List<CircleVO>> my(@RequestHeader("X-User-Id") Long userId) {
        return Result.success(circleService.my(userId));
    }

    /** GET /circle/{id} 圈子详情（匿名可看；待审核仅圈主/平台管理员可见） */
    @GetMapping("/{id}")
    public Result<CircleVO> detail(
            @RequestHeader(value = "X-User-Id", required = false) Long viewerId,
            @RequestHeader(value = "X-User-Role", required = false) String viewerRole,
            @PathVariable("id") Long circleId) {
        return Result.success(circleService.detail(viewerId, viewerRole, circleId));
    }

    /** POST /circle/create 创建圈子（登录，名称进入审核） */
    @PostMapping("/create")
    public Result<Long> create(@RequestHeader("X-User-Id") Long userId,
                               @RequestBody CircleSaveDTO dto) {
        return Result.success(circleService.create(userId, dto));
    }

    /** PUT /circle/{id} 编辑圈子（圈主/圈管理员/平台管理员；改名重新审核） */
    @PutMapping("/{id}")
    public Result<Void> update(@RequestHeader("X-User-Id") Long userId,
                               @RequestHeader(value = "X-User-Role", required = false) String viewerRole,
                               @PathVariable("id") Long circleId,
                               @RequestBody CircleSaveDTO dto) {
        circleService.update(userId, viewerRole, circleId, dto);
        return Result.success();
    }

    /** DELETE /circle/{id} 解散圈子（仅圈主） */
    @DeleteMapping("/{id}")
    public Result<Void> dissolve(@RequestHeader("X-User-Id") Long userId,
                                 @PathVariable("id") Long circleId) {
        circleService.dissolve(userId, circleId);
        return Result.success();
    }

    /** POST /circle/{id}/join 加入圈子（登录） */
    @PostMapping("/{id}/join")
    public Result<Void> join(@RequestHeader("X-User-Id") Long userId,
                             @PathVariable("id") Long circleId) {
        circleService.join(userId, circleId);
        return Result.success();
    }

    /** POST /circle/{id}/quit 退出圈子（登录；圈主不可退出） */
    @PostMapping("/{id}/quit")
    public Result<Void> quit(@RequestHeader("X-User-Id") Long userId,
                             @PathVariable("id") Long circleId) {
        circleService.quit(userId, circleId);
        return Result.success();
    }

    /** GET /circle/{id}/members 成员列表（圈内成员） */
    @GetMapping("/{id}/members")
    public Result<List<MemberVO>> members(@RequestHeader("X-User-Id") Long userId,
                                          @PathVariable("id") Long circleId) {
        return Result.success(circleService.members(userId, circleId));
    }

    // ===== 审核闭环（/circle/review/**：网关角色矩阵限平台 ADMIN）=====

    /** GET /circle/review/pending 待审核圈子列表 */
    @GetMapping("/review/pending")
    public Result<List<CircleVO>> pendingList() {
        return Result.success(circleService.pendingList());
    }

    /** GET /circle/review/count 待审核数量（管理端红点） */
    @GetMapping("/review/count")
    public Result<Long> pendingCount() {
        return Result.success(circleService.pendingCount());
    }

    /** POST /circle/review/{id}/approve 审核通过 */
    @PostMapping("/review/{id}/approve")
    public Result<Void> approve(@PathVariable("id") Long circleId) {
        circleService.approve(circleId);
        return Result.success();
    }

    /** POST /circle/review/{id}/reject 审核驳回（必填原因） */
    @PostMapping("/review/{id}/reject")
    public Result<Void> reject(@PathVariable("id") Long circleId,
                               @RequestParam String reason) {
        circleService.reject(circleId, reason);
        return Result.success();
    }
}
