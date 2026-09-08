package com.qxt.yuexiang.circle.controller;

import com.qxt.yuexiang.circle.dto.CommentCreateDTO;
import com.qxt.yuexiang.circle.dto.PostCreateDTO;
import com.qxt.yuexiang.circle.service.PostService;
import com.qxt.yuexiang.circle.vo.CommentVO;
import com.qxt.yuexiang.circle.vo.PostVO;
import com.qxt.yuexiang.common.result.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 乐坊帖子/评论/点赞接口
 * <p>
 * 帖子流与评论列表匿名可看（网关白名单 /circle/posts/**）；
 * 发帖/评论/点赞/删除登录后访问。权限在 service 层校验。
 */
@RestController
@RequestMapping("/circle")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    /** POST /circle/{id}/posts 发帖（圈内成员） */
    @PostMapping("/{id}/posts")
    public Result<Long> createPost(@RequestHeader("X-User-Id") Long userId,
                                   @PathVariable("id") Long circleId,
                                   @RequestBody PostCreateDTO dto) {
        return Result.success(postService.createPost(userId, circleId, dto));
    }

    /** GET /circle/{circleId}/posts 帖子列表（匿名可看；viewerId 可选） */
    @GetMapping("/{circleId}/posts")
    public Result<Map<String, Object>> posts(
            @RequestHeader(value = "X-User-Id", required = false) Long viewerId,
            @RequestHeader(value = "X-User-Role", required = false) String viewerRole,
            @PathVariable("circleId") Long circleId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        List<PostVO> items = postService.posts(viewerId, viewerRole, circleId, page, size);
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("items", items);
        result.put("page", page);
        result.put("size", size);
        return Result.success(result);
    }

    /** DELETE /circle/post/{postId} 删帖（发帖人/圈主/圈管理员/平台管理员） */
    @DeleteMapping("/post/{postId}")
    public Result<Void> deletePost(@RequestHeader("X-User-Id") Long userId,
                                   @RequestHeader(value = "X-User-Role", required = false) String viewerRole,
                                   @PathVariable("postId") Long postId) {
        postService.deletePost(userId, viewerRole, postId);
        return Result.success();
    }

    /** POST /circle/post/{postId}/pin 置顶；POST /circle/post/{postId}/unpin 取消置顶（圈主/圈管理员/平台管理员） */
    @PostMapping("/post/{postId}/pin")
    public Result<Void> pin(@RequestHeader("X-User-Id") Long userId,
                            @RequestHeader(value = "X-User-Role", required = false) String viewerRole,
                            @PathVariable("postId") Long postId) {
        postService.pinPost(userId, viewerRole, postId, true);
        return Result.success();
    }

    @PostMapping("/post/{postId}/unpin")
    public Result<Void> unpin(@RequestHeader("X-User-Id") Long userId,
                              @RequestHeader(value = "X-User-Role", required = false) String viewerRole,
                              @PathVariable("postId") Long postId) {
        postService.pinPost(userId, viewerRole, postId, false);
        return Result.success();
    }

    /** POST /circle/post/{postId}/like 点赞/取消点赞（登录，幂等切换） */
    @PostMapping("/post/{postId}/like")
    public Result<Void> toggleLike(@RequestHeader("X-User-Id") Long userId,
                                   @PathVariable("postId") Long postId) {
        postService.toggleLike(userId, postId);
        return Result.success();
    }

    /** GET /circle/post/{postId}/comments 评论列表楼中楼（匿名可看） */
    @GetMapping("/post/{postId}/comments")
    public Result<List<CommentVO>> comments(
            @RequestHeader(value = "X-User-Id", required = false) Long viewerId,
            @RequestHeader(value = "X-User-Role", required = false) String viewerRole,
            @PathVariable("postId") Long postId) {
        return Result.success(postService.comments(viewerId, viewerRole, postId));
    }

    /** POST /circle/post/{postId}/comment 评论/回复评论（圈内成员） */
    @PostMapping("/post/{postId}/comment")
    public Result<Long> comment(@RequestHeader("X-User-Id") Long userId,
                                @PathVariable("postId") Long postId,
                                @RequestBody CommentCreateDTO dto) {
        return Result.success(postService.comment(userId, postId, dto));
    }

    /** DELETE /circle/comment/{commentId} 删评论（发评人/圈主/圈管理员/平台管理员） */
    @DeleteMapping("/comment/{commentId}")
    public Result<Void> deleteComment(@RequestHeader("X-User-Id") Long userId,
                                      @RequestHeader(value = "X-User-Role", required = false) String viewerRole,
                                      @PathVariable("commentId") Long commentId) {
        postService.deleteComment(userId, viewerRole, commentId);
        return Result.success();
    }
}
