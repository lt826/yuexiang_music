package com.qxt.yuexiang.song.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qxt.yuexiang.song.entity.Song;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 歌曲 Mapper
 */
@Mapper
public interface SongMapper extends BaseMapper<Song> {

    /**
     * 子串模糊检索（对齐主流音乐 App：搜"晴"命中所有含"晴"的歌曲）
     * 命中字段：歌名 / 歌词 / 风格，仅搜已发布歌曲
     * LIKE '%kw%' 逃不开全表扫描，但课程数据量（千级）完全可接受
     */
    @Select("SELECT * FROM song s " +
            "WHERE s.deleted = 0 AND s.status = 1 " +
            "AND (s.name LIKE CONCAT('%',#{keyword},'%') " +
            "     OR s.lyric LIKE CONCAT('%',#{keyword},'%') " +
            "     OR s.style LIKE CONCAT('%',#{keyword},'%')) " +
            "ORDER BY (s.name LIKE CONCAT('%',#{keyword},'%')) DESC, s.play_count DESC " +
            "LIMIT #{offset}, #{size}")
    List<Song> searchByKeyword(@Param("keyword") String keyword,
                               @Param("offset") int offset,
                               @Param("size") int size);

    /** 子串命中总数（与 searchByKeyword 同条件） */
    @Select("SELECT COUNT(*) FROM song s " +
            "WHERE s.deleted = 0 AND s.status = 1 " +
            "AND (s.name LIKE CONCAT('%',#{keyword},'%') " +
            "     OR s.lyric LIKE CONCAT('%',#{keyword},'%') " +
            "     OR s.style LIKE CONCAT('%',#{keyword},'%'))")
    long countByKeyword(@Param("keyword") String keyword);

    /** 含歌手名联搜的子串检索（keyword 命中歌名/歌词/风格，或歌手名含 keyword 的歌手的作品） */
    @Select("<script>SELECT * FROM song s " +
            "WHERE s.deleted = 0 AND s.status = 1 " +
            "AND (s.name LIKE CONCAT('%',#{keyword},'%') " +
            "     OR s.lyric LIKE CONCAT('%',#{keyword},'%') " +
            "     OR s.style LIKE CONCAT('%',#{keyword},'%') " +
            "<if test='singerIds != null and singerIds.size() > 0'>" +
            "     OR s.singer_id IN " +
            "     <foreach collection='singerIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</if>) " +
            "ORDER BY (s.name LIKE CONCAT('%',#{keyword},'%')) DESC, s.play_count DESC " +
            "LIMIT #{offset}, #{size}</script>")
    List<Song> searchWithSingers(@Param("keyword") String keyword,
                                 @Param("singerIds") List<Long> singerIds,
                                 @Param("offset") int offset,
                                 @Param("size") int size);

    /** 含歌手名联搜的命中总数（与 searchWithSingers 同条件） */
    @Select("<script>SELECT COUNT(*) FROM song s " +
            "WHERE s.deleted = 0 AND s.status = 1 " +
            "AND (s.name LIKE CONCAT('%',#{keyword},'%') " +
            "     OR s.lyric LIKE CONCAT('%',#{keyword},'%') " +
            "     OR s.style LIKE CONCAT('%',#{keyword},'%') " +
            "<if test='singerIds != null and singerIds.size() > 0'>" +
            "     OR s.singer_id IN " +
            "     <foreach collection='singerIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</if>)</script>")
    long countWithSingers(@Param("keyword") String keyword,
                          @Param("singerIds") List<Long> singerIds);

    /**
     * 歌词紧凑匹配（原始 LIKE 未命中时的兜底）：LRC 时间戳与换行把歌词逐行拆断，
     * 用户凭记忆连报两句歌词时原始 LIKE 必然失配；双方都剥离时间戳与全部非文字字符
     * （\\W 在 ICU 正则下保留汉字/字母/数字）后再比对，跨行、带标点的片段也能命中。
     * keyword 剥离后为空（纯符号输入）时 IF 短路返回不命中，避免 LIKE '%%' 全表命中。
     */
    @Select("SELECT * FROM song s " +
            "WHERE s.deleted = 0 AND s.status = 1 " +
            "AND IF(CHAR_LENGTH(REGEXP_REPLACE(#{keyword}, '\\\\W', '')) > 0, " +
            "REGEXP_REPLACE(s.lyric, '\\\\[[0-9:.]+\\\\]|\\\\W', '') " +
            "LIKE CONCAT('%', REGEXP_REPLACE(#{keyword}, '\\\\W', ''), '%'), 0) " +
            "ORDER BY s.play_count DESC " +
            "LIMIT #{offset}, #{size}")
    List<Song> searchByLyricCompact(@Param("keyword") String keyword,
                                    @Param("offset") int offset,
                                    @Param("size") int size);

    /** 歌词紧凑匹配命中总数（与 searchByLyricCompact 同条件） */
    @Select("SELECT COUNT(*) FROM song s " +
            "WHERE s.deleted = 0 AND s.status = 1 " +
            "AND IF(CHAR_LENGTH(REGEXP_REPLACE(#{keyword}, '\\\\W', '')) > 0, " +
            "REGEXP_REPLACE(s.lyric, '\\\\[[0-9:.]+\\\\]|\\\\W', '') " +
            "LIKE CONCAT('%', REGEXP_REPLACE(#{keyword}, '\\\\W', ''), '%'), 0)")
    long countByLyricCompact(@Param("keyword") String keyword);

    /** 热门歌手ID：按已发布歌曲总播放量倒序（singer 服务推荐歌手排序用） */
    @Select("SELECT singer_id, SUM(play_count) AS total FROM song " +
            "WHERE deleted = 0 AND status = 1 " +
            "GROUP BY singer_id " +
            "ORDER BY total DESC " +
            "LIMIT #{limit}")
    List<Map<String, Object>> selectHotSingerIds(@Param("limit") int limit);

    /** 已发布歌曲总播放量（门户统计用） */
    @Select("SELECT COALESCE(SUM(play_count), 0) FROM song WHERE deleted = 0 AND status = 1")
    long sumPublishedPlayCount();

    /** 指定歌手的已发布歌曲，排除 excludeIds（猜你喜欢：该歌手的"其它"歌），按播放量倒序 */
    @Select("<script>SELECT * FROM song " +
            "WHERE deleted = 0 AND status = 1 AND singer_id = #{singerId} " +
            "<if test='excludeIds != null and excludeIds.size() > 0'>" +
            " AND id NOT IN " +
            " <foreach collection='excludeIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</if> " +
            "ORDER BY play_count DESC LIMIT #{limit}</script>")
    List<Song> selectBySingerExclude(@Param("singerId") Long singerId,
                                     @Param("excludeIds") List<Long> excludeIds,
                                     @Param("limit") int limit);

    /** 已发布歌曲随机取 N 首，排除 excludeIds（猜你喜欢无画像时的随机兜底） */
    @Select("<script>SELECT * FROM song " +
            "WHERE deleted = 0 AND status = 1 " +
            "<if test='excludeIds != null and excludeIds.size() > 0'>" +
            " AND id NOT IN " +
            " <foreach collection='excludeIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</if> " +
            "ORDER BY RAND() LIMIT #{limit}</script>")
    List<Song> selectRandomExclude(@Param("excludeIds") List<Long> excludeIds,
                                   @Param("limit") int limit);

    /**
     * 推荐候选池：某风格内按播放量取 TOP（加权混采的名额池），排除 excludeIds。
     * 风格为空字符串时不过滤风格（探索位/兜底用全曲库热门）。
     */
    @Select("<script>SELECT * FROM song " +
            "WHERE deleted = 0 AND status = 1 " +
            "<if test='style != null and style != \"\"'>" +
            " AND style = #{style} " +
            "</if>" +
            "<if test='excludeIds != null and excludeIds.size() > 0'>" +
            " AND id NOT IN " +
            " <foreach collection='excludeIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</if> " +
            "ORDER BY play_count DESC LIMIT #{limit}</script>")
    List<Song> selectTopCandidates(@Param("style") String style,
                                   @Param("excludeIds") List<Long> excludeIds,
                                   @Param("limit") int limit);

    /** 全部已发布的非空风格（风格画像统计的取值域） */
    @Select("SELECT DISTINCT style FROM song WHERE deleted = 0 AND status = 1 " +
            "AND style IS NOT NULL AND style != ''")
    List<String> selectAllStyles();

    /** 管理端全量歌曲列表：含待审/下架，关键字命中歌名/风格，按提交时间倒序 */
    @Select("<script>SELECT * FROM song WHERE deleted = 0 " +
            "<if test='keyword != null and keyword != \"\"'>" +
            " AND (name LIKE CONCAT('%',#{keyword},'%') OR style LIKE CONCAT('%',#{keyword},'%')) " +
            "</if>" +
            "ORDER BY create_time DESC, id DESC LIMIT #{offset}, #{size}</script>")
    List<Song> selectAdminList(@Param("keyword") String keyword,
                               @Param("offset") int offset,
                               @Param("size") int size);

    /** 管理端全量歌曲计数（与 selectAdminList 同条件） */
    @Select("<script>SELECT COUNT(*) FROM song WHERE deleted = 0 " +
            "<if test='keyword != null and keyword != \"\"'>" +
            " AND (name LIKE CONCAT('%',#{keyword},'%') OR style LIKE CONCAT('%',#{keyword},'%')) " +
            "</if></script>")
    long countAdminList(@Param("keyword") String keyword);
}
