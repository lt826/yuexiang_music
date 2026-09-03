package com.qxt.yuexiang.singer.service;

import com.qxt.yuexiang.singer.vo.SingerApplyDTO;
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
}
