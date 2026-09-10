package com.qxt.yuexiang.circle.service;

import com.qxt.yuexiang.circle.dto.CommentCreateDTO;
import com.qxt.yuexiang.circle.dto.PostCreateDTO;
import com.qxt.yuexiang.circle.vo.CommentVO;
import com.qxt.yuexiang.circle.vo.PostVO;

import java.util.List;

/**
 * 乐坊帖子服务：发帖（插歌曲卡片）/帖子列表（置顶优先）/评论楼中楼/点赞
 */
public interface PostService {

    /** 发帖（圈内成员；songId 可选，校验歌曲存在） */
    Long createPost(Long userId, Long circleId, PostCreateDTO dto);

    /** 帖子列表：置顶在前 + 发布时间倒序（匿名可看） */
    List<PostVO> posts(Long viewerId, String viewerRole, Long circleId, int page, int size);

    /** 删帖：发帖人本人 / 圈主 / 圈管理员 / 平台管理员 */
    void deletePost(Long userId, String viewerRole, Long postId);

    /** 置顶/取消置顶（圈主/圈管理员/平台管理员，最多 2 条） */
    void pinPost(Long userId, String viewerRole, Long postId, boolean pinned);

    /** 帖子评论列表（楼中楼：顶层评论 + replies，匿名可看） */
    List<CommentVO> comments(Long viewerId, String viewerRole, Long postId);

    /** 发表评论/回复评论（圈内成员） */
    Long comment(Long userId, Long postId, CommentCreateDTO dto);

    /** 删评论：发评人本人 / 圈主 / 圈管理员 / 平台管理员（删顶层评论连回复一起删） */
    void deleteComment(Long userId, String viewerRole, Long commentId);

    /** 点赞/取消点赞（登录用户） */
    void toggleLike(Long userId, Long postId);
}
