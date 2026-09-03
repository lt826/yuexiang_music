package com.qxt.yuexiang.admin.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qxt.yuexiang.admin.dto.AdminLoginDTO;
import com.qxt.yuexiang.admin.entity.Admin;
import com.qxt.yuexiang.admin.entity.Banner;
import com.qxt.yuexiang.admin.feign.AdminFeignClients;
import com.qxt.yuexiang.admin.mapper.AdminMapper;
import com.qxt.yuexiang.admin.mapper.BannerMapper;
import com.qxt.yuexiang.admin.vo.AdminLoginVO;
import com.qxt.yuexiang.admin.vo.BannerVO;
import com.qxt.yuexiang.common.exception.BusinessException;
import com.qxt.yuexiang.common.result.Result;
import com.qxt.yuexiang.common.result.ResultCode;
import com.qxt.yuexiang.common.utils.JwtUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 后台管理：登录 + 用户/歌手/歌曲管理与公告（FUN-12/13，聚合各业务服务）
 */
@Slf4j
@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminMapper adminMapper;
    private final BannerMapper bannerMapper;
    private final AdminFeignClients.UserClient userClient;
    private final AdminFeignClients.SingerClient singerClient;
    private final AdminFeignClients.SongClient songClient;
    private final AdminFeignClients.MessageClient messageClient;
    private final AdminFeignClients.PlaylistClient playlistClient;

    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    /** POST /admin/login 返回 token 与管理员信息（role 固定 ADMIN） */
    @PostMapping("/login")
    public Result<AdminLoginVO> login(@Valid @RequestBody AdminLoginDTO dto) {
        Admin admin = adminMapper.selectOne(
                new LambdaQueryWrapper<Admin>().eq(Admin::getUsername, dto.getUsername()));
        // 2001 账号不存在
        if (admin == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }
        // 2003 密码错误
        if (!PASSWORD_ENCODER.matches(dto.getPassword(), admin.getPassword())) {
            throw new BusinessException(ResultCode.PASSWORD_ERROR);
        }

        Map<String, Object> claims = new HashMap<>();
        claims.put("adminId", admin.getId());
        claims.put("username", admin.getUsername());
        claims.put("role", "ADMIN");

        AdminLoginVO vo = new AdminLoginVO();
        vo.setAdminId(admin.getId());
        vo.setUsername(admin.getUsername());
        vo.setNickname(admin.getNickname());
        vo.setRole("ADMIN");
        vo.setToken(JwtUtil.generateToken(claims));
        return Result.success(vo);
    }

    // ===== 平台统计（门户首页） =====

    /**
     * GET /admin/stats 平台实时统计（网关白名单放行，匿名可访）
     * 聚合 user/song 服务计数，供门户首页展示注册用户、驻站歌手、歌曲与播放量
     */
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        Map<String, Object> stats = new HashMap<>();
        try {
            stats.putAll(userClient.stats());
        } catch (Exception e) {
            log.warn("聚合用户统计失败: {}", e.getMessage());
        }
        try {
            stats.putAll(songClient.stats());
        } catch (Exception e) {
            log.warn("聚合歌曲统计失败: {}", e.getMessage());
        }
        return Result.success(stats);
    }

    // ===== 用户管理 =====

    /** GET /admin/user/list 用户列表（分页+关键字，转发 user 服务） */
    @GetMapping("/user/list")
    public Result<Object> userList(@RequestParam(required = false) String keyword,
                                   @RequestParam(defaultValue = "1") int page,
                                   @RequestParam(defaultValue = "10") int size) {
        return Result.success(userClient.list(keyword, page, size).get("data"));
    }

    /** POST /admin/user/create 新增用户（转发 user 服务） */
    @PostMapping("/user/create")
    public Result<Void> userCreate(@RequestBody Map<String, Object> body) {
        userClient.create(body);
        return Result.success();
    }

    /** PUT /admin/user/update 编辑用户（转发 user 服务） */
    @PutMapping("/user/update")
    public Result<Void> userUpdate(@RequestParam Long userId,
                                   @RequestBody Map<String, Object> body) {
        userClient.update(userId, body);
        return Result.success();
    }

    /** DELETE /admin/user/delete 删除用户（转发 user 服务，逻辑删除） */
    @DeleteMapping("/user/delete")
    public Result<Void> userDelete(@RequestParam Long userId) {
        userClient.delete(userId);
        return Result.success();
    }

    /** POST /admin/user/status 禁用/启用（转发 user 服务） */
    @PostMapping("/user/status")
    public Result<Void> userStatus(@RequestParam Long userId, @RequestParam boolean enable) {
        userClient.changeStatus(userId, enable);
        return Result.success();
    }

    // ===== 歌手审核 =====

    /** GET /admin/singer/pending 待审核歌手（转发 singer 服务） */
    @GetMapping("/singer/pending")
    public Result<Object> singerPending() {
        return Result.success(singerClient.pending().get("data"));
    }

    /** POST /admin/singer/audit/{id} 审核歌手（转发 singer 服务） */
    @PostMapping("/singer/audit/{id}")
    public Result<Void> singerAudit(@PathVariable("id") Long singerId,
                                    @RequestParam boolean approve,
                                    @RequestParam(required = false) String reason) {
        singerClient.audit(singerId, approve, reason);
        return Result.success();
    }

    // ===== 歌曲审核 =====

    /** GET /admin/song/pending 待审核歌曲（转发 song 服务） */
    @GetMapping("/song/pending")
    public Result<Object> songPending() {
        return Result.success(songClient.pending().get("data"));
    }

    /** POST /admin/song/audit/{id} 审核歌曲（转发 song 服务） */
    @PostMapping("/song/audit/{id}")
    public Result<Void> songAudit(@PathVariable("id") Long songId,
                                  @RequestParam boolean approve,
                                  @RequestParam(required = false) String reason) {
        songClient.audit(songId, approve, reason);
        return Result.success();
    }

    /** GET /admin/song/list 管理端全量歌曲（分页+关键字，转发 song 服务） */
    @GetMapping("/song/list")
    public Result<Object> songList(@RequestParam(required = false) String keyword,
                                   @RequestParam(defaultValue = "1") int page,
                                   @RequestParam(defaultValue = "10") int size) {
        return Result.success(songClient.adminList(keyword, page, size).get("data"));
    }

    /** POST /admin/song/offline 管理员强制下架（转发 song 服务，通知歌手） */
    @PostMapping("/song/offline")
    public Result<Void> songOffline(@RequestParam Long songId,
                                    @RequestParam(required = false) String reason) {
        songClient.adminOffline(songId, reason);
        return Result.success();
    }

    // ===== 公告 =====

    /** POST /admin/broadcast 群发系统公告（转发 message 服务） */
    @PostMapping("/broadcast")
    public Result<Void> broadcast(@RequestParam String title, @RequestParam String content) {
        messageClient.broadcast(title, content);
        return Result.success();
    }

    // ===== 歌单管理（转发 playlist 服务 /playlist/admin/**，官方歌单含电台节目单）=====

    /** GET /admin/playlist/list 全部歌单（官方 + 用户公开） */
    @GetMapping("/playlist/list")
    public Result<Object> playlistList() {
        return Result.success(playlistClient.adminList().get("data"));
    }

    /** GET /admin/playlist/{id}/songs 歌单内歌曲（按节目顺序） */
    @GetMapping("/playlist/{id}/songs")
    public Result<Object> playlistSongs(@PathVariable("id") Long playlistId) {
        return Result.success(playlistClient.adminSongs(playlistId).get("data"));
    }

    /** POST /admin/playlist/{id}/songs/{songId} 加歌（官方歌单/电台节目单维护） */
    @PostMapping("/playlist/{id}/songs/{songId}")
    public Result<Void> playlistAddSong(@PathVariable("id") Long playlistId,
                                        @PathVariable("songId") Long songId) {
        playlistClient.adminAddSong(playlistId, songId);
        return Result.success();
    }

    /** DELETE /admin/playlist/{id}/songs/{songId} 删歌 */
    @DeleteMapping("/playlist/{id}/songs/{songId}")
    public Result<Void> playlistRemoveSong(@PathVariable("id") Long playlistId,
                                           @PathVariable("songId") Long songId) {
        playlistClient.adminRemoveSong(playlistId, songId);
        return Result.success();
    }

    /** GET /admin/banner/active 当前上架的轮播图（小程序首页用，匿名）
     *  网关白名单放行；管理端维护入口已下线，仅保留只读数据源 */
    @GetMapping("/banner/active")
    public Result<List<BannerVO>> bannerActive() {
        List<BannerVO> vos = bannerMapper.selectList(
                        new LambdaQueryWrapper<Banner>()
                                .eq(Banner::getStatus, 1)
                                .orderByAsc(Banner::getSortOrder)
                                .orderByDesc(Banner::getId))
                .stream().map(b -> {
                    BannerVO vo = new BannerVO();
                    org.springframework.beans.BeanUtils.copyProperties(b, vo);
                    return vo;
                }).collect(java.util.stream.Collectors.toList());
        return Result.success(vos);
    }

    // ===== 管理员收件箱（消息中心：待审通知，与用户消息物理隔离） =====

    /** GET /admin/message/list 管理员消息列表（分页+未读） */
    @GetMapping("/message/list")
    public Result<Object> messageList(@RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        return Result.success(messageClient.adminList(page, size).get("data"));
    }

    /** GET /admin/message/unread 管理员未读数 */
    @GetMapping("/message/unread")
    public Result<Long> messageUnread() {
        Object data = messageClient.adminUnread().get("data");
        return Result.success(data == null ? 0L : Long.valueOf(data.toString()));
    }

    /** POST /admin/message/read/{id} 单条已读 */
    @PostMapping("/message/read/{messageId}")
    public Result<Void> messageRead(@PathVariable("messageId") Long messageId) {
        messageClient.adminRead(messageId);
        return Result.success();
    }

    /** POST /admin/message/read/all 全部已读 */
    @PostMapping("/message/read/all")
    public Result<Void> messageReadAll() {
        messageClient.adminReadAll();
        return Result.success();
    }
}
