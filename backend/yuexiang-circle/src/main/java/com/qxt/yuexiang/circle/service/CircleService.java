package com.qxt.yuexiang.circle.service;

import com.qxt.yuexiang.circle.dto.CircleSaveDTO;
import com.qxt.yuexiang.circle.vo.CircleVO;
import com.qxt.yuexiang.circle.vo.MemberVO;

import java.util.List;

/**
 * 乐坊圈子服务：创建（名称审核）/加入退出/成员/审核闭环
 */
public interface CircleService {

    /** 创建圈子：status=0 待审核，通知管理员审核，返回新圈子 ID */
    Long create(Long userId, CircleSaveDTO dto);

    /** 编辑圈子（仅圈主/圈管理员/平台管理员）：改名会重新进入审核 */
    void update(Long userId, String viewerRole, Long circleId, CircleSaveDTO dto);

    /** 解散圈子（仅圈主），级联清理成员/帖子/评论/点赞 */
    void dissolve(Long userId, Long circleId);

    /** 加入圈子（登录，公开圈直接加入） */
    void join(Long userId, Long circleId);

    /** 退出圈子（圈主不能退出，只能解散） */
    void quit(Long userId, Long circleId);

    /** 成员列表（圈内成员可看） */
    List<MemberVO> members(Long userId, Long circleId);

    /** 广场：审核通过的圈子按成员数/时间倒序（匿名可看） */
    List<CircleVO> square(Long viewerId, String keyword, int limit);

    /** 我的圈子（创建的 + 加入的，含待审核的自己的圈子） */
    List<CircleVO> my(Long userId);

    /** 圈子详情（匿名可看；待审核圈子仅圈主与平台管理员可见） */
    CircleVO detail(Long viewerId, String viewerRole, Long circleId);

    /** 审核通过圈子名称（平台管理员），站内信通知圈主 */
    void approve(Long circleId);

    /** 审核驳回（平台管理员，必填原因），站内信通知圈主 */
    void reject(Long circleId, String reason);

    /** 待审核列表（平台管理员） */
    List<CircleVO> pendingList();

    /** 待审核数量（管理员红点） */
    long pendingCount();
}
