package com.qxt.yuexiang.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qxt.yuexiang.common.exception.BusinessException;
import com.qxt.yuexiang.common.result.ResultCode;
import com.qxt.yuexiang.common.utils.JwtUtil;
import com.qxt.yuexiang.user.dto.LoginDTO;
import com.qxt.yuexiang.user.dto.RegisterDTO;
import com.qxt.yuexiang.user.entity.User;
import com.qxt.yuexiang.user.entity.UserFollow;
import com.qxt.yuexiang.user.mapper.UserFollowMapper;
import com.qxt.yuexiang.user.mapper.UserMapper;
import com.qxt.yuexiang.user.service.UserService;
import com.qxt.yuexiang.user.vo.FollowSummaryVO;
import com.qxt.yuexiang.user.vo.FollowUserVO;
import com.qxt.yuexiang.user.vo.LoginVO;
import com.qxt.yuexiang.user.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final UserFollowMapper userFollowMapper;
    private final StringRedisTemplate redisTemplate;

    /** BCrypt 实例无状态，可复用 */
    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    /** JWT 黑名单 key 前缀，与网关 AuthGlobalFilter 保持一致 */
    private static final String BLACKLIST_PREFIX = "auth:blacklist:";

    @Override
    public void register(RegisterDTO dto) {
        // 用户名唯一性：唯一约束兜底，先查给出友好错误码 2002
        Long usernameCount = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, dto.getUsername()));
        if (usernameCount > 0) {
            throw new BusinessException(ResultCode.USERNAME_EXISTED);
        }
        // 邮箱唯一性
        Long emailCount = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getEmail, dto.getEmail()));
        if (emailCount > 0) {
            throw new BusinessException("邮箱已被注册");
        }

        User user = new User();
        user.setUsername(dto.getUsername());
        user.setPassword(PASSWORD_ENCODER.encode(dto.getPassword()));
        user.setEmail(dto.getEmail());
        // 昵称与用户名分离：注册不设置昵称（为空时全局展示回退用户名），用户可在个人中心修改
        user.setRole("USER");
        user.setStatus(1);
        userMapper.insert(user);
        // 邮件激活（F001-F002）接入前先直接放行，日志留痕
        log.info("用户注册成功: id={}, username={}", user.getId(), user.getUsername());
    }

    @Override
    public LoginVO login(LoginDTO dto) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, dto.getUsername()));
        // 2001 用户不存在
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }
        // 2005 用户已被禁用
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException("用户已被禁用");
        }
        // 2003 密码错误
        if (!PASSWORD_ENCODER.matches(dto.getPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.PASSWORD_ERROR);
        }

        LoginVO vo = new LoginVO();
        vo.setUserId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setAvatar(user.getAvatar());
        vo.setRole(user.getRole());
        vo.setToken(generateToken(user));
        return vo;
    }

    @Override
    public UserVO getInfo(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        return vo;
    }

    @Override
    public void upgradeRole(Long userId, String role) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }
        // 角色只升不降（USER→SINGER）；ADMIN 不允许经此接口改动
        if ("ADMIN".equals(user.getRole()) || user.getRole().equals(role)) {
            return;
        }
        String oldRole = user.getRole();
        user.setRole(role);
        userMapper.updateById(user);
        log.info("用户角色升级: userId={}, {} → {}", userId, oldRole, role);
    }

    @Override
    public java.util.List<Long> allNormalIds() {
        return userMapper.selectList(
                        new LambdaQueryWrapper<User>().eq(User::getStatus, 1))
                .stream().map(User::getId).collect(java.util.stream.Collectors.toList());
    }

    @Override
    public java.util.Map<Long, java.util.Map<String, Object>> batchBriefInfo(java.util.List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        return userMapper.selectBatchIds(userIds).stream()
                .collect(java.util.stream.Collectors.toMap(
                        User::getId,
                        u -> {
                            java.util.Map<String, Object> info = new HashMap<>();
                            // 昵称为空回退用户名（评论区展示名）
                            info.put("nickname", u.getNickname() != null ? u.getNickname() : u.getUsername());
                            info.put("avatar", u.getAvatar());
                            return info;
                        },
                        (a, b) -> a));
    }

    @Override
    public java.util.Map<String, Long> stats() {
        // selectCount 带 @TableLogic 自动过滤已删除
        java.util.Map<String, Long> stats = new HashMap<>();
        stats.put("userCount", userMapper.selectCount(null));
        stats.put("singerCount", userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getRole, "SINGER")));
        stats.put("activeCount", userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getStatus, 1)));
        return stats;
    }

    @Override
    public java.util.Map<String, Object> listForAdmin(String keyword, int page, int size) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .orderByDesc(User::getId);
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim()
                    .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            wrapper.and(w -> w.like(User::getUsername, kw)
                    .or().like(User::getNickname, kw)
                    .or().like(User::getEmail, kw));
        }
        // MyBatis-Plus 分页：当前页从 1 起
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<User> pager =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(Math.max(1, page), Math.max(1, size));
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<User> result = userMapper.selectPage(pager, wrapper);
        java.util.Map<String, Object> data = new HashMap<>();
        data.put("items", result.getRecords().stream().map(user -> {
            UserVO vo = new UserVO();
            BeanUtils.copyProperties(user, vo);
            return vo;
        }).collect(java.util.stream.Collectors.toList()));
        data.put("total", result.getTotal());
        return data;
    }

    @Override
    public void createByAdmin(java.util.Map<String, Object> body) {
        String username = str(body.get("username"));
        String password = str(body.get("password"));
        String email = str(body.get("email"));
        if (username.isEmpty() || password.isEmpty() || email.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "用户名、密码与邮箱均为必填");
        }
        if (userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, username)) > 0) {
            throw new BusinessException(ResultCode.USERNAME_EXISTED);
        }
        if (userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getEmail, email)) > 0) {
            throw new BusinessException("邮箱已被注册");
        }
        User user = new User();
        user.setUsername(username);
        user.setPassword(PASSWORD_ENCODER.encode(password));
        user.setEmail(email);
        // 昵称与用户名分离：管理端创建也不强制昵称=用户名，留空由展示层回退
        user.setNickname(str(body.get("nickname")).isEmpty() ? null : str(body.get("nickname")));
        user.setPhone(str(body.get("phone")));
        user.setRole(strOr(body.get("role"), "USER"));
        user.setStatus(1);
        userMapper.insert(user);
        log.info("管理端新增用户: id={}, username={}, 操作后角色={}", user.getId(), username, user.getRole());
    }

    @Override
    public void updateByAdmin(Long userId, java.util.Map<String, Object> body) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }
        // 不可将管理员降级/禁用，避免锁死后台
        boolean selfIsAdmin = "ADMIN".equals(user.getRole());
        if (body.containsKey("nickname") && !str(body.get("nickname")).isEmpty()) {
            user.setNickname(str(body.get("nickname")));
        }
        if (body.containsKey("email") && !str(body.get("email")).isEmpty()) {
            String email = str(body.get("email"));
            Long dup = userMapper.selectCount(new LambdaQueryWrapper<User>()
                    .eq(User::getEmail, email).ne(User::getId, userId));
            if (dup > 0) {
                throw new BusinessException("邮箱已被其他账号使用");
            }
            user.setEmail(email);
        }
        if (body.containsKey("phone")) {
            user.setPhone(str(body.get("phone")));
        }
        if (body.containsKey("role") && !str(body.get("role")).isEmpty()) {
            if (selfIsAdmin && !"ADMIN".equals(str(body.get("role")))) {
                throw new BusinessException("不能修改管理员的角色");
            }
            user.setRole(str(body.get("role")));
        }
        if (body.containsKey("status") && body.get("status") != null) {
            if (selfIsAdmin) {
                throw new BusinessException("不能禁用管理员账号");
            }
            user.setStatus(Integer.valueOf(body.get("status").toString()));
        }
        // 可选重置密码（留空不重置）
        if (body.containsKey("password") && !str(body.get("password")).isEmpty()) {
            String pwd = str(body.get("password"));
            if (pwd.length() < 6 || pwd.length() > 20) {
                throw new BusinessException("密码长度需在 6-20 位之间");
            }
            user.setPassword(PASSWORD_ENCODER.encode(pwd));
        }
        userMapper.updateById(user);
        log.info("管理端编辑用户: userId={}", userId);
    }

    @Override
    public void deleteByAdmin(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }
        if ("ADMIN".equals(user.getRole())) {
            throw new BusinessException("不能删除管理员账号");
        }
        userMapper.deleteById(userId);
        log.info("管理端删除用户（逻辑删除）: userId={}, username={}", userId, user.getUsername());
    }

    /** Map 取字符串：null 安全，去空白 */
    private static String str(Object v) {
        return v == null ? "" : v.toString().trim();
    }

    private static String strOr(Object v, String fallback) {
        String s = str(v);
        return s.isEmpty() ? fallback : s;
    }

    @Override
    public void changeStatus(Long userId, boolean enable) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }
        // 管理员自身不可禁用，避免锁死后台
        if ("ADMIN".equals(user.getRole())) {
            throw new BusinessException("不能禁用管理员账号");
        }
        user.setStatus(enable ? 1 : 0);
        userMapper.updateById(user);
        log.info("用户状态变更: userId={}, → {}", userId, enable ? "正常" : "禁用");
    }

    @Override
    public void updateProfile(Long userId, String nickname, String signature) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }
        if (nickname != null) {
            String nick = nickname.trim();
            if (nick.isEmpty() || nick.length() > 50) {
                throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "昵称需为 1-50 个字符");
            }
            user.setNickname(nick);
        }
        if (signature != null) {
            user.setSignature(signature.trim());
        }
        userMapper.updateById(user);
        log.info("用户资料更新: userId={}", userId);
    }

    /** 用户公开主页：仅暴露昵称/头像/签名/角色/注册时间，不含用户名与手机邮箱 */
    @Override
    public UserVO getPublicInfo(Long userId) {
        User user = userMapper.selectById(userId);
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        String nick = user.getNickname();
        vo.setNickname(nick == null || nick.isBlank() ? "用户" + user.getId() : nick);
        vo.setAvatar(user.getAvatar());
        vo.setSignature(user.getSignature());
        vo.setRole(user.getRole());
        vo.setCreateTime(user.getCreateTime());
        return vo;
    }

    // ===== 用户互关（关注/粉丝） =====

    @Override
    public boolean follow(Long userId, Long targetId) {
        if (userId.equals(targetId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "不能关注自己");
        }
        User target = userMapper.selectById(targetId);
        if (target == null || target.getStatus() == null || target.getStatus() != 1) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }
        Long existed = userFollowMapper.selectCount(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowerId, userId)
                .eq(UserFollow::getFollowingId, targetId));
        if (existed > 0) {
            return false;
        }
        try {
            UserFollow follow = new UserFollow();
            follow.setFollowerId(userId);
            follow.setFollowingId(targetId);
            userFollowMapper.insert(follow);
        } catch (DuplicateKeyException e) {
            // 并发重复关注：唯一约束兜底
            log.info("重复关注被唯一约束拦截: follower={}, following={}", userId, targetId);
            return false;
        }
        log.info("关注成功: follower={}, following={}", userId, targetId);
        return true;
    }

    @Override
    public void unfollow(Long userId, Long targetId) {
        userFollowMapper.delete(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowerId, userId)
                .eq(UserFollow::getFollowingId, targetId));
        log.info("取消关注: follower={}, following={}", userId, targetId);
    }

    @Override
    public List<Long> followingIds(Long userId) {
        return userFollowMapper.selectList(new LambdaQueryWrapper<UserFollow>()
                        .select(UserFollow::getFollowingId)
                        .eq(UserFollow::getFollowerId, userId)
                        .orderByDesc(UserFollow::getId))
                .stream().map(UserFollow::getFollowingId).collect(Collectors.toList());
    }

    @Override
    public List<Long> followerIds(Long userId) {
        return userFollowMapper.selectList(new LambdaQueryWrapper<UserFollow>()
                        .select(UserFollow::getFollowerId)
                        .eq(UserFollow::getFollowingId, userId)
                        .orderByDesc(UserFollow::getId))
                .stream().map(UserFollow::getFollowerId).collect(Collectors.toList());
    }

    @Override
    public FollowSummaryVO followSummary(Long targetId, Long viewerId) {
        FollowSummaryVO vo = new FollowSummaryVO();
        vo.setUserId(targetId);
        vo.setFollowingCount(userFollowMapper.selectCount(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowerId, targetId)));
        vo.setFollowerCount(userFollowMapper.selectCount(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowingId, targetId)));
        if (viewerId != null && !viewerId.equals(targetId)) {
            vo.setFollowing(isFollowing(viewerId, targetId));
        }
        return vo;
    }

    @Override
    public List<FollowUserVO> followingList(Long targetId, Long viewerId) {
        List<UserFollow> follows = userFollowMapper.selectList(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowerId, targetId)
                .orderByDesc(UserFollow::getId)
                .last("LIMIT 100"));
        List<Long> ids = follows.stream().map(UserFollow::getFollowingId).collect(Collectors.toList());
        return toFollowUserVOs(ids, viewerId);
    }

    @Override
    public List<FollowUserVO> followerList(Long targetId, Long viewerId) {
        List<UserFollow> follows = userFollowMapper.selectList(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowingId, targetId)
                .orderByDesc(UserFollow::getId)
                .last("LIMIT 100"));
        List<Long> ids = follows.stream().map(UserFollow::getFollowerId).collect(Collectors.toList());
        return toFollowUserVOs(ids, viewerId);
    }

    /** 批量组装列表条目：昵称兜底"用户+ID"，注销/被禁用户剔除；附带查看者关注态 */
    private List<FollowUserVO> toFollowUserVOs(List<Long> userIds, Long viewerId) {
        if (userIds.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, User> users = userMapper.selectBatchIds(userIds).stream()
                .filter(u -> u.getStatus() == null || u.getStatus() == 1)
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
        Set<Long> viewerFollowed = viewerId == null ? Set.of()
                : userFollowMapper.selectList(new LambdaQueryWrapper<UserFollow>()
                        .eq(UserFollow::getFollowerId, viewerId)
                        .in(UserFollow::getFollowingId, userIds))
                .stream().map(UserFollow::getFollowingId).collect(Collectors.toSet());
        // 保序输出（关注/粉丝列表按时间倒序）
        List<FollowUserVO> result = new ArrayList<>();
        for (Long id : userIds) {
            User u = users.get(id);
            if (u == null) {
                continue;
            }
            FollowUserVO vo = new FollowUserVO();
            vo.setId(u.getId());
            String nick = u.getNickname();
            vo.setNickname(nick == null || nick.isBlank() ? "用户" + u.getId() : nick);
            vo.setAvatar(u.getAvatar());
            vo.setSignature(u.getSignature());
            vo.setFollowed(viewerId == null ? null : viewerFollowed.contains(u.getId()));
            vo.setRole(u.getRole());
            result.add(vo);
        }
        return result;
    }

    private boolean isFollowing(Long followerId, Long followingId) {
        return userFollowMapper.selectCount(new LambdaQueryWrapper<UserFollow>()
                .eq(UserFollow::getFollowerId, followerId)
                .eq(UserFollow::getFollowingId, followingId)) > 0;
    }

    @Override
    public void updateAvatar(Long userId, String avatar) {
        if (avatar == null || avatar.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "请先上传头像图片");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }
        user.setAvatar(avatar);
        userMapper.updateById(user);
        log.info("用户头像更新: userId={}", userId);
    }

    @Override
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST);
        }
        if (!PASSWORD_ENCODER.matches(oldPassword, user.getPassword())) {
            throw new BusinessException("原密码不正确");
        }
        if (newPassword == null || newPassword.length() < 6 || newPassword.length() > 20) {
            throw new BusinessException("新密码长度需在 6-20 位之间");
        }
        user.setPassword(PASSWORD_ENCODER.encode(newPassword));
        userMapper.updateById(user);
        log.info("用户密码修改: userId={}", userId);
    }

    /** JWT 载荷：userId、username、role（对齐详细设计 5.2） */
    private String generateToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("username", user.getUsername());
        claims.put("role", user.getRole());
        return JwtUtil.generateToken(claims);
    }

    /**
     * 登出：token 的 SHA-256 摘要写入 Redis 黑名单，TTL = token 剩余有效期
     * （过期 token 自然出名单，避免黑名单无限膨胀）；网关鉴权时查询拦截。
     */
    @Override
    public void logout(String token) {
        try {
            Date expiration = JwtUtil.parseToken(token).getExpiration();
            long ttlMs = expiration.getTime() - System.currentTimeMillis();
            if (ttlMs <= 0) {
                return; // 已过期 token 无需拉黑
            }
            redisTemplate.opsForValue().set(
                    BLACKLIST_PREFIX + sha256(token), "1", Duration.ofMillis(ttlMs));
            log.info("登出成功，token 已入黑名单");
        } catch (Exception e) {
            // 黑名单写入失败不阻塞登出（token 自然过期兜底，7 天内失效）
            log.warn("token 黑名单写入失败（自然过期兜底）: {}", e.getMessage());
        }
    }

    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
