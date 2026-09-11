package com.qxt.yuexiang.circle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qxt.yuexiang.circle.dto.CircleSaveDTO;
import com.qxt.yuexiang.circle.entity.Circle;
import com.qxt.yuexiang.circle.entity.CircleComment;
import com.qxt.yuexiang.circle.entity.CircleLike;
import com.qxt.yuexiang.circle.entity.CircleMember;
import com.qxt.yuexiang.circle.entity.CirclePost;
import com.qxt.yuexiang.circle.entity.ListenRoom;
import com.qxt.yuexiang.circle.feign.MessageClient;
import com.qxt.yuexiang.circle.feign.UserClient;
import com.qxt.yuexiang.circle.mapper.CircleCommentMapper;
import com.qxt.yuexiang.circle.mapper.CircleLikeMapper;
import com.qxt.yuexiang.circle.mapper.CircleMapper;
import com.qxt.yuexiang.circle.mapper.CircleMemberMapper;
import com.qxt.yuexiang.circle.mapper.CirclePostMapper;
import com.qxt.yuexiang.circle.mapper.ListenRoomMapper;
import com.qxt.yuexiang.circle.service.CircleService;
import com.qxt.yuexiang.circle.vo.CircleVO;
import com.qxt.yuexiang.circle.vo.MemberVO;
import com.qxt.yuexiang.common.exception.BusinessException;
import com.qxt.yuexiang.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 乐坊圈子服务实现
 * <p>
 * 名称审核闭环：创建 → status=0 待审核 + 通知管理员 → 通过/驳回 + 站内信通知圈主。
 * 通知失败不阻塞主流程（Feign try-catch 降级，对齐 playlist/message 通知策略）。
 * 权限总览（对齐需求"权限总览"表）：平台 ADMIN 等同圈主权限；圈管理员辅助管理。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CircleServiceImpl implements CircleService {

    private final CircleMapper circleMapper;
    private final CircleMemberMapper memberMapper;
    private final CirclePostMapper postMapper;
    private final CircleCommentMapper commentMapper;
    private final CircleLikeMapper likeMapper;
    private final ListenRoomMapper listenRoomMapper;
    private final UserClient userClient;
    private final MessageClient messageClient;

    public static final String ROLE_OWNER = "OWNER";
    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_MEMBER = "MEMBER";
    public static final String PLATFORM_ADMIN_ROLE = "ADMIN";

    // ===== 写操作 =====

    @Override
    public Long create(Long userId, CircleSaveDTO dto) {
        String name = requireValidName(dto);
        Circle circle = new Circle();
        circle.setCreatorId(userId);
        circle.setName(name);
        circle.setDescription(dto.getDescription());
        circle.setCover(dto.getCover());
        circle.setStatus(0);
        circle.setMemberCount(1);
        circle.setPostCount(0);
        circleMapper.insert(circle);

        // 圈主即第 1 个成员
        CircleMember member = new CircleMember();
        member.setCircleId(circle.getId());
        member.setUserId(userId);
        member.setRole(ROLE_OWNER);
        memberMapper.insert(member);

        // 通知管理员有新待审圈子（失败降级不阻塞）
        try {
            messageClient.notifyAdmins("乐坊新圈子待审核",
                    "圈子「" + name + "」已创建，等待审核名称。");
        } catch (Exception e) {
            log.warn("通知管理员失败（降级）: {}", e.getMessage());
        }
        log.info("圈子已创建待审: id={}, userId={}, name={}", circle.getId(), userId, name);
        return circle.getId();
    }

    @Override
    public void update(Long userId, String viewerRole, Long circleId, CircleSaveDTO dto) {
        Circle circle = requireCircle(circleId);
        requireManagerRole(userId, viewerRole, circle);
        if (dto.getName() != null && !dto.getName().isBlank() && !dto.getName().trim().equals(circle.getName())) {
            // 改名重新审核：审核期间圈子保持可见（用旧名），新名通过后生效
            circle.setName(dto.getName().trim());
            circle.setStatus(0);
            circle.setRejectReason(null);
        }
        if (dto.getDescription() != null) {
            circle.setDescription(dto.getDescription());
        }
        if (dto.getCover() != null) {
            circle.setCover(dto.getCover());
        }
        circleMapper.updateById(circle);
        try {
            messageClient.notifyAdmins("乐坊圈子改名待审核",
                    "圈子「" + circle.getName() + "」提交了新名称，等待审核。");
        } catch (Exception e) {
            log.warn("通知管理员失败（降级）: {}", e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void dissolve(Long userId, Long circleId) {
        Circle circle = requireCircle(circleId);
        // 权限：仅圈主可解散（平台管理员可下架违规圈子走管理端接口）
        if (!userId.equals(circle.getCreatorId())) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "只有圈主可以解散圈子");
        }
        cascadeDelete(circleId);
        log.info("圈子已解散: id={}, userId={}", circleId, userId);
    }

    @Override
    public void join(Long userId, Long circleId) {
        Circle circle = requireCircle(circleId);
        // 待审核/驳回的圈子不可加入
        if (circle.getStatus() == null || circle.getStatus() != 1) {
            throw new BusinessException(ResultCode.CIRCLE_NAME_REVIEW);
        }
        Long count = memberMapper.selectCount(new LambdaQueryWrapper<CircleMember>()
                .eq(CircleMember::getCircleId, circleId)
                .eq(CircleMember::getUserId, userId));
        if (count > 0) {
            throw new BusinessException(ResultCode.CIRCLE_ALREADY_MEMBER);
        }
        CircleMember member = new CircleMember();
        member.setCircleId(circleId);
        member.setUserId(userId);
        member.setRole(ROLE_MEMBER);
        memberMapper.insert(member);
        circle.setMemberCount((circle.getMemberCount() == null ? 0 : circle.getMemberCount()) + 1);
        circleMapper.updateById(circle);
    }

    @Override
    public void quit(Long userId, Long circleId) {
        Circle circle = requireCircle(circleId);
        if (userId.equals(circle.getCreatorId())) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "圈主不能退出，请解散圈子");
        }
        int removed = memberMapper.delete(new LambdaQueryWrapper<CircleMember>()
                .eq(CircleMember::getCircleId, circleId)
                .eq(CircleMember::getUserId, userId));
        if (removed == 0) {
            throw new BusinessException(ResultCode.CIRCLE_NOT_MEMBER);
        }
        circle.setMemberCount(Math.max(0, (circle.getMemberCount() == null ? 1 : circle.getMemberCount()) - 1));
        circleMapper.updateById(circle);
    }

    // ===== 成员管理（圈主/圈管理员） =====

    @Override
    public void kickMember(Long userId, String viewerRole, Long circleId, Long targetUserId) {
        Circle circle = requireCircle(circleId);
        requireManagerRole(userId, viewerRole, circle);
        if (userId.equals(targetUserId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "不能对自己操作");
        }
        CircleMember target = requireMemberRecord(circleId, targetUserId);
        // 权限分层：圈管理员只能踢普通成员；圈主/平台管理员可再踢圈管理员；圈主不可被踢
        if (ROLE_OWNER.equals(target.getRole()) || targetUserId.equals(circle.getCreatorId())) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "圈主不可被移出");
        }
        if (!userId.equals(circle.getCreatorId()) && !PLATFORM_ADMIN_ROLE.equals(viewerRole)
                && !ROLE_MEMBER.equals(target.getRole())) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "圈管理员只能移出普通成员");
        }
        memberMapper.deleteById(target.getId());
        circle.setMemberCount(Math.max(0, (circle.getMemberCount() == null ? 1 : circle.getMemberCount()) - 1));
        circleMapper.updateById(circle);
        notifyQuietly(targetUserId, "已被移出圈子",
                "您已被移出圈子「" + circle.getName() + "」。", "CIRCLE");
        log.info("成员被移出: circleId={}, target={}, by={}", circleId, targetUserId, userId);
    }

    @Override
    public void setMemberRole(Long userId, String viewerRole, Long circleId, Long targetUserId, String role) {
        Circle circle = requireCircle(circleId);
        // 设管理员是圈主专属（平台 ADMIN 例外，便于代管）
        boolean owner = userId.equals(circle.getCreatorId()) || PLATFORM_ADMIN_ROLE.equals(viewerRole);
        if (!owner) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "只有圈主可以设置管理员");
        }
        if (userId.equals(targetUserId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "不能对自己操作");
        }
        if (!ROLE_ADMIN.equals(role) && !ROLE_MEMBER.equals(role)) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "角色只能是 ADMIN 或 MEMBER");
        }
        CircleMember target = requireMemberRecord(circleId, targetUserId);
        if (targetUserId.equals(circle.getCreatorId())) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "圈主角色不可变更");
        }
        target.setRole(role);
        memberMapper.updateById(target);
        if (ROLE_ADMIN.equals(role)) {
            notifyQuietly(targetUserId, "已设为圈管理员",
                    "您已被圈主任命为圈子「" + circle.getName() + "」的管理员，可移出普通成员。", "CIRCLE");
        }
        log.info("成员角色变更: circleId={}, target={}, role={}, by={}", circleId, targetUserId, role, userId);
    }

    /** 目标成员记录（不存在即 4004 请先加入圈子） */
    private CircleMember requireMemberRecord(Long circleId, Long targetUserId) {
        CircleMember member = memberMapper.selectOne(new LambdaQueryWrapper<CircleMember>()
                .eq(CircleMember::getCircleId, circleId)
                .eq(CircleMember::getUserId, targetUserId)
                .last("LIMIT 1"));
        if (member == null) {
            throw new BusinessException(ResultCode.CIRCLE_NOT_MEMBER.getCode(), "对方不是该圈子成员");
        }
        return member;
    }

    // ===== 查询 =====

    @Override
    public List<MemberVO> members(Long userId, Long circleId) {
        // 权限总览：查看成员列表所有成员可看（普通成员也可以）
        requireMember(userId, circleId);
        List<CircleMember> list = memberMapper.selectList(new LambdaQueryWrapper<CircleMember>()
                .eq(CircleMember::getCircleId, circleId)
                .orderByAsc(CircleMember::getCreateTime));
        Map<Long, Map<String, Object>> users = batchUsers(
                list.stream().map(CircleMember::getUserId).collect(Collectors.toList()));
        return list.stream().map(m -> {
            MemberVO vo = new MemberVO();
            vo.setUserId(m.getUserId());
            vo.setRole(m.getRole());
            vo.setJoinTime(m.getCreateTime());
            Map<String, Object> u = users.get(m.getUserId());
            if (u != null) {
                String nick = (String) u.get("nickname");
                vo.setNickname(nick == null || nick.isBlank() ? "用户" + m.getUserId() : nick);
                vo.setAvatar((String) u.get("avatar"));
            }
            return vo;
        }).collect(Collectors.toList());
    }

    @Override
    public List<CircleVO> square(Long viewerId, String keyword, int limit) {
        LambdaQueryWrapper<Circle> wrapper = new LambdaQueryWrapper<Circle>()
                .eq(Circle::getStatus, 1)
                .orderByDesc(Circle::getMemberCount)
                .orderByDesc(Circle::getCreateTime)
                .last("LIMIT " + Math.min(Math.max(limit, 1), 100));
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like(Circle::getName, keyword.trim());
        }
        return toVOList(circleMapper.selectList(wrapper), viewerId);
    }

    @Override
    public List<CircleVO> my(Long userId) {
        // 我创建的 + 我加入的（待审核/驳回的只有创建者自己可见）
        List<CircleMember> mine = memberMapper.selectList(new LambdaQueryWrapper<CircleMember>()
                .eq(CircleMember::getUserId, userId));
        if (mine.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> circleIds = mine.stream().map(CircleMember::getCircleId).collect(Collectors.toList());
        List<Circle> circles = circleMapper.selectBatchIds(circleIds);
        return toVOList(circles, userId);
    }

    @Override
    public CircleVO detail(Long viewerId, String viewerRole, Long circleId) {
        Circle circle = requireCircle(circleId);
        boolean platformAdmin = PLATFORM_ADMIN_ROLE.equals(viewerRole);
        boolean owner = viewerId != null && viewerId.equals(circle.getCreatorId());
        // 待审核/驳回圈子仅圈主与平台管理员可见
        if (circle.getStatus() == null || circle.getStatus() != 1) {
            if (!owner && !platformAdmin) {
                throw new BusinessException(ResultCode.CIRCLE_NOT_EXIST);
            }
        }
        return toVOList(Collections.singletonList(circle), viewerId).get(0);
    }

    // ===== 审核闭环（平台管理员） =====

    @Override
    public void approve(Long circleId) {
        Circle circle = requireCircle(circleId);
        if (circle.getStatus() != null && circle.getStatus() == 1) {
            throw new BusinessException(ResultCode.REPEAT_OPERATION.getCode(), "该圈子已在审核通过状态");
        }
        circle.setStatus(1);
        circle.setRejectReason(null);
        circleMapper.updateById(circle);
        notifyQuietly(circle.getCreatorId(), "圈子审核通过",
                "您的圈子「" + circle.getName() + "」已通过审核，快去发帖吧！", "AUDIT");
        log.info("圈子审核通过: id={}, name={}", circleId, circle.getName());
    }

    @Override
    public void reject(Long circleId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "请填写驳回原因");
        }
        Circle circle = requireCircle(circleId);
        circle.setStatus(2);
        circle.setRejectReason(reason.trim());
        circleMapper.updateById(circle);
        notifyQuietly(circle.getCreatorId(), "圈子审核未通过",
                "您的圈子「" + circle.getName() + "」未通过审核，原因：" + reason.trim()
                        + "。您可修改名称后重新提交。", "AUDIT");
        log.info("圈子审核驳回: id={}, name={}, reason={}", circleId, circle.getName(), reason);
    }

    @Override
    public List<CircleVO> pendingList() {
        List<Circle> circles = circleMapper.selectList(new LambdaQueryWrapper<Circle>()
                .eq(Circle::getStatus, 0)
                .orderByAsc(Circle::getCreateTime));
        return toVOList(circles, null);
    }

    @Override
    public long pendingCount() {
        return circleMapper.selectCount(new LambdaQueryWrapper<Circle>()
                .eq(Circle::getStatus, 0));
    }

    // ===== 内部工具 =====

    private String requireValidName(CircleSaveDTO dto) {
        if (dto == null || dto.getName() == null || dto.getName().isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "请填写圈子名称");
        }
        String name = dto.getName().trim();
        if (name.length() > 50) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "圈子名称不能超过 50 字");
        }
        return name;
    }

    private Circle requireCircle(Long circleId) {
        Circle circle = circleMapper.selectById(circleId);
        if (circle == null) {
            throw new BusinessException(ResultCode.CIRCLE_NOT_EXIST);
        }
        return circle;
    }

    /** 是否圈主/圈管理员/平台管理员（平台角色由网关透传） */
    boolean isManager(Long userId, String viewerRole, Circle circle) {
        if (PLATFORM_ADMIN_ROLE.equals(viewerRole)) {
            return true;
        }
        if (userId.equals(circle.getCreatorId())) {
            return true;
        }
        CircleMember member = memberMapper.selectOne(new LambdaQueryWrapper<CircleMember>()
                .eq(CircleMember::getCircleId, circle.getId())
                .eq(CircleMember::getUserId, userId)
                .last("LIMIT 1"));
        return member != null && ROLE_ADMIN.equals(member.getRole());
    }

    void requireManagerRole(Long userId, String viewerRole, Circle circle) {
        if (!isManager(userId, viewerRole, circle)) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "需要圈主或圈管理员权限");
        }
    }

    private void requireMember(Long userId, Long circleId) {
        Long count = memberMapper.selectCount(new LambdaQueryWrapper<CircleMember>()
                .eq(CircleMember::getCircleId, circleId)
                .eq(CircleMember::getUserId, userId));
        if (count == 0) {
            throw new BusinessException(ResultCode.CIRCLE_NOT_MEMBER);
        }
    }

    /** 解散圈子级联清理：评论点赞 → 帖子 → 成员 → 圈子 */
    @Transactional(rollbackFor = Exception.class)
    void cascadeDelete(Long circleId) {
        List<Long> postIds = postMapper.selectList(new LambdaQueryWrapper<CirclePost>()
                        .eq(CirclePost::getCircleId, circleId))
                .stream().map(CirclePost::getId).collect(Collectors.toList());
        if (!postIds.isEmpty()) {
            commentMapper.delete(new LambdaQueryWrapper<CircleComment>()
                    .in(CircleComment::getPostId, postIds));
            likeMapper.delete(new LambdaQueryWrapper<CircleLike>()
                    .in(CircleLike::getPostId, postIds));
        }
        postMapper.delete(new LambdaQueryWrapper<CirclePost>()
                .eq(CirclePost::getCircleId, circleId));
        memberMapper.delete(new LambdaQueryWrapper<CircleMember>()
                .eq(CircleMember::getCircleId, circleId));
        circleMapper.deleteById(circleId);
    }

    /** 通知用户（失败降级不阻塞） */
    private void notifyQuietly(Long targetUserId, String title, String content, String type) {
        try {
            messageClient.send(targetUserId, title, content, type);
        } catch (Exception e) {
            log.warn("站内信通知失败（降级）: target={}, {}", targetUserId, e.getMessage());
        }
    }

    private Map<Long, Map<String, Object>> batchUsers(List<Long> userIds) {
        if (CollectionUtils.isEmpty(userIds)) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Map<String, Object>> raw = userClient.batchInfo(userIds);
            Map<Long, Map<String, Object>> result = new HashMap<>();
            raw.forEach((k, v) -> {
                try {
                    result.put(Long.valueOf(k), v);
                } catch (NumberFormatException ignore) {
                    // 脏 key 忽略
                }
            });
            return result;
        } catch (Exception e) {
            log.warn("聚合用户信息失败，降级为无昵称: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private List<CircleVO> toVOList(List<Circle> circles, Long viewerId) {
        if (circles.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, Map<String, Object>> creators = batchUsers(
                circles.stream().map(Circle::getCreatorId).distinct().collect(Collectors.toList()));

        // viewer 加入态
        Map<Long, String> myRoles = new HashMap<>();
        if (viewerId != null) {
            List<Long> ids = circles.stream().map(Circle::getId).collect(Collectors.toList());
            memberMapper.selectList(new LambdaQueryWrapper<CircleMember>()
                            .eq(CircleMember::getUserId, viewerId)
                            .in(CircleMember::getCircleId, ids))
                    .forEach(m -> myRoles.put(m.getCircleId(), m.getRole()));
        }

        // 进行中的一起听（走 DB status=0，不依赖 Redis，天然降级；一圈子最多一场）
        Map<Long, Long> liveRoomIds = new HashMap<>();
        List<Long> circleIds = circles.stream().map(Circle::getId).collect(Collectors.toList());
        listenRoomMapper.selectList(new LambdaQueryWrapper<ListenRoom>()
                        .eq(ListenRoom::getStatus, 0)
                        .in(ListenRoom::getCircleId, circleIds))
                .forEach(r -> liveRoomIds.putIfAbsent(r.getCircleId(), r.getId()));

        return circles.stream().map(c -> {
            CircleVO vo = new CircleVO();
            BeanUtils.copyProperties(c, vo);
            Map<String, Object> u = creators.get(c.getCreatorId());
            if (u != null) {
                String creatorNick = (String) u.get("nickname");
                vo.setCreatorName(creatorNick == null || creatorNick.isBlank() ? "用户" + c.getCreatorId() : creatorNick);
            }
            String role = myRoles.get(c.getId());
            vo.setJoined(role != null);
            vo.setMyRole(role);
            Long liveId = liveRoomIds.get(c.getId());
            vo.setLiveRoom(liveId != null);
            vo.setLiveRoomId(liveId);
            return vo;
        }).collect(Collectors.toList());
    }
}
