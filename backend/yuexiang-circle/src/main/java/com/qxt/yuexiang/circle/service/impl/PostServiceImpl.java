package com.qxt.yuexiang.circle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.qxt.yuexiang.circle.dto.CommentCreateDTO;
import com.qxt.yuexiang.circle.dto.PostCreateDTO;
import com.qxt.yuexiang.circle.entity.Circle;
import com.qxt.yuexiang.circle.entity.CircleComment;
import com.qxt.yuexiang.circle.entity.CircleLike;
import com.qxt.yuexiang.circle.entity.CircleMember;
import com.qxt.yuexiang.circle.entity.CirclePost;
import com.qxt.yuexiang.circle.feign.SongClient;
import com.qxt.yuexiang.circle.feign.UserClient;
import com.qxt.yuexiang.circle.mapper.CircleCommentMapper;
import com.qxt.yuexiang.circle.mapper.CircleLikeMapper;
import com.qxt.yuexiang.circle.mapper.CircleMapper;
import com.qxt.yuexiang.circle.mapper.CircleMemberMapper;
import com.qxt.yuexiang.circle.mapper.CirclePostMapper;
import com.qxt.yuexiang.circle.service.CircleService;
import com.qxt.yuexiang.circle.service.PostService;
import com.qxt.yuexiang.circle.vo.CommentVO;
import com.qxt.yuexiang.circle.vo.PostVO;
import com.qxt.yuexiang.common.exception.BusinessException;
import com.qxt.yuexiang.common.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 乐坊帖子服务实现
 * <p>
 * 权限对齐需求"权限总览"表：发帖/评论/点赞为成员权限；删他人帖/评、置顶为圈主/管理员权限。
 * 平台 ADMIN（网关透传 X-User-Role）等同圈主。Feign 聚合歌曲/用户信息均降级不阻塞。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostServiceImpl implements PostService {

    private final CirclePostMapper postMapper;
    private final CircleCommentMapper commentMapper;
    private final CircleLikeMapper likeMapper;
    private final CircleMapper circleMapper;
    private final CircleMemberMapper memberMapper;
    private final SongClient songClient;
    private final UserClient userClient;
    private final CircleService circleService;

    private static final int MAX_PINNED = 2;

    // ===== 帖子 =====

    @Override
    public Long createPost(Long userId, Long circleId, PostCreateDTO dto) {
        requireCircleActive(circleId);
        requireMember(userId, circleId);
        if (dto == null || dto.getContent() == null || dto.getContent().isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "请输入帖子内容");
        }
        if (dto.getContent().length() > 2000) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "帖子内容不能超过 2000 字");
        }
        if (dto.getSongId() != null) {
            // Feign 校验歌曲存在且可播（不存在则发帖失败，对齐歌单加歌校验策略）
            try {
                Map<String, Map<String, Object>> batch = songClient.batch(List.of(dto.getSongId()));
                if (!batch.containsKey(String.valueOf(dto.getSongId()))) {
                    throw new BusinessException(ResultCode.SONG_NOT_EXIST);
                }
            } catch (BusinessException e) {
                throw e;
            } catch (Exception e) {
                throw new BusinessException("歌曲服务暂时不可用，请稍后重试");
            }
        }
        if (dto.getImage() != null && !dto.getImage().isBlank()) {
            // 图片必须是 file 服务上传返回的站内地址（防外链/注入），最多 1 张
            String image = dto.getImage().trim();
            if (image.length() > 500) {
                throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "图片地址不合法");
            }
            if (!image.startsWith("/api/file/")) {
                throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "帖子图片仅支持站内上传的图片");
            }
            dto.setImage(image);
        } else {
            dto.setImage(null);
        }
        CirclePost post = new CirclePost();
        post.setCircleId(circleId);
        post.setUserId(userId);
        post.setContent(dto.getContent().trim());
        post.setSongId(dto.getSongId());
        post.setImage(dto.getImage());
        post.setPinned(0);
        post.setLikeCount(0);
        post.setCommentCount(0);
        postMapper.insert(post);

        // 圈子帖子计数 +1
        circleMapper.update(null, new LambdaUpdateWrapper<Circle>()
                .eq(Circle::getId, circleId)
                .setSql("post_count = post_count + 1"));
        log.info("帖子已发布: id={}, circleId={}, userId={}", post.getId(), circleId, userId);
        return post.getId();
    }

    @Override
    public List<PostVO> posts(Long viewerId, String viewerRole, Long circleId, int page, int size) {
        int pageSize = Math.min(Math.max(size, 1), 50);
        // 置顶在前 + 发布时间倒序（pinned DESC, create_time DESC 走索引 idx_circle_pinned_time）
        List<CirclePost> list = postMapper.selectList(new LambdaQueryWrapper<CirclePost>()
                .eq(CirclePost::getCircleId, circleId)
                .orderByDesc(CirclePost::getPinned)
                .orderByDesc(CirclePost::getCreateTime)
                .last("LIMIT " + (long) (Math.max(page, 1) - 1) * pageSize + ", " + pageSize));

        // 圈主/管理员判定信息
        Circle circle = circleMapper.selectById(circleId);
        Set<Long> managerIds = managerIdsOf(circle);
        boolean platformAdmin = CircleServiceImpl.PLATFORM_ADMIN_ROLE.equals(viewerRole);

        // 批量聚合：用户信息、点赞态、歌曲卡片
        Set<Long> likedPostIds = likedPostIds(viewerId, list);
        Map<Long, Map<String, Object>> users = batchUsers(
                list.stream().map(CirclePost::getUserId).collect(Collectors.toList()));
        Map<Long, Map<String, Object>> songs = batchSongs(
                list.stream().map(CirclePost::getSongId).filter(Objects::nonNull).collect(Collectors.toList()));

        return list.stream().map(p -> toPostVO(p, viewerId, users, likedPostIds, songs,
                managerIds, platformAdmin, circle)).collect(Collectors.toList());
    }

    @Override
    public void deletePost(Long userId, String viewerRole, Long postId) {
        CirclePost post = requirePost(postId);
        boolean allowed = postIdOwner(userId, post) || isManager(userId, viewerRole, post.getCircleId());
        if (!allowed) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "无权限删除该帖子");
        }
        postMapper.deleteById(postId);
        circleMapper.update(null, new LambdaUpdateWrapper<Circle>()
                .eq(Circle::getId, post.getCircleId())
                .setSql("post_count = GREATEST(post_count - 1, 0)"));
        log.info("帖子已删除: id={}, by={}", postId, userId);
    }

    @Override
    public void pinPost(Long userId, String viewerRole, Long postId, boolean pinned) {
        CirclePost post = requirePost(postId);
        if (!isManager(userId, viewerRole, post.getCircleId())) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "需要圈主或圈管理员权限");
        }
        if (pinned) {
            // 置顶最多 2 条
            Long count = postMapper.selectCount(new LambdaQueryWrapper<CirclePost>()
                    .eq(CirclePost::getCircleId, post.getCircleId())
                    .eq(CirclePost::getPinned, 1)
                    .ne(CirclePost::getId, postId));
            if (count >= MAX_PINNED) {
                throw new BusinessException(ResultCode.PINNED_LIMIT);
            }
        }
        post.setPinned(pinned ? 1 : 0);
        postMapper.updateById(post);
    }

    // ===== 评论（楼中楼） =====

    @Override
    public List<CommentVO> comments(Long viewerId, String viewerRole, Long postId) {
        CirclePost post = requirePost(postId);
        List<CircleComment> all = commentMapper.selectList(new LambdaQueryWrapper<CircleComment>()
                .eq(CircleComment::getPostId, postId)
                .orderByAsc(CircleComment::getCreateTime));

        Circle circle = circleMapper.selectById(post.getCircleId());
        Set<Long> managerIds = managerIdsOf(circle);
        boolean platformAdmin = CircleServiceImpl.PLATFORM_ADMIN_ROLE.equals(viewerRole);
        Map<Long, Map<String, Object>> users = batchUsers(
                all.stream().map(CircleComment::getUserId).collect(Collectors.toList()));

        // 顶层评论 + 各自 replies（一次查询内存分组，避免 N+1）
        Map<Long, List<CircleComment>> byParent = all.stream()
                .filter(c -> c.getParentId() != null)
                .collect(Collectors.groupingBy(CircleComment::getParentId));
        List<CommentVO> roots = new ArrayList<>();
        for (CircleComment c : all) {
            if (c.getParentId() != null) {
                continue;
            }
            roots.add(toCommentVO(c, byParent.get(c.getId()), viewerId, users, managerIds, platformAdmin));
        }
        return roots;
    }

    @Override
    public Long comment(Long userId, Long postId, CommentCreateDTO dto) {
        CirclePost post = requirePost(postId);
        requireCircleActive(post.getCircleId());
        requireMember(userId, post.getCircleId());
        if (dto == null || dto.getContent() == null || dto.getContent().isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "请输入评论内容");
        }
        if (dto.getContent().length() > 500) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "评论内容不能超过 500 字");
        }
        CircleComment c = new CircleComment();
        c.setPostId(postId);
        c.setUserId(userId);
        c.setContent(dto.getContent().trim());
        if (dto.getParentId() != null) {
            // 回复某条评论：父评论必须属于同一帖子
            CircleComment parent = commentMapper.selectById(dto.getParentId());
            if (parent == null || !parent.getPostId().equals(postId)) {
                throw new BusinessException(ResultCode.COMMENT_NOT_EXIST);
            }
            c.setParentId(parent.getParentId() != null ? parent.getParentId() : parent.getId());
            c.setReplyToUserId(parent.getUserId());
        }
        commentMapper.insert(c);
        postMapper.update(null, new LambdaUpdateWrapper<CirclePost>()
                .eq(CirclePost::getId, postId)
                .setSql("comment_count = comment_count + 1"));
        return c.getId();
    }

    @Override
    public void deleteComment(Long userId, String viewerRole, Long commentId) {
        CircleComment c = commentMapper.selectById(commentId);
        if (c == null) {
            throw new BusinessException(ResultCode.COMMENT_NOT_EXIST);
        }
        CirclePost post = requirePost(c.getPostId());
        boolean allowed = userId.equals(c.getUserId())
                || isManager(userId, viewerRole, post.getCircleId());
        if (!allowed) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "无权限删除该评论");
        }
        // 删顶层评论连回复一起删（业务语义：整楼删除）；楼中楼回复只删自己
        long removed = 1;
        commentMapper.deleteById(commentId);
        if (c.getParentId() == null) {
            List<Long> replyIds = commentMapper.selectList(new LambdaQueryWrapper<CircleComment>()
                            .eq(CircleComment::getParentId, commentId))
                    .stream().map(CircleComment::getId).collect(Collectors.toList());
            if (!replyIds.isEmpty()) {
                commentMapper.deleteByIds(replyIds);
                removed += replyIds.size();
            }
        }
        postMapper.update(null, new LambdaUpdateWrapper<CirclePost>()
                .eq(CirclePost::getId, post.getId())
                .setSql("comment_count = GREATEST(comment_count - " + removed + ", 0)"));
    }

    // ===== 点赞 =====

    @Override
    public void toggleLike(Long userId, Long postId) {
        requirePost(postId);
        Long count = likeMapper.selectCount(new LambdaQueryWrapper<CircleLike>()
                .eq(CircleLike::getPostId, postId)
                .eq(CircleLike::getUserId, userId));
        if (count > 0) {
            likeMapper.delete(new LambdaQueryWrapper<CircleLike>()
                    .eq(CircleLike::getPostId, postId)
                    .eq(CircleLike::getUserId, userId));
            postMapper.update(null, new LambdaUpdateWrapper<CirclePost>()
                    .eq(CirclePost::getId, postId)
                    .setSql("like_count = GREATEST(like_count - 1, 0)"));
        } else {
            CircleLike like = new CircleLike();
            like.setPostId(postId);
            like.setUserId(userId);
            likeMapper.insert(like);
            postMapper.update(null, new LambdaUpdateWrapper<CirclePost>()
                    .eq(CirclePost::getId, postId)
                    .setSql("like_count = like_count + 1"));
        }
    }

    // ===== 内部工具 =====

    private void requireCircleActive(Long circleId) {
        Circle circle = circleMapper.selectById(circleId);
        if (circle == null) {
            throw new BusinessException(ResultCode.CIRCLE_NOT_EXIST);
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

    private CirclePost requirePost(Long postId) {
        CirclePost post = postMapper.selectById(postId);
        if (post == null) {
            throw new BusinessException(ResultCode.POST_NOT_EXIST);
        }
        return post;
    }

    private boolean isManager(Long userId, String viewerRole, Long circleId) {
        if (CircleServiceImpl.PLATFORM_ADMIN_ROLE.equals(viewerRole)) {
            return true;
        }
        Circle circle = circleMapper.selectById(circleId);
        if (circle == null) {
            return false;
        }
        // 圈主
        if (userId.equals(circle.getCreatorId())) {
            return true;
        }
        // 圈管理员
        CircleMember member = memberMapper.selectOne(new LambdaQueryWrapper<CircleMember>()
                .eq(CircleMember::getCircleId, circleId)
                .eq(CircleMember::getUserId, userId)
                .last("LIMIT 1"));
        return member != null && CircleServiceImpl.ROLE_ADMIN.equals(member.getRole());
    }

    /** 圈子的圈主+圈管理员 ID 集合（帖子列表批量判权用） */
    private Set<Long> managerIdsOf(Circle circle) {
        if (circle == null) {
            return Collections.emptySet();
        }
        Set<Long> ids = new HashSet<>();
        ids.add(circle.getCreatorId());
        memberMapper.selectList(new LambdaQueryWrapper<CircleMember>()
                        .eq(CircleMember::getCircleId, circle.getId())
                        .eq(CircleMember::getRole, CircleServiceImpl.ROLE_ADMIN))
                .forEach(m -> ids.add(m.getUserId()));
        return ids;
    }

    private Set<Long> likedPostIds(Long viewerId, List<CirclePost> posts) {
        if (viewerId == null || posts.isEmpty()) {
            return Collections.emptySet();
        }
        return likeMapper.selectList(new LambdaQueryWrapper<CircleLike>()
                        .eq(CircleLike::getUserId, viewerId)
                        .in(CircleLike::getPostId,
                                posts.stream().map(CirclePost::getId).collect(Collectors.toList())))
                .stream().map(CircleLike::getPostId).collect(Collectors.toSet());
    }

    private Map<Long, Map<String, Object>> batchUsers(List<Long> userIds) {
        List<Long> distinct = userIds.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (distinct.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Map<String, Object>> raw = userClient.batchInfo(distinct);
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

    private Map<Long, Map<String, Object>> batchSongs(List<Long> songIds) {
        List<Long> distinct = songIds.stream().filter(Objects::nonNull).distinct().collect(Collectors.toList());
        if (distinct.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Map<String, Object>> raw = songClient.batch(distinct);
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
            log.warn("聚合歌曲卡片失败，降级为仅ID: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private PostVO toPostVO(CirclePost p, Long viewerId, Map<Long, Map<String, Object>> users,
                            Set<Long> likedPostIds, Map<Long, Map<String, Object>> songs,
                            Set<Long> managerIds, boolean platformAdmin, Circle circle) {
        PostVO vo = new PostVO();
        BeanUtils.copyProperties(p, vo);
        Map<String, Object> u = users.get(p.getUserId());
        if (u != null) {
            String nick = (String) u.get("nickname");
            vo.setNickname(nick == null || nick.isBlank() ? "用户" + p.getUserId() : nick);
            vo.setAvatar((String) u.get("avatar"));
        } else {
            vo.setNickname("用户" + p.getUserId());
        }
        if (p.getSongId() != null) {
            vo.setSong(songs.get(p.getSongId()));
        }
        vo.setLiked(likedPostIds.contains(p.getId()));
        // canDelete：发帖人本人/圈主/圈管理员/平台管理员；canPin：圈主/圈管理员/平台管理员
        boolean manager = platformAdmin || (viewerId != null && managerIds.contains(viewerId));
        vo.setCanDelete(manager || (viewerId != null && viewerId.equals(p.getUserId())));
        vo.setCanPin(manager);
        return vo;
    }

    private CommentVO toCommentVO(CircleComment c, List<CircleComment> replies, Long viewerId,
                                  Map<Long, Map<String, Object>> users, Set<Long> managerIds,
                                  boolean platformAdmin) {
        CommentVO vo = new CommentVO();
        BeanUtils.copyProperties(c, vo);
        Map<String, Object> u = users.get(c.getUserId());
        if (u != null) {
            String nick = (String) u.get("nickname");
            vo.setNickname(nick == null || nick.isBlank() ? "用户" + c.getUserId() : nick);
            vo.setAvatar((String) u.get("avatar"));
        } else {
            vo.setNickname("用户" + c.getUserId());
        }
        boolean manager = platformAdmin || (viewerId != null && managerIds.contains(viewerId));
        vo.setCanDelete(manager || (viewerId != null && viewerId.equals(c.getUserId())));
        if (!CollectionUtils.isEmpty(replies)) {
            vo.setReplies(replies.stream()
                    .map(r -> toCommentVO(r, null, viewerId, users, managerIds, platformAdmin))
                    .collect(Collectors.toList()));
        }
        return vo;
    }

    private boolean postIdOwner(Long userId, CirclePost post) {
        return userId != null && userId.equals(post.getUserId());
    }
}
