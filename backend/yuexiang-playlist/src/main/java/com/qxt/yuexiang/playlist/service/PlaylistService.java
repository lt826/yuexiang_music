package com.qxt.yuexiang.playlist.service;

import com.qxt.yuexiang.playlist.dto.PlaylistSaveDTO;
import com.qxt.yuexiang.playlist.vo.PlaylistVO;

import java.util.List;
import java.util.Map;

/**
 * 歌单服务接口（FUN-07）
 */
public interface PlaylistService {

    /** 官方推荐歌单（已有首页接口） */
    List<PlaylistVO> recommend(int limit);

    /** 热门歌单（全站按播放量，含非官方） */
    List<PlaylistVO> hot(int limit);

    /** 最新用户创建的歌单（歌单广场「乐友歌单」区） */
    List<PlaylistVO> userCreated(int limit);

    /** 创建歌单（FUN-07：标题非空校验），返回新歌单 ID */
    Long create(Long userId, PlaylistSaveDTO dto);

    /** 编辑歌单信息（仅创建者） */
    void update(Long userId, Long playlistId, PlaylistSaveDTO dto);

    /** 删除歌单（仅创建者，级联清理关联表） */
    void remove(Long userId, Long playlistId);

    /** 我的歌单列表 */
    List<PlaylistVO> my(Long userId);

    /** 歌单详情（含歌曲ID有序列表；公开歌单任何人可看） */
    Map<String, Object> detail(Long playlistId, Long viewerId);

    /** 添加歌曲（Feign 校验歌曲存在且已发布；sort 追加保序） */
    void addSong(Long userId, Long playlistId, Long songId);

    /** 移除歌曲（仅创建者） */
    void removeSong(Long userId, Long playlistId, Long songId);

    /** 调整歌曲顺序（仅创建者；传全量 songId 有序列表覆盖 sort） */
    void reorderSongs(Long userId, Long playlistId, List<Long> songIds);

    /** 我收藏的歌单 ID 集合（favorite 服务数据，经其接口聚合——见 favorite 模块） */
    List<PlaylistVO> allVisible(Long userId);

    /** 播放量 +1（打开详情时） */
    void incrPlay(Long playlistId);

    // ===== 管理端（/playlist/admin/**，网关限 ADMIN；官方歌单/电台节目单维护）=====

    /** 管理端歌单列表（官方 + 用户公开，按官方在前、播放量降序） */
    List<PlaylistVO> adminList();

    /** 管理员加歌（豁免创建者归属校验；歌曲存在性校验与 addSong 一致） */
    void adminAddSong(Long playlistId, Long songId);

    /** 管理员删歌（豁免创建者归属校验） */
    void adminRemoveSong(Long playlistId, Long songId);
}
