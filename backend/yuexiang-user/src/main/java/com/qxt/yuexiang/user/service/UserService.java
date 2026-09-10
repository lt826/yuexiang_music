package com.qxt.yuexiang.user.service;

import com.qxt.yuexiang.user.dto.LoginDTO;
import com.qxt.yuexiang.user.dto.RegisterDTO;
import com.qxt.yuexiang.user.vo.LoginVO;
import com.qxt.yuexiang.user.vo.UserVO;

/**
 * 用户服务接口
 */
public interface UserService {

    /** 注册并发送激活邮件（对齐详细设计 5.1：校验唯一性、BCrypt 加密落库） */
    void register(RegisterDTO dto);

    /** 登录：依次校验账号存在(2001)、状态正常(2005)、密码匹配(2003)，签发 JWT */
    LoginVO login(LoginDTO dto);

    /** 查询用户信息 */
    UserVO getInfo(Long userId);

    /** 用户公开主页（匿名可看）：仅昵称/头像/签名/角色/注册时间，不含用户名与手机邮箱 */
    UserVO getPublicInfo(Long userId);

    /** 关注（登录；不能关注自己；幂等，重复关注返回 false），true=新关注成功 */
    boolean follow(Long userId, Long targetId);

    /** 取消关注（登录；幂等，未关注时静默） */
    void unfollow(Long userId, Long targetId);

    /** 我关注的用户ID集合（按关注时间倒序；singer 服务"我的关注"聚合用，内部接口） */
    java.util.List<Long> followingIds(Long userId);

    /** 关注某用户的用户ID集合（粉丝；新歌通知 MQ 消费用，内部接口） */
    java.util.List<Long> followerIds(Long userId);

    /** 关注/粉丝计数（匿名可看；viewerId 非空时附带查看者是否已关注） */
    com.qxt.yuexiang.user.vo.FollowSummaryVO followSummary(Long targetId, Long viewerId);

    /** 某用户的关注列表（匿名可看；viewerId 非空时附带查看者关注态） */
    java.util.List<com.qxt.yuexiang.user.vo.FollowUserVO> followingList(Long targetId, Long viewerId);

    /** 某用户的粉丝列表（匿名可看；viewerId 非空时附带查看者关注态） */
    java.util.List<com.qxt.yuexiang.user.vo.FollowUserVO> followerList(Long targetId, Long viewerId);

    /** 升级角色（singer 服务认证通过后 Feign 回调；USER→SINGER） */
    void upgradeRole(Long userId, String role);

    /** 全部正常用户ID（message 服务群发用） */
    java.util.List<Long> allNormalIds();

    /** 批量查用户简要信息（nickname/avatar；song 服务评论列表聚合用）→ Map<userId, {nickname, avatar}> */
    java.util.Map<Long, java.util.Map<String, Object>> batchBriefInfo(java.util.List<Long> userIds);

    /** 平台统计：总用户数/歌手数/正常账号数（门户首页展示，admin 服务 Feign 聚合用） */
    java.util.Map<String, Long> stats();

    /** 用户列表（管理端，分页+关键字模糊，返回 items/total） */
    java.util.Map<String, Object> listForAdmin(String keyword, int page, int size);

    /** 管理端新增用户（用户名/密码/邮箱必填） */
    void createByAdmin(java.util.Map<String, Object> body);

    /** 管理端编辑用户（昵称/邮箱/角色/状态等，不传的字段不动） */
    void updateByAdmin(Long userId, java.util.Map<String, Object> body);

    /** 管理端删除用户（逻辑删除） */
    void deleteByAdmin(Long userId);

    /** 禁用/启用用户（管理端） */
    void changeStatus(Long userId, boolean enable);

    /** 修改昵称与签名（个人中心） */
    void updateProfile(Long userId, String nickname, String signature);

    /** 修改头像（个人中心，URL 由 file 服务上传返回） */
    void updateAvatar(Long userId, String avatar);

    /** 修改密码（校验旧密码，个人中心） */
    void changePassword(Long userId, String oldPassword, String newPassword);

    /** 登出：token 摘要写入 Redis 黑名单（TTL=剩余有效期），网关校验实现即时失效 */
    void logout(String token);
}
