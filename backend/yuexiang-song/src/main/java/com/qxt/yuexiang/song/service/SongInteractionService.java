package com.qxt.yuexiang.song.service;

import com.qxt.yuexiang.song.dto.CommentPostDTO;
import com.qxt.yuexiang.song.vo.CommentVO;
import com.qxt.yuexiang.song.vo.SongInteractionVO;

import java.util.Map;

/**
 * 歌曲互动服务：歌曲点赞 + 评论体系（两级楼中楼、评论点赞、推荐/最热/最新排序）
 */
public interface SongInteractionService {

    /**
     * 查询歌曲互动聚合（点赞数/是否已赞/评论数）。
     *
     * @param songId 歌曲ID
     * @param userId 当前登录用户ID，匿名传 null
     */
    SongInteractionVO interaction(Long songId, Long userId);

    /**
     * 点赞：重复点赞抛 REPEAT_OPERATION。
     *
     * @return 点赞后的最新总数
     */
    long like(Long userId, Long songId);

    /**
     * 取消点赞（未赞过静默成功）。
     *
     * @return 取消后的最新总数
     */
    long unlike(Long userId, Long songId);

    /**
     * 根评论分页（聚合评论人昵称/头像、点赞数/是否已赞、回复数与最新 2 条回复预览）。
     *
     * @param sort hot=最热(点赞多→回复多→时间新) / time=最新 / recommend=推荐(热度和时间加权)
     * @return {items: List<CommentVO>, total, page, size}
     */
    Map<String, Object> comments(Long songId, Long userId, String sort, int page, int size);

    /**
     * 某根评论下的回复分页（时间正序，聚合昵称/头像/点赞态）。
     *
     * @return {items: List<CommentVO>, total, root: CommentVO}
     */
    Map<String, Object> replies(Long commentId, Long userId, int page, int size);

    /**
     * 发表评论/回复：歌曲须存在，内容 1~500 字；回复时 rootId 必须是本歌的根评论，
     * 成功后给被回复人发消息通知（失败不阻塞）。
     *
     * @return 含评论人昵称/头像的完整条目（前端直接插入列表）
     */
    CommentVO postComment(Long userId, Long songId, CommentPostDTO dto);

    /**
     * 删除自己的评论（非本人抛 FORBIDDEN，逻辑删除）。
     * 根评论被删时级联删除其全部回复（楼中楼不出现孤儿楼层）。
     */
    void deleteComment(Long userId, Long commentId);

    /**
     * 评论点赞：一人一评一次，重复抛 REPEAT_OPERATION；冗余 like_count 同步 +1。
     *
     * @return 点赞后的最新点赞数
     */
    long likeComment(Long userId, Long commentId);

    /**
     * 取消评论点赞（未赞过静默成功），冗余 like_count 同步 -1（不小于 0）。
     *
     * @return 取消后的最新点赞数
     */
    long unlikeComment(Long userId, Long commentId);
}
