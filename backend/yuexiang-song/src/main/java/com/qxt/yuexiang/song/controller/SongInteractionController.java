package com.qxt.yuexiang.song.controller;

import com.qxt.yuexiang.common.result.Result;
import com.qxt.yuexiang.song.dto.CommentPostDTO;
import com.qxt.yuexiang.song.service.SongInteractionService;
import com.qxt.yuexiang.song.vo.CommentVO;
import com.qxt.yuexiang.song.vo.SongInteractionVO;
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

import java.util.Map;

/**
 * 歌曲互动接口：点赞 + 评论。
 * 查询（interaction/comments）匿名可看（网关白名单按 GET 放行），
 * 写操作（点赞/发评/删评）必须登录，身份经网关校验 JWT 后透传 X-User-Id。
 */
@RestController
@RequestMapping("/song")
@RequiredArgsConstructor
public class SongInteractionController {

    private final SongInteractionService songInteractionService;

    /** GET /song/{id}/interaction 互动聚合：点赞数/是否已赞/评论数（播放页顶栏） */
    @GetMapping("/{id}/interaction")
    public Result<SongInteractionVO> interaction(
            @PathVariable("id") Long songId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        return Result.success(songInteractionService.interaction(songId, userId));
    }

    /** POST /song/{id}/like 点赞（一人一歌一次，重复抛 3001）→ 返回最新总数 */
    @PostMapping("/{id}/like")
    public Result<Long> like(@PathVariable("id") Long songId,
                             @RequestHeader("X-User-Id") Long userId) {
        return Result.success(songInteractionService.like(userId, songId));
    }

    /** DELETE /song/{id}/like 取消点赞 → 返回最新总数 */
    @DeleteMapping("/{id}/like")
    public Result<Long> unlike(@PathVariable("id") Long songId,
                               @RequestHeader("X-User-Id") Long userId) {
        return Result.success(songInteractionService.unlike(userId, songId));
    }

    /** GET /song/{id}/comments 根评论分页（sort: recommend推荐/hot最热/time最新，默认最新；含点赞态与回复预览），匿名可看 */
    @GetMapping("/{id}/comments")
    public Result<Map<String, Object>> comments(
            @PathVariable("id") Long songId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(songInteractionService.comments(songId, userId, sort, page, size));
    }

    /** GET /song/comment/{commentId}/replies 某根评论下的回复分页（时间正序），匿名可看 */
    @GetMapping("/comment/{commentId}/replies")
    public Result<Map<String, Object>> replies(
            @PathVariable("commentId") Long commentId,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(songInteractionService.replies(commentId, userId, page, size));
    }

    /** POST /song/{id}/comment 发表评论/回复（rootId 有值=回复）→ 返回完整条目（前端直接插入列表） */
    @PostMapping("/{id}/comment")
    public Result<CommentVO> postComment(@PathVariable("id") Long songId,
                                         @RequestHeader("X-User-Id") Long userId,
                                         @RequestBody CommentPostDTO dto) {
        return Result.success(songInteractionService.postComment(userId, songId, dto));
    }

    /** DELETE /song/comment/{commentId} 删除自己的评论（非本人 403；根评论级联删回复） */
    @DeleteMapping("/comment/{commentId}")
    public Result<Void> deleteComment(@PathVariable("commentId") Long commentId,
                                      @RequestHeader("X-User-Id") Long userId) {
        songInteractionService.deleteComment(userId, commentId);
        return Result.success();
    }

    /** POST /song/comment/{commentId}/like 评论点赞（一人一评一次，重复抛 3001）→ 返回最新点赞数 */
    @PostMapping("/comment/{commentId}/like")
    public Result<Long> likeComment(@PathVariable("commentId") Long commentId,
                                    @RequestHeader("X-User-Id") Long userId) {
        return Result.success(songInteractionService.likeComment(userId, commentId));
    }

    /** DELETE /song/comment/{commentId}/like 取消评论点赞 → 返回最新点赞数 */
    @DeleteMapping("/comment/{commentId}/like")
    public Result<Long> unlikeComment(@PathVariable("commentId") Long commentId,
                                      @RequestHeader("X-User-Id") Long userId) {
        return Result.success(songInteractionService.unlikeComment(userId, commentId));
    }
}
