package com.qxt.yuexiang.singer.service;

import com.qxt.yuexiang.singer.vo.SingerApplyDTO;
import com.qxt.yuexiang.singer.vo.SingerFollowVO;
import com.qxt.yuexiang.singer.vo.SingerHomeVO;
import com.qxt.yuexiang.singer.vo.SingerVO;

import java.util.List;

/**
 * 歌手服务接口
 */
public interface SingerService {

    /** 热门歌手（已有接口的 service 化） */
    List<SingerVO> hot(int limit);

    /** 认证申请：用户提交艺名与简介，落一条待审核记录（FUN-048 前置） */
    void apply(Long userId, SingerApplyDTO dto);

    /** 我的申请状态：未申请返回 null，已申请返回歌手记录（含状态） */
    SingerVO myApplication(Long userId);

    /** 歌手更新自己的资料（艺名/简介/头像）：艺名即歌曲展示的发布者名字 */
    void updateProfile(Long userId, SingerApplyDTO dto);

    /** 歌手主页（对外展示：正常状态歌手） */
    SingerVO profile(Long singerId);

    /** 待审核列表（admin 聚合用） */
    List<SingerVO> pendingList();

    /** 审核：通过则置正常并回调 user 服务升级角色，驳回记原因 */
    void audit(Long singerId, boolean approve, String reason);

    // ===== 关注（歌手主页闭环） =====

    /** 歌手主页聚合：基本资料 + 作品数/总播放/粉丝数 + 当前用户关注态（viewerId 可空=匿名） */
    SingerHomeVO home(Long singerId, Long viewerId);

    /** 关注歌手（登录；不能关注自己；重复关注报错） */
    void follow(Long userId, Long singerId);

    /** 取消关注（登录） */
    void unfollow(Long userId, Long singerId);

    /** 我的关注列表（歌手简要资料 + 关注时间，最近关注在前） */
    List<SingerFollowVO> myFollows(Long userId);

    /** 某用户关注的虚拟歌手列表（用户公开主页聚合用；仅本地关注表，认证歌手在 user_follow） */
    List<SingerFollowVO> virtualFollowsOf(Long userId);

    /** 我关注的歌手ID集合（播放页关注态回显用） */
    List<Long> myFollowIds(Long userId);

    /** 关注某歌手的用户ID列表（favorite 服务新歌通知合并用） */
    List<Long> followerIds(Long singerId);

    /** 关键字搜歌手（主页跳转用；仅正常状态） */
    List<SingerVO> searchSingers(String keyword, int limit);
}
