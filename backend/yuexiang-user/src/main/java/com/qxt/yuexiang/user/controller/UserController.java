package com.qxt.yuexiang.user.controller;

import com.qxt.yuexiang.common.result.Result;
import com.qxt.yuexiang.user.dto.LoginDTO;
import com.qxt.yuexiang.user.dto.RegisterDTO;
import com.qxt.yuexiang.user.service.UserService;
import com.qxt.yuexiang.user.vo.FollowSummaryVO;
import com.qxt.yuexiang.user.vo.FollowUserVO;
import com.qxt.yuexiang.user.vo.LoginVO;
import com.qxt.yuexiang.user.vo.UserVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 登录注册接口（对齐详细设计 5.4 接口清单）
 */
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** POST /user/register 注册并发送激活邮件 */
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterDTO dto) {
        userService.register(dto);
        return Result.success();
    }

    /** POST /user/login 返回 token 与用户信息 */
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
        return Result.success(userService.login(dto));
    }

    /** POST /user/logout 登出：token 写入 Redis 黑名单，网关校验即时失效 */
    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader("Authorization") String token) {
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        userService.logout(token);
        return Result.success();
    }

    /** GET /user/info 查询基本信息（网关校验 JWT 后透传 X-User-Id） */
    @GetMapping("/info")
    public Result<UserVO> info(@RequestHeader("X-User-Id") Long userId) {
        return Result.success(userService.getInfo(userId));
    }

    /** GET /user/{id} 用户公开主页（网关白名单匿名可看）：昵称/头像/签名/角色/注册时间 */
    @GetMapping("/{id}")
    public Result<UserVO> publicHome(@PathVariable("id") Long id) {
        return Result.success(userService.getPublicInfo(id));
    }

    // ===== 用户互关（关注/粉丝） =====

    /** POST /user/{id}/follow 关注（登录；幂等，重复关注返回 false） */
    @PostMapping("/{id}/follow")
    public Result<Boolean> follow(@RequestHeader("X-User-Id") Long userId,
                                  @PathVariable("id") Long targetId) {
        return Result.success(userService.follow(userId, targetId));
    }

    /** DELETE /user/{id}/follow 取消关注（登录；幂等） */
    @DeleteMapping("/{id}/follow")
    public Result<Void> unfollow(@RequestHeader("X-User-Id") Long userId,
                                 @PathVariable("id") Long targetId) {
        userService.unfollow(userId, targetId);
        return Result.success();
    }

    /** GET /user/{id}/following-ids 我关注的用户ID（内部接口，singer 服务聚合"我的关注"用） */
    @GetMapping("/{id}/following-ids")
    public List<Long> followingIds(@PathVariable("id") Long id) {
        return userService.followingIds(id);
    }

    /** GET /user/{id}/follower-ids 关注该用户的用户ID（内部接口，新歌通知 MQ 消费用） */
    @GetMapping("/{id}/follower-ids")
    public List<Long> followerIds(@PathVariable("id") Long id) {
        return userService.followerIds(id);
    }

    /** GET /user/{id}/follow-summary 关注/粉丝计数（匿名可看；带 token 附带查看者关注态） */
    @GetMapping("/{id}/follow-summary")
    public Result<FollowSummaryVO> followSummary(
            @RequestHeader(value = "X-User-Id", required = false) Long viewerId,
            @PathVariable("id") Long targetId) {
        return Result.success(userService.followSummary(targetId, viewerId));
    }

    /** GET /user/{id}/following 关注列表（匿名可看；条目可点击进对方主页） */
    @GetMapping("/{id}/following")
    public Result<List<FollowUserVO>> followingList(
            @RequestHeader(value = "X-User-Id", required = false) Long viewerId,
            @PathVariable("id") Long targetId) {
        return Result.success(userService.followingList(targetId, viewerId));
    }

    /** GET /user/{id}/followers 粉丝列表（匿名可看；条目可点击进对方主页） */
    @GetMapping("/{id}/followers")
    public Result<List<FollowUserVO>> followerList(
            @RequestHeader(value = "X-User-Id", required = false) Long viewerId,
            @PathVariable("id") Long targetId) {
        return Result.success(userService.followerList(targetId, viewerId));
    }

    /** POST /user/role 升级角色（内部接口：singer 服务认证通过后 Feign 回调） */
    @PostMapping("/role")
    public Result<Void> upgradeRole(@RequestParam("userId") Long userId,
                                    @RequestParam("role") String role) {
        userService.upgradeRole(userId, role);
        return Result.success();
    }

    /** GET /user/all-ids 全部正常用户ID（message 服务群发公告用；内部接口返回裸 List） */
    @GetMapping("/all-ids")
    public List<Long> allIds() {
        return userService.allNormalIds();
    }

    /** GET /user/batch-info?ids=1,2,3 批量查用户简要信息（song 服务评论列表聚合昵称/头像用；内部接口返回裸 Map） */
    @GetMapping("/batch-info")
    public Map<Long, Map<String, Object>> batchInfo(@RequestParam("ids") String ids) {
        List<Long> userIds = java.util.Arrays.stream(ids.split(","))
                .map(String::trim).filter(s -> !s.isEmpty())
                .map(Long::valueOf).distinct().limit(200).toList();
        return userService.batchBriefInfo(userIds);
    }

    /** GET /user/stats 平台用户统计（admin 服务聚合门户展示用；内部接口返回裸 Map） */
    @GetMapping("/stats")
    public Map<String, Long> stats() {
        return userService.stats();
    }

    /** GET /user/admin/list 用户列表（管理端：分页+关键字，admin 服务 Feign 转发） */
    @GetMapping("/admin/list")
    public Result<Map<String, Object>> adminList(@RequestParam(required = false) String keyword,
                                                 @RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "10") int size) {
        return Result.success(userService.listForAdmin(keyword, page, size));
    }

    /** POST /user/admin/create 新增用户（管理端） */
    @PostMapping("/admin/create")
    public Result<Void> adminCreate(@RequestBody Map<String, Object> body) {
        userService.createByAdmin(body);
        return Result.success();
    }

    /** PUT /user/admin/update 编辑用户（管理端） */
    @PutMapping("/admin/update")
    public Result<Void> adminUpdate(@RequestParam Long userId,
                                    @RequestBody Map<String, Object> body) {
        userService.updateByAdmin(userId, body);
        return Result.success();
    }

    /** DELETE /user/admin/delete 删除用户（管理端，逻辑删除） */
    @DeleteMapping("/admin/delete")
    public Result<Void> adminDelete(@RequestParam Long userId) {
        userService.deleteByAdmin(userId);
        return Result.success();
    }

    /** POST /user/admin/status 禁用/启用（管理端） */
    @PostMapping("/admin/status")
    public Result<Void> adminStatus(@RequestParam Long userId, @RequestParam boolean enable) {
        userService.changeStatus(userId, enable);
        return Result.success();
    }

    // ===== 个人中心（FUN-10） =====

    /** PUT /user/profile 修改昵称与签名 */
    @PutMapping("/profile")
    public Result<UserVO> updateProfile(@RequestHeader("X-User-Id") Long userId,
                                        @RequestBody Map<String, String> body) {
        userService.updateProfile(userId, body.get("nickname"), body.get("signature"));
        return Result.success(userService.getInfo(userId));
    }

    /** PUT /user/avatar 修改头像（URL 由 file 服务上传返回） */
    @PutMapping("/avatar")
    public Result<UserVO> updateAvatar(@RequestHeader("X-User-Id") Long userId,
                                       @RequestBody Map<String, String> body) {
        userService.updateAvatar(userId, body.get("avatar"));
        return Result.success(userService.getInfo(userId));
    }

    /** POST /user/password 修改密码（校验旧密码；改完前端需重新登录） */
    @PostMapping("/password")
    public Result<Void> changePassword(@RequestHeader("X-User-Id") Long userId,
                                       @RequestBody Map<String, String> body) {
        userService.changePassword(userId, body.get("oldPassword"), body.get("newPassword"));
        return Result.success();
    }
}
