package com.qxt.yuexiang.playlist.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qxt.yuexiang.common.exception.BusinessException;
import com.qxt.yuexiang.common.result.ResultCode;
import com.qxt.yuexiang.playlist.dto.PlaylistSaveDTO;
import com.qxt.yuexiang.playlist.entity.Playlist;
import com.qxt.yuexiang.playlist.entity.PlaylistSong;
import com.qxt.yuexiang.playlist.feign.FavoriteClient;
import com.qxt.yuexiang.playlist.feign.SongClient;
import com.qxt.yuexiang.playlist.mapper.PlaylistMapper;
import com.qxt.yuexiang.playlist.mapper.PlaylistSongMapper;
import com.qxt.yuexiang.playlist.service.PlaylistService;
import com.qxt.yuexiang.playlist.vo.PlaylistVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 歌单服务实现
 * <p>
 * 保序规则（对齐详细设计 8）：追加歌曲取当前最大 sort+1；查询按 (playlist_id, sort) 排序；
 * 移除后压缩序号。可见性：官方歌单全站可见，用户歌单仅创建者可见（课设从简，不做 is_public）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlaylistServiceImpl implements PlaylistService {

    private final PlaylistMapper playlistMapper;
    private final PlaylistSongMapper playlistSongMapper;
    private final SongClient songClient;
    private final FavoriteClient favoriteClient;

    // ===== 查询 =====

    @Override
    public List<PlaylistVO> recommend(int limit) {
        List<Playlist> playlists = playlistMapper.selectList(
                new LambdaQueryWrapper<Playlist>()
                        .eq(Playlist::getIsOfficial, 1)
                        .orderByDesc(Playlist::getPlayCount)
                        .last("LIMIT " + limit));
        return toVOList(playlists, null);
    }

    @Override
    public List<PlaylistVO> hot(int limit) {
        // 广场只展示已投稿公开的歌单，私藏歌单不出现在任何公开列表
        List<Playlist> playlists = playlistMapper.selectList(
                new LambdaQueryWrapper<Playlist>()
                        .eq(Playlist::getPublished, 1)
                        .orderByDesc(Playlist::getPlayCount)
                        .last("LIMIT " + limit));
        return toVOList(playlists, null);
    }

    @Override
    public List<PlaylistVO> userCreated(int limit) {
        // 乐友歌单 = 用户主动投稿公开的歌单（published=1），创建≠公开
        List<Playlist> playlists = playlistMapper.selectList(
                new LambdaQueryWrapper<Playlist>()
                        .eq(Playlist::getIsOfficial, 0)
                        .eq(Playlist::getPublished, 1)
                        .orderByDesc(Playlist::getUpdateTime)
                        .last("LIMIT " + limit));
        return toVOList(playlists, null);
    }

    @Override
    public List<PlaylistVO> allVisible(Long userId) {
        // 可见范围：官方歌单 + 自己创建的歌单
        List<Playlist> playlists = playlistMapper.selectList(
                new LambdaQueryWrapper<Playlist>()
                        .and(w -> w.eq(Playlist::getIsOfficial, 1)
                                .or().eq(Playlist::getUserId, userId))
                        .orderByDesc(Playlist::getPlayCount));
        return toVOList(playlists, userId);
    }

    @Override
    public List<PlaylistVO> my(Long userId) {
        List<Playlist> playlists = playlistMapper.selectList(
                new LambdaQueryWrapper<Playlist>()
                        .eq(Playlist::getUserId, userId)
                        .orderByDesc(Playlist::getUpdateTime));
        return toVOList(playlists, userId);
    }

    @Override
    public Map<String, Object> detail(Long playlistId, Long viewerId) {
        Playlist playlist = requirePlaylist(playlistId);
        PlaylistVO vo = toVOList(Collections.singletonList(playlist), viewerId).get(0);

        // 歌曲按 sort 保序输出
        List<PlaylistSong> rels = playlistSongMapper.selectList(
                new LambdaQueryWrapper<PlaylistSong>()
                        .eq(PlaylistSong::getPlaylistId, playlistId)
                        .orderByAsc(PlaylistSong::getSort));
        List<Long> songIds = rels.stream().map(PlaylistSong::getSongId).collect(Collectors.toList());

        // 聚合歌曲信息（song 服务下发歌名/歌手/URL 等）
        List<Map<String, Object>> songs = Collections.emptyList();
        if (!songIds.isEmpty()) {
            try {
                Map<Long, Map<String, Object>> batch = songClient.batch(songIds);
                songs = songIds.stream()
                        .map(batch::get)
                        .filter(java.util.Objects::nonNull)
                        .collect(Collectors.toList());
            } catch (Exception e) {
                log.warn("歌单详情聚合歌曲失败，降级为仅ID: {}", e.getMessage());
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("playlist", vo);
        result.put("songs", songs);
        return result;
    }

    // ===== 写操作 =====

    @Override
    public Long create(Long userId, PlaylistSaveDTO dto) {
        if (dto.getTitle() == null || dto.getTitle().isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "请输入歌单名称");
        }
        Playlist playlist = new Playlist();
        playlist.setUserId(userId);
        playlist.setTitle(dto.getTitle().trim());
        playlist.setCover(dto.getCover());
        playlist.setDescription(dto.getDescription());
        playlist.setTag(dto.getTag());
        playlist.setIsOfficial(0);
        // 新歌单默认私藏，创建者可在编辑中"投稿到广场"
        playlist.setPublished(0);
        playlist.setPlayCount(0L);
        playlistMapper.insert(playlist);
        log.info("歌单已创建: id={}, userId={}, 标题={}", playlist.getId(), userId, dto.getTitle());
        return playlist.getId();
    }

    @Override
    public void update(Long userId, Long playlistId, PlaylistSaveDTO dto) {
        Playlist playlist = requireOwned(userId, playlistId);
        if (dto.getTitle() != null && !dto.getTitle().isBlank()) {
            playlist.setTitle(dto.getTitle().trim());
        }
        if (dto.getCover() != null) {
            playlist.setCover(dto.getCover());
        }
        if (dto.getDescription() != null) {
            playlist.setDescription(dto.getDescription());
        }
        if (dto.getTag() != null) {
            playlist.setTag(dto.getTag());
        }
        if (dto.getPublished() != null) {
            // 投稿状态切换（私藏 ↔ 上传公开）
            playlist.setPublished(dto.getPublished());
        }
        playlistMapper.updateById(playlist);
        log.info("歌单已更新: id={}, userId={}, published={}", playlistId, userId, playlist.getPublished());
    }

    /**
     * 调整歌单内歌曲顺序（仅创建者）：前端传全量 songId 有序列表，按位置重写 sort。
     * 全量覆盖而非单条上移/下移，一次请求落定，前端拖拽/按钮两种交互都能支撑。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reorderSongs(Long userId, Long playlistId, List<Long> songIds) {
        requireOwned(userId, playlistId);
        if (songIds == null || songIds.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "歌曲顺序不能为空");
        }
        List<PlaylistSong> rels = playlistSongMapper.selectList(
                new LambdaQueryWrapper<PlaylistSong>()
                        .eq(PlaylistSong::getPlaylistId, playlistId));
        Map<Long, PlaylistSong> bySong = rels.stream()
                .collect(Collectors.toMap(PlaylistSong::getSongId, r -> r, (a, b) -> a));
        // 传入列表必须恰好覆盖歌单现有歌曲（防丢歌/防脏数据）
        if (songIds.size() != rels.size() || songIds.stream().anyMatch(id -> !bySong.containsKey(id))) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "歌曲列表与歌单内容不一致，请刷新后重试");
        }
        int sort = 1;
        for (Long songId : songIds) {
            PlaylistSong rel = bySong.get(songId);
            if (rel.getSort() == null || rel.getSort() != sort) {
                rel.setSort(sort);
                playlistSongMapper.updateById(rel);
            }
            sort++;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long userId, Long playlistId) {
        requireOwned(userId, playlistId);
        playlistMapper.deleteById(playlistId);
        // 级联清理关联表
        playlistSongMapper.delete(
                new LambdaQueryWrapper<PlaylistSong>()
                        .eq(PlaylistSong::getPlaylistId, playlistId));
        log.info("歌单已删除: id={}, userId={}", playlistId, userId);
    }

    @Override
    public void addSong(Long userId, Long playlistId, Long songId) {
        requireOwned(userId, playlistId);
        // Feign 校验歌曲存在且已发布（对齐详细设计 8：添加前校验存在性，失败不写入）
        Map<Long, Map<String, Object>> batch;
        try {
            batch = songClient.batch(Collections.singletonList(songId));
        } catch (Exception e) {
            throw new BusinessException("歌曲服务暂时不可用，请稍后重试");
        }
        if (!batch.containsKey(songId)) {
            throw new BusinessException(ResultCode.SONG_NOT_EXIST);
        }
        // 唯一约束防重复收录；sort 追加保序
        Long count = playlistSongMapper.selectCount(
                new LambdaQueryWrapper<PlaylistSong>()
                        .eq(PlaylistSong::getPlaylistId, playlistId)
                        .eq(PlaylistSong::getSongId, songId));
        if (count > 0) {
            throw new BusinessException(ResultCode.REPEAT_OPERATION.getCode(), "该歌曲已在歌单中");
        }
        Integer maxSort = playlistSongMapper.selectList(
                        new LambdaQueryWrapper<PlaylistSong>()
                                .eq(PlaylistSong::getPlaylistId, playlistId)
                                .orderByDesc(PlaylistSong::getSort)
                                .last("LIMIT 1"))
                .stream().findFirst().map(PlaylistSong::getSort).orElse(0);

        PlaylistSong rel = new PlaylistSong();
        rel.setPlaylistId(playlistId);
        rel.setSongId(songId);
        rel.setSort(maxSort + 1);
        playlistSongMapper.insert(rel);
    }

    @Override
    public void removeSong(Long userId, Long playlistId, Long songId) {
        requireOwned(userId, playlistId);
        playlistSongMapper.delete(
                new LambdaQueryWrapper<PlaylistSong>()
                        .eq(PlaylistSong::getPlaylistId, playlistId)
                        .eq(PlaylistSong::getSongId, songId));
    }

    @Override
    public void incrPlay(Long playlistId) {
        Playlist playlist = playlistMapper.selectById(playlistId);
        if (playlist != null) {
            playlist.setPlayCount((playlist.getPlayCount() == null ? 0 : playlist.getPlayCount()) + 1);
            playlistMapper.updateById(playlist);
        }
    }

    // ===== 管理端（网关已限 ADMIN，此处不再做创建者归属校验）=====

    @Override
    public List<PlaylistVO> adminList() {
        // 官方在前（电台节目单/首页推荐位），同组内按播放量降序
        List<Playlist> playlists = playlistMapper.selectList(
                new LambdaQueryWrapper<Playlist>()
                        .orderByDesc(Playlist::getIsOfficial)
                        .orderByDesc(Playlist::getPlayCount));
        return toVOList(playlists, null);
    }

    @Override
    public void adminAddSong(Long playlistId, Long songId) {
        requirePlaylist(playlistId);
        // 与 addSong 一致：Feign 校验歌曲存在且已发布
        Map<Long, Map<String, Object>> batch;
        try {
            batch = songClient.batch(Collections.singletonList(songId));
        } catch (Exception e) {
            throw new BusinessException("歌曲服务暂时不可用，请稍后重试");
        }
        if (!batch.containsKey(songId)) {
            throw new BusinessException(ResultCode.SONG_NOT_EXIST);
        }
        Long count = playlistSongMapper.selectCount(
                new LambdaQueryWrapper<PlaylistSong>()
                        .eq(PlaylistSong::getPlaylistId, playlistId)
                        .eq(PlaylistSong::getSongId, songId));
        if (count > 0) {
            throw new BusinessException(ResultCode.REPEAT_OPERATION.getCode(), "该歌曲已在歌单中");
        }
        Integer maxSort = playlistSongMapper.selectList(
                        new LambdaQueryWrapper<PlaylistSong>()
                                .eq(PlaylistSong::getPlaylistId, playlistId)
                                .orderByDesc(PlaylistSong::getSort)
                                .last("LIMIT 1"))
                .stream().findFirst().map(PlaylistSong::getSort).orElse(0);
        PlaylistSong rel = new PlaylistSong();
        rel.setPlaylistId(playlistId);
        rel.setSongId(songId);
        rel.setSort(maxSort + 1);
        playlistSongMapper.insert(rel);
        log.info("管理员加歌: playlistId={}, songId={}, sort={}", playlistId, songId, maxSort + 1);
    }

    @Override
    public void adminRemoveSong(Long playlistId, Long songId) {
        requirePlaylist(playlistId);
        playlistSongMapper.delete(
                new LambdaQueryWrapper<PlaylistSong>()
                        .eq(PlaylistSong::getPlaylistId, playlistId)
                        .eq(PlaylistSong::getSongId, songId));
        log.info("管理员删歌: playlistId={}, songId={}", playlistId, songId);
    }

    // ===== 内部 =====

    private Playlist requirePlaylist(Long playlistId) {
        Playlist playlist = playlistMapper.selectById(playlistId);
        if (playlist == null) {
            throw new BusinessException(ResultCode.PLAYLIST_NOT_EXIST);
        }
        return playlist;
    }

    /** 校验歌单归属（仅创建者可编辑/删除/加歌） */
    private Playlist requireOwned(Long userId, Long playlistId) {
        Playlist playlist = requirePlaylist(playlistId);
        if (!userId.equals(playlist.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "只能操作自己创建的歌单");
        }
        return playlist;
    }

    private List<PlaylistVO> toVOList(List<Playlist> playlists, Long viewerId) {
        if (playlists.isEmpty()) {
            return Collections.emptyList();
        }
        // 歌曲数聚合
        List<Long> ids = playlists.stream().map(Playlist::getId).collect(Collectors.toList());
        Map<Long, Long> countMap = playlistSongMapper.selectList(
                        new LambdaQueryWrapper<PlaylistSong>().in(PlaylistSong::getPlaylistId, ids))
                .stream().collect(Collectors.groupingBy(PlaylistSong::getPlaylistId, Collectors.counting()));

        // 收藏态聚合（viewerId 为空或 favorite 服务不可用时跳过）
        Set<Long> favoredIds = Collections.emptySet();
        if (viewerId != null) {
            try {
                favoredIds = new HashSet<>(favoriteClient.favoredPlaylistIds(viewerId));
            } catch (Exception e) {
                log.warn("聚合收藏态失败，降级为未收藏: {}", e.getMessage());
            }
        }
        final Set<Long> finalFavored = favoredIds;

        return playlists.stream().map(p -> {
            PlaylistVO vo = new PlaylistVO();
            BeanUtils.copyProperties(p, vo);
            vo.setSongCount(countMap.getOrDefault(p.getId(), 0L).intValue());
            vo.setFavored(finalFavored.contains(p.getId()));
            return vo;
        }).collect(Collectors.toList());
    }
}
