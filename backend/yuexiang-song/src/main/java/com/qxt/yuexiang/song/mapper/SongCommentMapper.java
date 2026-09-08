package com.qxt.yuexiang.song.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qxt.yuexiang.song.entity.SongComment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 歌曲评论 Mapper
 */
@Mapper
public interface SongCommentMapper extends BaseMapper<SongComment> {

    /**
     * 批量取每个根评论的最新 N 条回复（回复预览用）。
     * MySQL 8 窗口函数：按 root_id 分组、组内按 id 倒序编号，取 rn ≤ N。
     * 返回按 root_id 正序、组内 id 正序（展示顺序由 service 按插入序重排为最新在前）。
     */
    @Select("<script>SELECT * FROM (" +
            " SELECT c.*, ROW_NUMBER() OVER (PARTITION BY c.root_id ORDER BY c.id DESC) AS rn" +
            " FROM song_comment c" +
            " WHERE c.deleted = 0 AND c.root_id IN" +
            " <foreach collection='rootIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            ") t WHERE t.rn &lt;= #{limit}</script>")
    List<SongComment> selectPreviewReplies(@Param("rootIds") List<Long> rootIds,
                                           @Param("limit") int limit);
}
