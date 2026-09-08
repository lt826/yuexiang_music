package com.qxt.yuexiang.song.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.qxt.yuexiang.common.exception.BusinessException;
import com.qxt.yuexiang.common.result.ResultCode;
import com.qxt.yuexiang.song.dto.CommentPostDTO;
import com.qxt.yuexiang.song.entity.Song;
import com.qxt.yuexiang.song.entity.SongComment;
import com.qxt.yuexiang.song.entity.SongCommentLike;
import com.qxt.yuexiang.song.entity.SongLike;
import com.qxt.yuexiang.song.feign.MessageClient;
import com.qxt.yuexiang.song.feign.UserClient;
import com.qxt.yuexiang.song.mapper.SongCommentLikeMapper;
import com.qxt.yuexiang.song.mapper.SongCommentMapper;
import com.qxt.yuexiang.song.mapper.SongLikeMapper;
import com.qxt.yuexiang.song.mapper.SongMapper;
import com.qxt.yuexiang.song.service.SongInteractionService;
import com.qxt.yuexiang.song.vo.CommentVO;
import com.qxt.yuexiang.song.vo.SongInteractionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 歌曲互动实现：点赞照收藏的"先查后插 + 唯一键兜底"双保险；
 * 评论两级楼中楼（回复直挂根），列表按 sort 排序 + 回复预览 + 点赞态聚合
 * （Feign 批量查 user 服务，熔断降级显示"用户{id}"）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SongInteractionServiceImpl implements SongInteractionService {

    private final SongMapper songMapper;
    private final SongLikeMapper songLikeMapper;
    private final SongCommentMapper songCommentMapper;
    private final SongCommentLikeMapper songCommentLikeMapper;
    private final UserClient userClient;
    private final MessageClient messageClient;

    /** 评论时间前端展示格式 */
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** 评论内容长度上限（与 song_comment.content 列宽一致） */
    private static final int CONTENT_MAX = 500;

    /** 评论分页单页上限（防 size 传大值拖垮查询） */
    private static final int PAGE_SIZE_MAX = 50;

    /** 根评论列表随附的回复预览条数 */
    private static final int REPLY_PREVIEW_SIZE = 2;

    @Override
    public SongInteractionVO interaction(Long songId, Long userId) {
        requireSong(songId);
        SongInteractionVO vo = new SongInteractionVO();
        vo.setLikeCount(songLikeMapper.selectCount(
                new LambdaQueryWrapper<SongLike>().eq(SongLike::getSongId, songId)));
        vo.setCommentCount(songCommentMapper.selectCount(
                new LambdaQueryWrapper<SongComment>().eq(SongComment::getSongId, songId)));
        vo.setLiked(userId != null && songLikeMapper.selectCount(
                new LambdaQueryWrapper<SongLike>()
                        .eq(SongLike::getUserId, userId)
                        .eq(SongLike::getSongId, songId)) > 0);
        return vo;
    }

    @Override
    public long like(Long userId, Long songId) {
        requireSong(songId);
        SongLike exist = songLikeMapper.selectOne(
                new LambdaQueryWrapper<SongLike>()
                        .eq(SongLike::getUserId, userId)
                        .eq(SongLike::getSongId, songId));
        if (exist != null) {
            throw new BusinessException(ResultCode.REPEAT_OPERATION);
        }
        SongLike like = new SongLike();
        like.setUserId(userId);
        like.setSongId(songId);
        try {
            songLikeMapper.insert(like);
        } catch (DuplicateKeyException e) {
            // 唯一约束兜底防并发重复点赞
            throw new BusinessException(ResultCode.REPEAT_OPERATION);
        }
        return songLikeMapper.selectCount(
                new LambdaQueryWrapper<SongLike>().eq(SongLike::getSongId, songId));
    }

    @Override
    public long unlike(Long userId, Long songId) {
        requireSong(songId);
        songLikeMapper.delete(
                new LambdaQueryWrapper<SongLike>()
                        .eq(SongLike::getUserId, userId)
                        .eq(SongLike::getSongId, songId));
        return songLikeMapper.selectCount(
                new LambdaQueryWrapper<SongLike>().eq(SongLike::getSongId, songId));
    }

    // ===== 评论列表（推荐/最热/最新） =====

    /**
     * 推荐热度分：like_count×3 + reply_count×2 + 1（保底，零互动也参与排序），
     * 再按创建时间衰减——72 小时内每过 24h 热度打 6 折，老热评沉底但不消失。
     * （*100 取整保留小数序；GREATEST(x,1) 防全零评论整页乱序）
     */
    private static final String RECOMMEND_ORDER =
            "GREATEST(ROUND(("
                    + " (like_count * 3 + reply_count * 2 + 1)"
                    + " * POW(0.6, TIMESTAMPDIFF(HOUR, create_time, NOW()) / 72)"
                    + ") * 100), 1) DESC, id DESC";

    @Override
    public Map<String, Object> comments(Long songId, Long userId, String sort, int page, int size) {
        requireSong(songId);
        int p = Math.max(page, 1);
        int s = Math.min(Math.max(size, 1), PAGE_SIZE_MAX);

        // 仅根评论参与主列表分页（回复挂在根下），total = 根评论数
        LambdaQueryWrapper<SongComment> base = new LambdaQueryWrapper<SongComment>()
                .eq(SongComment::getSongId, songId)
                .isNull(SongComment::getRootId);
        long total = songCommentMapper.selectCount(base);

        String order = sort == null ? "" : sort.trim().toLowerCase();
        LambdaQueryWrapper<SongComment> query = new LambdaQueryWrapper<SongComment>()
                .eq(SongComment::getSongId, songId)
                .isNull(SongComment::getRootId);
        switch (order) {
            // 最热：点赞多 → 回复多 → 时间新
            case "hot" -> query.orderByDesc(SongComment::getLikeCount)
                    .orderByDesc(SongComment::getReplyCount)
                    .orderByDesc(SongComment::getId)
                    .last("LIMIT " + (long) (p - 1) * s + ", " + s);
            case "recommend" -> query.last("ORDER BY " + RECOMMEND_ORDER
                    + " LIMIT " + (long) (p - 1) * s + ", " + s);
            // time / 未知值统一时间倒序（默认最新）
            default -> query.orderByDesc(SongComment::getId)
                    .last("LIMIT " + (long) (p - 1) * s + ", " + s);
        }
        List<SongComment> roots = songCommentMapper.selectList(query);
        List<CommentVO> items = toVOList(roots);
        fillRootExtras(items, userId);
        return Map.of("items", items, "total", total, "page", p, "size", s);
    }

    @Override
    public Map<String, Object> replies(Long commentId, Long userId, int page, int size) {
        SongComment root = requireComment(commentId);
        // 只允许展开根评论的回复（对回复再展开无意义：楼中楼扁平两级）
        if (root.getRootId() != null) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "仅根评论可展开回复");
        }
        int p = Math.max(page, 1);
        int s = Math.min(Math.max(size, 1), PAGE_SIZE_MAX);
        LambdaQueryWrapper<SongComment> query = new LambdaQueryWrapper<SongComment>()
                .eq(SongComment::getRootId, commentId);
        long total = songCommentMapper.selectCount(query);
        List<SongComment> rows = songCommentMapper.selectList(query
                .orderByAsc(SongComment::getId)
                .last("LIMIT " + (long) (p - 1) * s + ", " + s));
        List<CommentVO> items = toVOList(rows);
        fillLikeState(items, userId);
        fillReplyNicknames(items);
        CommentVO rootVO = toVO(root, Collections.emptyMap());
        fillLikeState(List.of(rootVO), userId);
        Map<String, Object> result = new HashMap<>();
        result.put("items", items);
        result.put("total", total);
        result.put("root", rootVO);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommentVO postComment(Long userId, Long songId, CommentPostDTO dto) {
        requireSong(songId);
        String content = dto == null || dto.getContent() == null ? "" : dto.getContent().trim();
        if (content.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "评论内容不能为空");
        }
        if (content.length() > CONTENT_MAX) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "评论最多 " + CONTENT_MAX + " 字");
        }

        SongComment comment = new SongComment();
        comment.setSongId(songId);
        comment.setUserId(userId);
        comment.setContent(content);

        boolean isReply = dto.getRootId() != null;
        Long notifyUserId = null;
        if (isReply) {
            // 回复校验：根评论存在、属于本歌、确实是根
            SongComment root = requireComment(dto.getRootId());
            if (root.getSongId() != null && !root.getSongId().equals(songId)) {
                throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "回复的评论不属于当前歌曲");
            }
            if (root.getRootId() != null) {
                throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "回复请挂在根评论下");
            }
            comment.setRootId(root.getId());
            // 被回复人：replyToId 指定"回复谁"（楼中楼里可回复楼中楼层），缺省回复根评论作者
            Long targetUserId = root.getUserId();
            if (dto.getReplyToId() != null) {
                SongComment target = songCommentMapper.selectById(dto.getReplyToId());
                if (target == null || target.getRootId() == null
                        || !target.getRootId().equals(root.getId())) {
                    throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "被回复的评论不存在或不在该楼层");
                }
                targetUserId = target.getUserId();
            }
            comment.setReplyUserId(targetUserId);
            // 冗余回复数 +1（回复数变化驱动"最热"排序）
            songCommentMapper.update(null, new LambdaUpdateWrapper<SongComment>()
                    .eq(SongComment::getId, root.getId())
                    .setSql("reply_count = reply_count + 1"));
            // 通知被回复人（自回复自己不通知；失败不阻塞主流程）
            if (!targetUserId.equals(userId)) {
                notifyUserId = targetUserId;
            }
        }
        songCommentMapper.insert(comment);

        if (notifyUserId != null) {
            notifyReply(notifyUserId, songId, content);
        }
        return toVO(comment, fillUserInfo(Set.of(userId)));
    }

    /** 回复通知：失败仅记日志（message 服务抖动不影响评论） */
    private void notifyReply(Long targetUserId, Long songId, String content) {
        try {
            Song song = songMapper.selectById(songId);
            String songName = song == null ? "" : song.getName();
            String excerpt = content.length() > 30 ? content.substring(0, 30) + "…" : content;
            messageClient.send(targetUserId, "收到新回复",
                    "你在《" + songName + "》下的评论有了新回复：" + excerpt, "REPLY");
        } catch (Exception e) {
            log.warn("回复通知发送失败: userId={}, songId={}, {}", targetUserId, songId, e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteComment(Long userId, Long commentId) {
        SongComment comment = songCommentMapper.selectById(commentId);
        if (comment == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        if (!comment.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        if (comment.getRootId() == null) {
            // 根评论：级联删回复 + 清理两层的点赞记录（楼中楼不出现孤儿楼层）
            songCommentMapper.delete(new LambdaQueryWrapper<SongComment>()
                    .eq(SongComment::getRootId, commentId));
            songCommentLikeMapper.delete(new LambdaQueryWrapper<SongCommentLike>()
                    .eq(SongCommentLike::getCommentId, commentId));
            List<Long> replyIds = songCommentMapper.selectList(new LambdaQueryWrapper<SongComment>()
                            .select(SongComment::getId)
                            .eq(SongComment::getRootId, commentId))
                    .stream().map(SongComment::getId).toList();
            if (!replyIds.isEmpty()) {
                songCommentLikeMapper.delete(new LambdaQueryWrapper<SongCommentLike>()
                        .in(SongCommentLike::getCommentId, replyIds));
            }
        } else {
            // 回复：根评论冗余回复数 -1（不小于 0）
            songCommentMapper.update(null, new LambdaUpdateWrapper<SongComment>()
                    .eq(SongComment::getId, comment.getRootId())
                    .apply("reply_count > 0")
                    .setSql("reply_count = reply_count - 1"));
        }
        songCommentMapper.deleteById(commentId);
    }

    // ===== 评论点赞 =====

    @Override
    public long likeComment(Long userId, Long commentId) {
        requireComment(commentId);
        Long exist = songCommentLikeMapper.selectCount(
                new LambdaQueryWrapper<SongCommentLike>()
                        .eq(SongCommentLike::getUserId, userId)
                        .eq(SongCommentLike::getCommentId, commentId));
        if (exist > 0) {
            throw new BusinessException(ResultCode.REPEAT_OPERATION);
        }
        SongCommentLike like = new SongCommentLike();
        like.setUserId(userId);
        like.setCommentId(commentId);
        try {
            songCommentLikeMapper.insert(like);
        } catch (DuplicateKeyException e) {
            // 唯一约束兜底防并发重复点赞
            throw new BusinessException(ResultCode.REPEAT_OPERATION);
        }
        // 冗余计数同步（乐观兜底：无需精确到行锁，列表口径以 like 表计数为准）
        songCommentMapper.update(null, new LambdaUpdateWrapper<SongComment>()
                .eq(SongComment::getId, commentId)
                .setSql("like_count = like_count + 1"));
        return songCommentLikeMapper.selectCount(
                new LambdaQueryWrapper<SongCommentLike>()
                        .eq(SongCommentLike::getCommentId, commentId));
    }

    @Override
    public long unlikeComment(Long userId, Long commentId) {
        requireComment(commentId);
        songCommentLikeMapper.delete(
                new LambdaQueryWrapper<SongCommentLike>()
                        .eq(SongCommentLike::getUserId, userId)
                        .eq(SongCommentLike::getCommentId, commentId));
        songCommentMapper.update(null, new LambdaUpdateWrapper<SongComment>()
                .eq(SongComment::getId, commentId)
                .apply("like_count > 0")
                .setSql("like_count = like_count - 1"));
        return songCommentLikeMapper.selectCount(
                new LambdaQueryWrapper<SongCommentLike>()
                        .eq(SongCommentLike::getCommentId, commentId));
    }

    // ===== 私有辅助 =====

    /** 歌曲须存在（已删除由 @TableLogic 自动过滤），不存在抛 SONG_NOT_EXIST */
    private void requireSong(Long songId) {
        if (songMapper.selectById(songId) == null) {
            throw new BusinessException(ResultCode.SONG_NOT_EXIST);
        }
    }

    /** 评论须存在，不存在抛 NOT_FOUND */
    private SongComment requireComment(Long commentId) {
        SongComment comment = songCommentMapper.selectById(commentId);
        if (comment == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        return comment;
    }

    /** 实体列表 → VO 列表（批量聚合用户信息） */
    private List<CommentVO> toVOList(List<SongComment> rows) {
        if (rows.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> userIds = rows.stream().map(SongComment::getUserId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, Map<String, Object>> userInfo = fillUserInfo(userIds);
        return rows.stream().map(c -> toVO(c, userInfo)).collect(Collectors.toList());
    }

    /** 实体 → VO（userInfo 缺失时昵称降级"用户{id}"；计数取冗余列兜底 0） */
    private CommentVO toVO(SongComment c, Map<Long, Map<String, Object>> userInfo) {
        CommentVO vo = new CommentVO();
        BeanUtils.copyProperties(c, vo);
        vo.setLikeCount(c.getLikeCount() == null ? 0L : c.getLikeCount());
        vo.setReplyCount(c.getReplyCount() == null ? 0L : c.getReplyCount());
        vo.setLiked(false);
        LocalDateTime time = c.getCreateTime();
        vo.setCreateTime(time);
        vo.setTime(time == null ? "" : time.format(TIME_FMT));
        Map<String, Object> info = userInfo == null ? null : userInfo.get(c.getUserId());
        if (info != null) {
            Object nickname = info.get("nickname");
            Object avatar = info.get("avatar");
            vo.setNickname(nickname == null || nickname.toString().isBlank()
                    ? "用户" + c.getUserId() : nickname.toString());
            vo.setAvatar(avatar == null ? null : avatar.toString());
        } else {
            vo.setNickname("用户" + c.getUserId());
        }
        return vo;
    }

    /**
     * 根评论列表增强：点赞态、回复预览（最新 2 条）+ 预览内被回复人昵称。
     * 两条 SQL 批量完成（预览查询带 LIMIT），N 条评论不产生 N+1。
     */
    private void fillRootExtras(List<CommentVO> items, Long userId) {
        if (items.isEmpty()) {
            return;
        }
        fillLikeState(items, userId);

        List<Long> rootIds = items.stream().map(CommentVO::getId).toList();
        // 每个根评论的最新 2 条回复（MySQL 8 窗口函数，一次查询取全部根的预览）
        List<SongComment> previews = songCommentMapper.selectPreviewReplies(rootIds, REPLY_PREVIEW_SIZE);
        if (previews.isEmpty()) {
            return;
        }
        Map<Long, List<CommentVO>> byRoot = new HashMap<>();
        for (SongComment reply : previews) {
            byRoot.computeIfAbsent(reply.getRootId(), k -> new ArrayList<>()).add(toVO(reply, null));
        }
        // 被回复人昵称批量聚合
        Set<Long> replyUserIds = previews.stream().map(SongComment::getReplyUserId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, Map<String, Object>> replyUserInfo = fillUserInfo(replyUserIds);
        for (CommentVO vo : items) {
            List<CommentVO> preview = byRoot.get(vo.getId());
            if (preview != null) {
                preview.forEach(r -> {
                    r.setRootId(vo.getId());
                    if (r.getReplyUserId() != null) {
                        Map<String, Object> info = replyUserInfo.get(r.getReplyUserId());
                        if (info != null && info.get("nickname") != null) {
                            r.setReplyNickname(info.get("nickname").toString());
                        } else {
                            r.setReplyNickname("用户" + r.getReplyUserId());
                        }
                    }
                });
                vo.setReplyPreview(preview);
            }
        }
    }

    /** 批量回填点赞态（当前用户在目标评论里已赞的集合），未登录直接跳过 */
    private void fillLikeState(List<CommentVO> items, Long userId) {
        if (items.isEmpty() || userId == null) {
            return;
        }
        List<Long> ids = items.stream().map(CommentVO::getId).toList();
        Set<Long> likedIds = songCommentLikeMapper.selectList(
                        new LambdaQueryWrapper<SongCommentLike>()
                                .select(SongCommentLike::getCommentId)
                                .eq(SongCommentLike::getUserId, userId)
                                .in(SongCommentLike::getCommentId, ids))
                .stream().map(SongCommentLike::getCommentId)
                .collect(Collectors.toSet());
        items.forEach(vo -> vo.setLiked(likedIds.contains(vo.getId())));
    }

    /** 回复列表逐条聚合被回复人昵称（分页 ≤50 条，一条批量查询） */
    private void fillReplyNicknames(List<CommentVO> items) {
        Set<Long> ids = items.stream().map(CommentVO::getReplyUserId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, Map<String, Object>> info = fillUserInfo(ids);
        items.forEach(r -> {
            if (r.getReplyUserId() != null) {
                Map<String, Object> m = info.get(r.getReplyUserId());
                r.setReplyNickname(m != null && m.get("nickname") != null
                        ? m.get("nickname").toString() : "用户" + r.getReplyUserId());
            }
        });
    }

    /**
     * 批量查用户昵称/头像。Feign 熔断/异常返回空 Map（昵称降级"用户{id}"），
     * 与播放计数"宁可少显示，不可整页报错"的降级原则一致。
     */
    private Map<Long, Map<String, Object>> fillUserInfo(Set<Long> userIds) {
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        String ids = userIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        try {
            Map<Long, Map<String, Object>> info = userClient.batchInfo(ids);
            return info == null ? Collections.emptyMap() : info;
        } catch (Exception e) {
            log.warn("批量查用户信息失败（昵称降级显示）: ids={}, {}", ids, e.getMessage());
            return Collections.emptyMap();
        }
    }
}
