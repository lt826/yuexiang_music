package com.qxt.yuexiang.singer.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qxt.yuexiang.common.exception.BusinessException;
import com.qxt.yuexiang.common.result.ResultCode;
import com.qxt.yuexiang.singer.entity.Singer;
import com.qxt.yuexiang.singer.feign.MessageClient;
import com.qxt.yuexiang.singer.feign.SongClient;
import com.qxt.yuexiang.singer.feign.UserClient;
import com.qxt.yuexiang.singer.mapper.SingerMapper;
import com.qxt.yuexiang.singer.service.SingerService;
import com.qxt.yuexiang.singer.vo.SingerApplyDTO;
import com.qxt.yuexiang.singer.vo.SingerVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

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

    private SingerVO toVO(Singer singer) {
        SingerVO vo = new SingerVO();
        BeanUtils.copyProperties(singer, vo);
        return vo;
    }

    private List<SingerVO> toVOList(List<Singer> singers) {
        return singers.stream().map(this::toVO).collect(Collectors.toList());
    }
}
