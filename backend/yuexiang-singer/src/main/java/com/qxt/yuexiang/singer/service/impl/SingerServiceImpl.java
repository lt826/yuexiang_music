package com.qxt.yuexiang.singer.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qxt.yuexiang.common.exception.BusinessException;
import com.qxt.yuexiang.common.result.Result;
import com.qxt.yuexiang.common.result.ResultCode;
import com.qxt.yuexiang.singer.entity.Singer;
import com.qxt.yuexiang.singer.entity.SingerFollow;
import com.qxt.yuexiang.singer.feign.MessageClient;
import com.qxt.yuexiang.singer.feign.SongClient;
import com.qxt.yuexiang.singer.feign.UserClient;
import com.qxt.yuexiang.singer.mapper.SingerFollowMapper;
import com.qxt.yuexiang.singer.mapper.SingerMapper;
import com.qxt.yuexiang.singer.service.SingerService;
import com.qxt.yuexiang.singer.vo.SingerApplyDTO;
import com.qxt.yuexiang.singer.vo.SingerFollowVO;
import com.qxt.yuexiang.singer.vo.SingerHomeVO;
import com.qxt.yuexiang.singer.vo.SingerVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 歌手服务实现
 * <p>
 * 认证状态机（对齐详细设计 5.3，落于 singer.status）：
 * 申请 → 0 待审核 → 通过 → 1 正常（回调 user 服务升级 SINGER）
 *                 → 驳回 → 2 驳回（可重新申请覆盖）
 * 同一用户同时只有一条活跃申请（待审核即拒绝重复提交）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SingerServiceImpl implements SingerService {

    private final SingerMapper singerMapper;
    private final SingerFollowMapper singerFollowMapper;
    private final UserClient userClient;
    private final MessageClient messageClient;
    private final SongClient songClient;

    @Override
    public List<SingerVO> hot(int limit) {
        // 主路：按歌手已发布歌曲的总播放量（作品热度）倒序
        List<Long> hotIds;
        try {
            hotIds = songClient.hotSingerIds(limit);
        } catch (Exception e) {
            log.warn("查询热门歌手ID失败，降级为 id 倒序: {}", e.getMessage());
            hotIds = List.of();
        }
        if (!hotIds.isEmpty()) {
            List<Singer> singers = singerMapper.selectList(
                    new LambdaQueryWrapper<Singer>()
                            .in(Singer::getId, hotIds)
                            .eq(Singer::getStatus, 1));
            // 按 hotIds 的热度顺序重排（selectList 不保序）
            Map<Long, Singer> byId = singers.stream()
                    .collect(Collectors.toMap(Singer::getId, s -> s, (a, b) -> a));
            List<Singer> ordered = hotIds.stream()
                    .map(byId::get)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            // 无作品歌手不足额时，用最新认证歌手补齐
            if (ordered.size() < limit) {
                List<Singer> fill = singerMapper.selectList(
                        new LambdaQueryWrapper<Singer>()
                                .eq(Singer::getStatus, 1)
                                .notIn(Singer::getId, hotIds)
                                .orderByDesc(Singer::getId)
                                .last("LIMIT " + (limit - ordered.size())));
                ordered.addAll(fill);
            }
            return toVOList(ordered);
        }
        // 兜底：song 服务不可用时按 id 倒序
        List<Singer> singers = singerMapper.selectList(
                new LambdaQueryWrapper<Singer>()
                        .eq(Singer::getStatus, 1)
                        .orderByDesc(Singer::getId)
                        .last("LIMIT " + limit));
        return toVOList(singers);
    }

    @Override
    public void apply(Long userId, SingerApplyDTO dto) {
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "请填写艺名");
        }
        // 同一用户存在待审核申请则拒绝，避免重复单
        Long pending = singerMapper.selectCount(
                new LambdaQueryWrapper<Singer>()
                        .eq(Singer::getUserId, userId)
                        .eq(Singer::getStatus, 0));
        if (pending > 0) {
            throw new BusinessException("已有待审核的申请，请耐心等待");
        }
        // 驳回后重新申请：覆盖旧记录
        Singer rejected = singerMapper.selectOne(
                new LambdaQueryWrapper<Singer>()
                        .eq(Singer::getUserId, userId)
                        .eq(Singer::getStatus, 2)
                        .last("LIMIT 1"));
        if (rejected != null) {
            rejected.setName(dto.getName());
            rejected.setIntroduction(dto.getIntroduction());
            rejected.setAvatar(dto.getAvatar());
            rejected.setStatus(0);
            rejected.setRejectReason(null);
            singerMapper.updateById(rejected);
            notifyAdminPending(dto.getName());
            return;
        }
        // 已是歌手不能重复申请
        Long approved = singerMapper.selectCount(
                new LambdaQueryWrapper<Singer>()
                        .eq(Singer::getUserId, userId)
                        .eq(Singer::getStatus, 1));
        if (approved > 0) {
            throw new BusinessException("你已是认证歌手，无需重复申请");
        }

        Singer singer = new Singer();
        singer.setUserId(userId);
        singer.setName(dto.getName());
        singer.setIntroduction(dto.getIntroduction());
        singer.setAvatar(dto.getAvatar());
        singer.setStatus(0);
        singerMapper.insert(singer);
        log.info("歌手认证申请已提交: userId={}, 艺名={}", userId, dto.getName());
        notifyAdminPending(dto.getName());
    }

    /** 闭环：申请提交即通知管理员（失败不阻塞申请主流程） */
    private void notifyAdminPending(String artistName) {
        try {
            messageClient.notifyAdmins("新歌手认证待审核",
                    "用户提交了歌手认证申请（艺名：" + artistName + "），请前往管理中心审核。");
        } catch (Exception e) {
            log.warn("歌手待审通知发送失败: 艺名={}, {}", artistName, e.getMessage());
        }
    }

    @Override
    public SingerVO myApplication(Long userId) {
        Singer singer = singerMapper.selectOne(
                new LambdaQueryWrapper<Singer>()
                        .eq(Singer::getUserId, userId)
                        .orderByDesc(Singer::getId)
                        .last("LIMIT 1"));
        return singer == null ? null : toVO(singer);
    }

    @Override
    public void updateProfile(Long userId, SingerApplyDTO dto) {
        Singer singer = singerMapper.selectOne(
                new LambdaQueryWrapper<Singer>()
                        .eq(Singer::getUserId, userId)
                        .eq(Singer::getStatus, 1)
                        .last("LIMIT 1"));
        if (singer == null) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "仅认证歌手可修改资料");
        }
        // 艺名即歌曲展示的发布者名字：改这里 = 改所有已发布歌曲的署名
        if (dto.getName() != null && !dto.getName().isBlank()) {
            singer.setName(dto.getName().trim());
        }
        if (dto.getIntroduction() != null) {
            singer.setIntroduction(dto.getIntroduction());
        }
        if (dto.getAvatar() != null) {
            singer.setAvatar(dto.getAvatar());
        }
        singerMapper.updateById(singer);
        log.info("歌手资料已更新: userId={}, 新艺名={}", userId, singer.getName());
    }

    @Override
    public SingerVO profile(Long singerId) {
        Singer singer = singerMapper.selectById(singerId);
        if (singer == null || singer.getStatus() == null || singer.getStatus() != 1) {
            throw new BusinessException(ResultCode.SINGER_NOT_EXIST);
        }
        return toVO(singer);
    }

    @Override
    public List<SingerVO> pendingList() {
        List<Singer> singers = singerMapper.selectList(
                new LambdaQueryWrapper<Singer>()
                        .eq(Singer::getStatus, 0)
                        .orderByAsc(Singer::getId));
        return toVOList(singers);
    }

    @Override
    public void audit(Long singerId, boolean approve, String reason) {
        Singer singer = singerMapper.selectById(singerId);
        if (singer == null || singer.getStatus() == null || singer.getStatus() != 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "该申请不存在或已处理");
        }
        if (approve) {
            singer.setStatus(1);
            singerMapper.updateById(singer);
            // 回调 user 服务升级角色；失败不回滚审核（角色可补偿，人工可查）
            try {
                userClient.upgradeRole(singer.getUserId(), "SINGER");
            } catch (Exception e) {
                log.error("升级用户角色失败（人工补偿）: userId={}, {}", singer.getUserId(), e.getMessage());
            }
            notify(singer.getUserId(), "歌手认证通过",
                    "恭喜！你的歌手认证已通过，现在可以在歌手中心发布作品了。");
            log.info("歌手认证通过: singerId={}, userId={}", singerId, singer.getUserId());
        } else {
            singer.setStatus(2);
            singer.setRejectReason(reason == null || reason.isBlank() ? "未通过审核" : reason);
            singerMapper.updateById(singer);
            notify(singer.getUserId(), "歌手认证未通过",
                    "很遗憾，你的歌手认证未通过。原因：" + singer.getRejectReason() + "。可修改后重新申请。");
            log.info("歌手认证驳回: singerId={}, 原因={}", singerId, singer.getRejectReason());
        }
    }

    /** 审核结果通知：失败仅记日志，永不阻塞审核主流程 */
    private void notify(Long userId, String title, String content) {
        try {
            messageClient.send(userId, title, content, "AUDIT");
        } catch (Exception e) {
            log.warn("审核通知发送失败: userId={}, {}", userId, e.getMessage());
        }
    }

    // ===== 关注（歌手主页闭环） =====

    @Override
    public SingerHomeVO home(Long singerId, Long viewerId) {
        Singer singer = singerMapper.selectById(singerId);
        if (singer == null || singer.getStatus() == null || singer.getStatus() != 1) {
            throw new BusinessException(ResultCode.SINGER_NOT_EXIST);
        }
        SingerHomeVO vo = new SingerHomeVO();
        vo.setId(singer.getId());
        vo.setUserId(singer.getUserId());
        vo.setName(singer.getName());
        vo.setAvatar(singer.getAvatar());
        vo.setIntroduction(singer.getIntroduction());
        vo.setSongCount(0L);
        vo.setTotalPlayCount(0L);
        // 作品数与总播放量聚合 song 服务（失败降级为 0，主页照常打开）
        try {
            List<Map<String, Object>> songs = songClient.bySinger(singerId).getData();
            if (songs != null) {
                vo.setSongCount((long) songs.size());
                vo.setTotalPlayCount(songs.stream()
                        .map(s -> s.get("playCount"))
                        .filter(Objects::nonNull)
                        .mapToLong(v -> Long.valueOf(v.toString()))
                        .sum());
            }
        } catch (Exception e) {
            log.warn("歌手主页聚合作品数据失败: singerId={}, {}", singerId, e.getMessage());
        }
        // 粉丝数/关注态与关注接口同源：认证歌手读用户互关表（user_follow），
        // 虚拟歌手（无所属账号）读本地 singer_follow；Feign 失败降级本地，主页照常打开
        long followerCount;
        boolean followed = false;
        if (singer.getUserId() != null) {
            try {
                // 透传 viewerId：user 服务按查看者回填 following 关注态（不传恒为 null）
                Result<Map<String, Object>> summaryResult = userClient.followSummary(singer.getUserId(), viewerId);
                Map<String, Object> summary = summaryResult == null ? null : summaryResult.getData();
                if (summary != null) {
                    Object fc = summary.get("followerCount");
                    followerCount = fc == null ? 0L : Long.valueOf(fc.toString());
                    followed = Boolean.TRUE.equals(summary.get("following"));
                } else {
                    followerCount = localFollowerCount(singerId);
                }
            } catch (Exception e) {
                log.warn("聚合认证歌手粉丝数失败（降级本地）: singerId={}, {}", singerId, e.getMessage());
                followerCount = localFollowerCount(singerId);
            }
        } else {
            followerCount = localFollowerCount(singerId);
        }
        vo.setFollowerCount(followerCount);
        vo.setFollowed(viewerId != null && (followed || singerFollowMapper.selectCount(
                new LambdaQueryWrapper<SingerFollow>()
                        .eq(SingerFollow::getUserId, viewerId)
                        .eq(SingerFollow::getSingerId, singerId)) > 0));
        return vo;
    }

    /** 本地关注表计数（虚拟歌手 / Feign 降级路径） */
    private long localFollowerCount(Long singerId) {
        return singerFollowMapper.selectCount(
                new LambdaQueryWrapper<SingerFollow>().eq(SingerFollow::getSingerId, singerId));
    }

    @Override
    public void follow(Long userId, Long singerId) {
        Singer singer = singerMapper.selectById(singerId);
        if (singer == null || singer.getStatus() == null || singer.getStatus() != 1) {
            throw new BusinessException(ResultCode.SINGER_NOT_EXIST);
        }
        if (singer.getUserId() != null && singer.getUserId().equals(userId)) {
            throw new BusinessException("不能关注自己");
        }
        boolean newly;
        if (singer.getUserId() != null) {
            // 认证歌手：关注统一落到用户互关表（user_follow），与个人主页粉丝数同源；
            // 否则用户主页粉丝数与歌手主页粉丝数对不上
            Result<Boolean> followResult = userClient.follow(userId, singer.getUserId());
            newly = followResult != null && Boolean.TRUE.equals(followResult.getData());
        } else {
            // 平台导入的虚拟歌手（无所属账号）：保留本地关注表
            Long exist = singerFollowMapper.selectCount(
                    new LambdaQueryWrapper<SingerFollow>()
                            .eq(SingerFollow::getUserId, userId)
                            .eq(SingerFollow::getSingerId, singerId));
            if (exist > 0) {
                return;
            }
            SingerFollow follow = new SingerFollow();
            follow.setUserId(userId);
            follow.setSingerId(singerId);
            try {
                singerFollowMapper.insert(follow);
            } catch (DuplicateKeyException e) {
                // 唯一约束兜底防并发重复关注
                return;
            }
            newly = true;
        }
        log.info("用户关注歌手: userId={}, singerId={}, 认证={}, 新关注={}", userId, singerId,
                singer.getUserId() != null, newly);
        // 通知歌手有新粉丝（仅真正新增时通知，失败不阻塞关注主流程）
        if (newly && singer.getUserId() != null) {
            try {
                messageClient.send(singer.getUserId(), "新粉丝关注",
                        "有乐友关注了你，快去发布新作品回馈粉丝吧！", "FOLLOW");
            } catch (Exception e) {
                log.warn("关注通知发送失败: singerId={}, {}", singerId, e.getMessage());
            }
        }
    }

    @Override
    public void unfollow(Long userId, Long singerId) {
        Singer singer = singerMapper.selectById(singerId);
        if (singer != null && singer.getUserId() != null) {
            userClient.unfollow(userId, singer.getUserId());
        } else {
            singerFollowMapper.delete(
                    new LambdaQueryWrapper<SingerFollow>()
                            .eq(SingerFollow::getUserId, userId)
                            .eq(SingerFollow::getSingerId, singerId));
        }
        log.info("用户取消关注: userId={}, singerId={}", userId, singerId);
    }

    @Override
    public List<SingerFollowVO> myFollows(Long userId) {
        // 关注的歌手 = 认证歌手（经用户互关表）∪ 虚拟歌手（本地关注表），去重合并
        Map<Long, SingerFollowVO> merged = new LinkedHashMap<>();
        try {
            List<Long> followingUserIds = userClient.followingIdsOf(userId);
            if (followingUserIds != null && !followingUserIds.isEmpty()) {
                Map<Long, Singer> byUserId = singerMapper.selectList(new LambdaQueryWrapper<Singer>()
                                .in(Singer::getUserId, followingUserIds)
                                .eq(Singer::getStatus, 1))
                        .stream()
                        .collect(Collectors.toMap(Singer::getUserId, s -> s, (a, b) -> a));
                for (Long uid : followingUserIds) {
                    Singer singer = byUserId.get(uid);
                    if (singer != null) {
                        merged.put(singer.getId(), toFollowVO(singer, null));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("聚合用户互关的歌手列表失败（降级）: userId={}, {}", userId, e.getMessage());
        }
        List<SingerFollow> follows = singerFollowMapper.selectList(
                new LambdaQueryWrapper<SingerFollow>()
                        .eq(SingerFollow::getUserId, userId)
                        .orderByDesc(SingerFollow::getId));
        // 批量取歌手资料（已删除/冻结歌手自动过滤，列表里不再展示）
        if (!follows.isEmpty()) {
            List<Long> singerIds = follows.stream().map(SingerFollow::getSingerId).toList();
            Map<Long, Singer> byId = singerMapper.selectBatchIds(singerIds).stream()
                    .filter(s -> s.getStatus() != null && s.getStatus() == 1)
                    .collect(Collectors.toMap(Singer::getId, s -> s, (a, b) -> a));
            for (SingerFollow f : follows) {
                Singer singer = byId.get(f.getSingerId());
                if (singer != null) {
                    merged.putIfAbsent(singer.getId(), toFollowVO(singer, f.getCreateTime()));
                }
            }
        }
        return new ArrayList<>(merged.values());
    }

    @Override
    public List<SingerFollowVO> virtualFollowsOf(Long userId) {
        List<SingerFollow> follows = singerFollowMapper.selectList(
                new LambdaQueryWrapper<SingerFollow>()
                        .eq(SingerFollow::getUserId, userId)
                        .orderByDesc(SingerFollow::getId));
        if (follows.isEmpty()) {
            return Collections.emptyList();
        }
        // 批量取歌手资料（已删除/冻结歌手自动过滤，列表里不再展示）
        List<Long> singerIds = follows.stream().map(SingerFollow::getSingerId).toList();
        Map<Long, Singer> byId = singerMapper.selectBatchIds(singerIds).stream()
                .filter(s -> s.getStatus() != null && s.getStatus() == 1)
                .collect(Collectors.toMap(Singer::getId, s -> s, (a, b) -> a));
        List<SingerFollowVO> result = new ArrayList<>();
        for (SingerFollow f : follows) {
            Singer singer = byId.get(f.getSingerId());
            if (singer != null) {
                result.add(toFollowVO(singer, f.getCreateTime()));
            }
        }
        return result;
    }

    private SingerFollowVO toFollowVO(Singer singer, java.time.LocalDateTime followTime) {
        SingerFollowVO vo = new SingerFollowVO();
        vo.setSingerId(singer.getId());
        vo.setName(singer.getName());
        vo.setAvatar(singer.getAvatar());
        vo.setIntroduction(singer.getIntroduction());
        vo.setFollowTime(followTime);
        return vo;
    }

    @Override
    public List<Long> myFollowIds(Long userId) {
        // 与 myFollows 同源：认证歌手走用户互关，虚拟歌手走本地关注表
        java.util.LinkedHashSet<Long> ids = new java.util.LinkedHashSet<>();
        try {
            List<Long> followingUserIds = userClient.followingIdsOf(userId);
            if (followingUserIds != null && !followingUserIds.isEmpty()) {
                singerMapper.selectList(new LambdaQueryWrapper<Singer>()
                                .select(Singer::getId, Singer::getUserId)
                                .in(Singer::getUserId, followingUserIds)
                                .eq(Singer::getStatus, 1))
                        .forEach(s -> ids.add(s.getId()));
            }
        } catch (Exception e) {
            log.warn("聚合用户互关歌手ID失败（降级）: userId={}, {}", userId, e.getMessage());
        }
        singerFollowMapper.selectList(new LambdaQueryWrapper<SingerFollow>()
                        .select(SingerFollow::getSingerId)
                        .eq(SingerFollow::getUserId, userId))
                .forEach(f -> ids.add(f.getSingerId()));
        return new ArrayList<>(ids);
    }

    @Override
    public List<Long> followerIds(Long singerId) {
        Singer singer = singerMapper.selectById(singerId);
        if (singer != null && singer.getUserId() != null) {
            // 认证歌手：粉丝 = 关注其账号的用户（user_follow）
            try {
                return userClient.followerIdsOf(singer.getUserId());
            } catch (Exception e) {
                log.warn("查询认证歌手粉丝失败（降级为空）: singerId={}, {}", singerId, e.getMessage());
                return Collections.emptyList();
            }
        }
        return singerFollowMapper.selectList(new LambdaQueryWrapper<SingerFollow>()
                        .select(SingerFollow::getUserId)
                        .eq(SingerFollow::getSingerId, singerId))
                .stream().map(SingerFollow::getUserId).toList();
    }

    @Override
    public List<SingerVO> searchSingers(String keyword, int limit) {
        if (keyword == null || keyword.isBlank()) {
            return Collections.emptyList();
        }
        String escaped = keyword.trim()
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        List<Singer> singers = singerMapper.selectList(
                new LambdaQueryWrapper<Singer>()
                        .eq(Singer::getStatus, 1)
                        .like(Singer::getName, escaped)
                        .orderByDesc(Singer::getId)
                        .last("LIMIT " + Math.min(Math.max(limit, 1), 20)));
        return toVOList(singers);
    }

    private SingerVO toVO(Singer singer) {
        SingerVO vo = new SingerVO();
        BeanUtils.copyProperties(singer, vo);
        return vo;
    }

    private List<SingerVO> toVOList(List<Singer> singers) {
        return singers.stream().map(this::toVO).collect(Collectors.toList());
    }
}
