package com.qxt.yuexiang.song.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.qxt.yuexiang.common.exception.BusinessException;
import com.qxt.yuexiang.common.result.ResultCode;
import com.qxt.yuexiang.song.dto.SongPublishDTO;
import com.qxt.yuexiang.song.entity.Song;
import com.qxt.yuexiang.song.feign.FavoriteClient;
import com.qxt.yuexiang.song.feign.MessageClient;
import com.qxt.yuexiang.song.feign.SingerClient;
import com.qxt.yuexiang.song.mapper.SongMapper;
import com.qxt.yuexiang.song.mq.PlayEventPublisher;
import com.qxt.yuexiang.song.service.SongRedisService;
import com.qxt.yuexiang.song.service.SongService;
import com.qxt.yuexiang.song.vo.SearchResultVO;
import com.qxt.yuexiang.song.vo.SongVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 歌曲服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SongServiceImpl implements SongService {

    private final SongMapper songMapper;
    private final SingerClient singerClient;
    private final MessageClient messageClient;
    private final FavoriteClient favoriteClient;
    private final SongRedisService songRedisService;
    private final PlayEventPublisher playEventPublisher;

    @Override
    public List<SongVO> hotChart(int limit) {
        List<Song> songs = songMapper.selectList(
                new LambdaQueryWrapper<Song>()
                        .eq(Song::getStatus, 1)
                        .orderByDesc(Song::getPlayCount)
                        .last("LIMIT " + limit));
        return fillSingerName(toVOList(songs));
    }

    /**
     * 实时热歌榜：以 Redis ZSet 热度为序，MySQL 只按 ID 批量补详情。
     * 榜空（新库/Redis 故障）回退播放量排序的经典热门榜，接口永远有值。
     */
    @Override
    public List<SongVO> realtimeChart(int limit) {
        List<Long> songIds = songRedisService.topPlayedSongIds(limit);
        if (songIds.isEmpty()) {
            return hotChart(limit);
        }
        List<Song> songs = songMapper.selectBatchIds(songIds);
        // 榜单序保留 Redis 排序；过滤已下架/已删除（selectBatchIds 走逻辑删除，但状态需单独过滤）
        Map<Long, Song> byId = songs.stream()
                .collect(Collectors.toMap(Song::getId, s -> s, (a, b) -> a));
        List<SongVO> result = new ArrayList<>();
        for (Long id : songIds) {
            Song song = byId.get(id);
            if (song == null || (song.getStatus() != null && song.getStatus() != 1)) {
                continue;
            }
            SongVO vo = new SongVO();
            BeanUtils.copyProperties(song, vo);
            result.add(vo);
        }
        return fillSingerName(result);
    }

    @Override
    public List<SongVO> newSongs(int limit) {
        List<Song> songs = songMapper.selectList(
                new LambdaQueryWrapper<Song>()
                        .eq(Song::getStatus, 1)
                        .orderByDesc(Song::getCreateTime)
                        .orderByDesc(Song::getId)
                        .last("LIMIT " + limit));
        return fillSingerName(toVOList(songs));
    }

    /**
     * 推荐画像信号强度：风格分母系数。取值越大画像越平滑（冷用户也能出全量风格配比），
     * 越小画像越尖锐（少量历史就形成强偏好）。40 ≈ 20 次收听时画像权重达 ~0.67。
     */
    private static final int PROFILE_SMOOTHING = 40;

    /** 探索位比例（分子分母约 1/8）：每 8 首里留 1 首全站热门，防信息茧房并收集新风格反馈 */
    private static final int EXPLORE_NUMERATOR = 1;
    private static final int EXPLORE_DENOMINATOR = 8;

    /** 单风格候选池深度：按播放量取该风格 TOP 池，混采时在池内随机，保证头部歌曲优先露出 */
    private static final int POOL_DEPTH_PER_STYLE = 30;

    /**
     * 个性化推荐：风格标签画像 + 加权混采（对齐详细设计 6.2 个性化推荐策略）。
     *
     * <p>双信号画像：收听历史按时间衰减计分（最近第 i 次听歌权重 1/√(i+1)，反映兴趣漂移），
     * 收藏为强信号一次计 5 次收听分（用户主动行为，价值远高于被动播放）。
     * 得到"最近行为 × 风格分布"的偏好向量后做拉普拉斯平滑，避免低频风格被直接清零。</p>
     *
     * <p>加权混采：按风格占比用最大余数法（Hamilton）把 limit 个名额分配到各风格
     * （保证总数恰为 limit 且无系统偏差），每个名额从该风格 TOP 候选池内随机抽取；
     * 末尾保留探索位给全站热门。历史歌不重复推荐。</p>
     *
     * <p>兜底链：画像为空（未登录/无任何行为）或候选池耗尽 → 热门榜补齐；
     * Feign 失败同降级为热门榜，保证接口永远可用。</p>
     */
    @Override
    public List<SongVO> recommend(Long userId, int limit) {
        if (userId == null) {
            return hotChart(limit);
        }

        // 画像三要素：历史分 + 收藏分 + 已听排除集
        Set<Long> excludeIds = new LinkedHashSet<>();
        Map<String, Double> styleScore = new HashMap<>();
        boolean profileFailed = false;
        try {
            // 时间衰减收听历史：取最近 50 条（去重后约覆盖全部近期行为），越近权重越高
            List<Long> historySongIds = favoriteClient.latestSongIds(userId, 50);
            for (int i = 0; i < historySongIds.size(); i++) {
                Song song = songMapper.selectById(historySongIds.get(i));
                if (song == null || song.getStyle() == null || song.getStyle().isBlank()) {
                    continue;
                }
                excludeIds.add(song.getId());
                // 1/√(i+1) 衰减：第 1 次权重 1.0，第 9 次已衰减到 0.32
                styleScore.merge(song.getStyle(), 1.0 / Math.sqrt(i + 1), Double::sum);
            }

            // 收藏强信号：一次收藏抵 5 次收听（衰减取中值档）
            List<Long> favoriteSongIds = favoriteClient.favoriteSongIds(userId);
            for (Long songId : favoriteSongIds) {
                Song song = songMapper.selectById(songId);
                if (song == null || song.getStyle() == null || song.getStyle().isBlank()) {
                    continue;
                }
                excludeIds.add(song.getId());
                styleScore.merge(song.getStyle(), 5.0 / Math.sqrt(3), Double::sum);
            }
        } catch (Exception e) {
            log.warn("推荐画像构建失败，降级热门榜: userId={}, {}", userId, e.getMessage());
            profileFailed = true;
        }

        // 无画像（新用户/服务故障）→ 热门榜兜底（详细设计 6.2 冷启动策略）
        if (profileFailed || styleScore.isEmpty()) {
            return hotChart(limit);
        }

        // 拉普拉斯平滑：分母系数使小样本用户也能产出非零的全风格配比
        List<String> allStyles = songMapper.selectAllStyles();
        for (String style : allStyles) {
            styleScore.merge(style, 1.0 / PROFILE_SMOOTHING, Double::sum);
        }

        // 探索位名额（至少留一半给个性化，避免少量名额时被探索吞掉）
        int exploreSlots = Math.max(1, limit * EXPLORE_NUMERATOR / EXPLORE_DENOMINATOR);
        exploreSlots = Math.min(exploreSlots, limit / 2);
        int personalSlots = limit - exploreSlots;

        // 最大余数法名额分配：按风格得分占比分 personalSlots 个名额，余数大者优先补齐
        double totalScore = styleScore.values().stream().mapToDouble(Double::doubleValue).sum();
        Map<String, Integer> quotas = new LinkedHashMap<>();
        int allocated = 0;
        List<Map.Entry<String, Double>> remainders = new ArrayList<>();
        for (Map.Entry<String, Double> e : styleScore.entrySet()) {
            double exact = e.getValue() / totalScore * personalSlots;
            int floor = (int) exact;
            if (floor > 0) {
                quotas.put(e.getKey(), floor);
                allocated += floor;
            }
            remainders.add(Map.entry(e.getKey(), exact - floor));
        }
        remainders.sort(Map.Entry.<String, Double>comparingByValue().reversed());
        for (int i = 0; allocated < personalSlots && i < remainders.size(); i++, allocated++) {
            quotas.merge(remainders.get(i).getKey(), 1, Integer::sum);
        }

        // 逐风格从候选池混采：池内随机，打碎纯播放量排序，同风格多名额也能出中尾部歌曲
        List<Song> picked = new ArrayList<>();
        for (Map.Entry<String, Integer> quota : quotas.entrySet()) {
            List<Song> pool = songMapper.selectTopCandidates(
                    quota.getKey(), new ArrayList<>(excludeIds), POOL_DEPTH_PER_STYLE);
            if (pool.isEmpty()) {
                continue;
            }
            Collections.shuffle(pool);
            for (int i = 0; i < quota.getValue() && !pool.isEmpty(); i++) {
                Song song = pool.remove(pool.size() - 1);
                picked.add(song);
                excludeIds.add(song.getId());
            }
        }

        // 探索位：全站热门补齐（候选不足、画像失衡时也由此兜底）
        if (picked.size() < limit) {
            picked.addAll(songMapper.selectTopCandidates(
                    "", new ArrayList<>(excludeIds), limit - picked.size()));
        }

        List<SongVO> result = fillSingerName(toVOList(picked));
        log.info("个性化推荐完成: userId={}, limit={}, 画像风格数={}, 候选命中={}",
                userId, limit, styleScore.size(), result.size());
        return result;
    }

    /**
     * 猜你喜欢：收听历史第 1 首歌的歌手的其它歌 → 不够 10 首看第 2 首的歌手 → …
     * 全部耗尽或无收听历史时从曲库随机补齐。userId 为空（未登录）直接随机。
     */
    @Override
    public List<SongVO> guessYouLike(Long userId, int limit) {
        List<Song> picked = new ArrayList<>();
        // 已听过的歌不推荐：历史歌一开始就整体进排除集
        List<Long> excludeIds = new ArrayList<>();

        if (userId != null) {
            // 收听历史画像：最近收听的歌曲ID（倒序去重）
            List<Long> historySongIds = Collections.emptyList();
            try {
                historySongIds = favoriteClient.latestSongIds(userId, limit);
            } catch (Exception e) {
                log.warn("猜你喜欢获取收听历史失败，走随机兜底: {}", e.getMessage());
            }
            excludeIds.addAll(historySongIds);

            // 逐首历史歌 → 其歌手的其它歌（排除已听与已选），直到凑满
            for (Long historySongId : historySongIds) {
                if (picked.size() >= limit) {
                    break;
                }
                Song historySong = songMapper.selectById(historySongId);
                if (historySong == null || historySong.getSingerId() == null) {
                    continue;
                }
                List<Song> more = songMapper.selectBySingerExclude(
                        historySong.getSingerId(), excludeIds, limit - picked.size());
                for (Song s : more) {
                    if (picked.size() >= limit) {
                        break;
                    }
                    picked.add(s);
                    excludeIds.add(s.getId());
                }
            }
        }

        // 随机兜底：画像耗尽/无历史/未登录，从曲库剩余歌曲随机补齐
        if (picked.size() < limit) {
            picked.addAll(songMapper.selectRandomExclude(excludeIds, limit - picked.size()));
        }
        return fillSingerName(toVOList(picked));
    }

    @Override
    public SearchResultVO search(String keyword, Long userId, int page, int size) {
        String kw = keyword == null ? "" : keyword.trim();
        if (kw.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "请输入搜索关键字");
        }
        // LIKE 通配符转义，防止用户输入 % _ \ 干扰匹配
        String escaped = kw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        int offset = (page - 1) * size;

        // 有效搜索计入热搜榜 + 用户搜索历史（首搜才计，翻页不重复计）
        if (page == 1) {
            songRedisService.recordSearchKeyword(kw);
            songRedisService.recordSearchHistory(userId, kw);
        }

        // 歌手名联搜（失败不阻塞，降级为仅搜歌曲字段）
        List<Long> singerIds = Collections.emptyList();
        try {
            singerIds = singerClient.searchIds(escaped);
        } catch (Exception e) {
            log.warn("歌手名联搜失败，降级为仅歌曲字段: {}", e.getMessage());
        }

        // 子串匹配：搜"晴"命中歌名/歌词/风格/歌手名任何位置含"晴"的结果（对齐主流音乐 App 行为）
        long total = singerIds.isEmpty()
                ? songMapper.countByKeyword(escaped)
                : songMapper.countWithSingers(escaped, singerIds);
        List<Song> songs = singerIds.isEmpty()
                ? songMapper.searchByKeyword(escaped, offset, size)
                : songMapper.searchWithSingers(escaped, singerIds, offset, size);

        // 歌词紧凑匹配兜底：LRC 时间戳/换行把歌词逐行拆断，用户连报两句歌词时
        // 原始 LIKE 必然失配；剥离时间戳与非文字字符后再比对（命中视为第二页继续）
        if (total == 0) {
            total = songMapper.countByLyricCompact(kw);
            if (total > 0) {
                songs = songMapper.searchByLyricCompact(kw, offset, size);
                log.info("歌词紧凑匹配兜底命中: keyword={}, total={}", kw, total);
            }
        }

        SearchResultVO result = new SearchResultVO();
        result.setTotal(total);
        result.setPage(page);
        result.setSize(size);
        result.setSongs(fillSingerName(toVOList(songs)));
        return result;
    }

    @Override
    public SongVO play(Long songId, Long userId) {
        Song song = songMapper.selectById(songId);
        // 2001 歌曲不存在
        if (song == null || song.getDeleted() != null && song.getDeleted() == 1) {
            throw new BusinessException(ResultCode.SONG_NOT_EXIST);
        }
        // 状态拦截（详细设计 6.1）：审核中不可播、已下架不可播；
        // 例外：归属歌手本人可试听任意状态的自己的作品（歌手中心预览，不计数）
        boolean owner = userId != null && isSongOwner(song, userId);
        if (!owner) {
            if (song.getStatus() != null && song.getStatus() == 0) {
                throw new BusinessException("歌曲审核中，暂不可播放");
            }
            if (song.getStatus() != null && song.getStatus() == 2) {
                throw new BusinessException("歌曲已下架");
            }
        } else {
            log.info("歌手试听自己的作品: songId={}, status={}, userId={}", songId, song.getStatus(), userId);
        }

        SongVO vo = new SongVO();
        BeanUtils.copyProperties(song, vo);
        // 聚合歌手名（播放条需要展示）
        try {
            Map<Long, String> names = singerClient.names(String.valueOf(song.getSingerId()));
            vo.setSingerName(names.get(song.getSingerId()));
        } catch (Exception e) {
            log.warn("播放接口聚合歌手名失败，降级为空: {}", e.getMessage());
        }
        return vo;
    }

    /** 判断请求用户是否为歌曲归属歌手（song.singerId 经 singer 服务反查关联用户比对） */
    private boolean isSongOwner(Song song, Long userId) {
        try {
            Map<String, Object> singer = singerClient.userOf(song.getSingerId());
            Object ownerUserId = singer == null ? null : singer.get("userId");
            return ownerUserId != null && Long.valueOf(ownerUserId.toString()).equals(userId);
        } catch (Exception e) {
            log.warn("校验歌曲归属歌手失败，按非本人处理: songId={}, {}", song.getId(), e.getMessage());
            return false;
        }
    }

    /**
     * 播放上报：发 RabbitMQ 播放事件（异步化）。
     * song 消费端写 Redis 计数/热榜；favorite 消费端异步落库收听历史。
     * MySQL 的 play_count 仍由定时任务批量回写（写合并）。
     */
    @Override
    public void reportPlay(Long songId, Long userId) {
        // 仅已发布歌曲计数（对齐原 SQL 的 status=1 条件）
        Song song = songMapper.selectById(songId);
        if (song == null || song.getStatus() == null || song.getStatus() != 1) {
            log.warn("播放上报未命中已发布歌曲: songId={}", songId);
            return;
        }
        playEventPublisher.publishPlay(songId, userId);
    }

    @Override
    public void flushPlayCount() {
        Map<Long, Long> buffer = songRedisService.drainPlayCountBuffer();
        if (buffer.isEmpty()) {
            return;
        }
        buffer.forEach((songId, delta) -> songMapper.update(null,
                new LambdaUpdateWrapper<Song>()
                        .eq(Song::getId, songId)
                        .setSql("play_count = play_count + " + delta)));
        log.info("播放计数回写完成: 歌曲数={}, 总增量={}",
                buffer.size(), buffer.values().stream().mapToLong(Long::longValue).sum());
    }

    @Override
    public List<SongRedisService.HotKeyword> hotKeywords(int limit) {
        return songRedisService.topSearchKeywords(limit);
    }

    @Override
    public void recordSearchHistory(Long userId, String keyword) {
        songRedisService.recordSearchHistory(userId, keyword);
    }

    @Override
    public List<String> searchHistory(Long userId) {
        return songRedisService.searchHistory(userId);
    }

    @Override
    public void clearSearchHistory(Long userId) {
        songRedisService.clearSearchHistory(userId);
    }

    @Override
    public Map<Long, SongVO> batchByIds(List<Long> songIds) {
        if (songIds == null || songIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Song> songs = songMapper.selectBatchIds(songIds);
        List<SongVO> vos = fillSingerName(toVOList(songs));
        return vos.stream().collect(Collectors.toMap(SongVO::getId, vo -> vo, (a, b) -> a));
    }

    // ===== 歌手作品管理（FUN-08/09） =====

    /** 经 singer 服务校验用户歌手身份，返回 singerId；非认证歌手抛业务异常 */
    private Long requireSingerId(Long userId) {
        Map<String, Object> singer;
        try {
            singer = singerClient.byUser(userId);
        } catch (Exception e) {
            log.warn("查询歌手身份失败: userId={}, {}", userId, e.getMessage());
            throw new BusinessException("歌手服务暂时不可用，请稍后重试");
        }
        if (singer.isEmpty() || !Integer.valueOf(1).equals(singer.get("status"))) {
            throw new BusinessException(ResultCode.FORBIDDEN.getCode(), "仅认证歌手可发布歌曲");
        }
        return Long.valueOf(singer.get("id").toString());
    }

    @Override
    public void publish(Long userId, SongPublishDTO dto) {
        if (dto.getName() == null || dto.getName().isBlank()
                || dto.getUrl() == null || dto.getUrl().isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "请填写歌名并上传音频文件");
        }
        Long singerId = requireSingerId(userId);
        Song song = new Song();
        song.setSingerId(singerId);
        song.setName(dto.getName());
        song.setUrl(dto.getUrl());
        song.setCover(dto.getCover());
        song.setLyric(dto.getLyric());
        song.setStyle(dto.getStyle());
        song.setPlayCount(0L);
        song.setDuration(dto.getDuration() == null ? 0 : Math.max(0, dto.getDuration()));
        song.setStatus(0);
        songMapper.insert(song);
        log.info("歌曲已提交审核: songId={}, 歌手={}, 歌名={}", song.getId(), singerId, dto.getName());
        // 闭环：提交即通知管理员有待审歌曲（失败不阻塞发布主流程）
        try {
            messageClient.notifyAdmins("新歌曲待审核",
                    "歌手提交了新歌曲《" + dto.getName() + "》，请前往管理中心审核。");
        } catch (Exception e) {
            log.warn("新歌待审通知发送失败: songId={}, {}", song.getId(), e.getMessage());
        }
    }

    @Override
    public List<SongVO> mySongs(Long userId) {
        Long singerId = requireSingerId(userId);
        List<Song> songs = songMapper.selectList(
                new LambdaQueryWrapper<Song>()
                        .eq(Song::getSingerId, singerId)
                        .orderByDesc(Song::getUpdateTime));
        return toVOList(songs);
    }

    @Override
    public void updateSong(Long userId, Long songId, SongPublishDTO dto) {
        Long singerId = requireSingerId(userId);
        Song song = songMapper.selectById(songId);
        if (song == null || !singerId.equals(song.getSingerId())) {
            throw new BusinessException(ResultCode.SONG_NOT_EXIST);
        }
        // 编辑规则：审核中不可改；已发布先下架才能改；仅已下架可编辑，
        // 且编辑完自动回审核队列（status→0），管理员通过后重新发布
        if (song.getStatus() != null && song.getStatus() == 0) {
            throw new BusinessException("歌曲审核中，不可修改");
        }
        if (song.getStatus() == null || song.getStatus() != 2) {
            throw new BusinessException("已发布的歌曲请先下架再编辑，保存后将重新送审");
        }
        if (dto.getName() != null && !dto.getName().isBlank()) {
            song.setName(dto.getName());
        }
        if (dto.getUrl() != null && !dto.getUrl().isBlank()) {
            song.setUrl(dto.getUrl());
        }
        if (dto.getCover() != null) {
            song.setCover(dto.getCover());
        }
        if (dto.getLyric() != null) {
            song.setLyric(dto.getLyric());
        }
        if (dto.getStyle() != null) {
            song.setStyle(dto.getStyle());
        }
        // 编辑完自动进入待审核（内容变更必须复审，防止绕过审核直接上架）
        song.setStatus(0);
        song.setRejectReason(null);
        songMapper.updateById(song);
        log.info("已下架歌曲编辑完成并重新送审: songId={}, 歌手={}", songId, singerId);
        // 复用重新送审通知：告知管理员有修改后的歌待审
        try {
            messageClient.notifyAdmins("歌曲修改后待审核",
                    "歌手修改了下架歌曲《" + song.getName() + "》，请前往管理中心复审。");
        } catch (Exception e) {
            log.warn("编辑送审通知发送失败: songId={}, {}", songId, e.getMessage());
        }
    }

    @Override
    public void offline(Long userId, Long songId) {
        Long singerId = requireSingerId(userId);
        Song song = songMapper.selectById(songId);
        if (song == null || !singerId.equals(song.getSingerId())) {
            throw new BusinessException(ResultCode.SONG_NOT_EXIST);
        }
        song.setStatus(2);
        songMapper.updateById(song);
        log.info("歌曲已下架: songId={}, 操作歌手={}", songId, singerId);
    }

    @Override
    public void resubmit(Long userId, Long songId) {
        Long singerId = requireSingerId(userId);
        Song song = songMapper.selectById(songId);
        if (song == null || !singerId.equals(song.getSingerId())) {
            throw new BusinessException(ResultCode.SONG_NOT_EXIST);
        }
        if (song.getStatus() == null || song.getStatus() != 2) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "仅已下架的歌曲可重新送审");
        }
        if (song.getUrl() == null || song.getUrl().isBlank()) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "请先补全音频后再送审");
        }
        song.setStatus(0);
        song.setRejectReason(null);
        songMapper.updateById(song);
        log.info("歌曲已重新提交审核: songId={}, 歌手={}", songId, singerId);
        try {
            messageClient.notifyAdmins("歌曲重新上架待审核",
                    "歌手将下架歌曲《" + song.getName() + "》修改后重新提交，请前往管理中心审核。");
        } catch (Exception e) {
            log.warn("重新送审通知发送失败: songId={}, {}", songId, e.getMessage());
        }
    }

    @Override
    public List<SongVO> pendingList() {
        List<Song> songs = songMapper.selectList(
                new LambdaQueryWrapper<Song>()
                        .eq(Song::getStatus, 0)
                        .orderByAsc(Song::getId));
        return fillSingerName(toVOList(songs));
    }

    @Override
    public Map<String, Object> adminList(String keyword, int page, int size) {
        String kw = keyword == null ? "" : keyword.trim();
        int offset = (page - 1) * size;
        List<Song> songs = songMapper.selectAdminList(kw, offset, size);
        long total = songMapper.countAdminList(kw);
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("items", fillSingerName(toVOList(songs)));
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        return result;
    }

    @Override
    public void adminOffline(Long songId, String reason) {
        Song song = songMapper.selectById(songId);
        if (song == null || song.getDeleted() != null && song.getDeleted() == 1) {
            throw new BusinessException(ResultCode.SONG_NOT_EXIST);
        }
        if (song.getStatus() != null && song.getStatus() == 2) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "该歌曲已是下架状态");
        }
        song.setStatus(2);
        song.setRejectReason(reason == null || reason.isBlank()
                ? "平台管理下架" : reason);
        songMapper.updateById(song);
        log.info("管理员强制下架: songId={}, 原因={}", songId, song.getRejectReason());
        notifySinger(song, "歌曲被平台下架",
                "你的歌曲《" + song.getName() + "》被平台管理下架。原因：" + song.getRejectReason());
    }

    @Override
    public void audit(Long songId, boolean approve, String reason) {
        Song song = songMapper.selectById(songId);
        if (song == null || song.getStatus() == null || song.getStatus() != 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "该歌曲不存在或已处理");
        }
        if (approve) {
            song.setStatus(1);
            song.setRejectReason(null);
            songMapper.updateById(song);
            notifySinger(song, "歌曲审核通过",
                    "你的歌曲《" + song.getName() + "》已审核通过，现已在平台发布。");
            // 歌曲上架事件：MQ 广播给粉丝（favorite 查粉丝名单 → message 发通知）
            publishSongPublished(song);
            log.info("歌曲审核通过: songId={}", songId);
        } else {
            song.setStatus(2);
            song.setRejectReason(reason == null || reason.isBlank() ? "内容未通过审核" : reason);
            songMapper.updateById(song);
            notifySinger(song, "歌曲审核未通过",
                    "你的歌曲《" + song.getName() + "》未通过审核。原因：" + song.getRejectReason());
            log.info("歌曲审核驳回: songId={}, 原因={}", songId, song.getRejectReason());
        }
    }

    /** 审核结果通知歌手：失败仅记日志，永不阻塞审核主流程 */
    private void notifySinger(Song song, String title, String content) {
        try {
            Map<String, Object> owner = singerClient.userOf(song.getSingerId());
            Object userId = owner.get("userId");
            if (userId != null) {
                messageClient.send(Long.valueOf(userId.toString()), title, content, "AUDIT");
            }
        } catch (Exception e) {
            log.warn("歌曲审核通知发送失败: songId={}, {}", song.getId(), e.getMessage());
        }
    }

    /** 歌曲上架事件发布：MQ 故障仅记日志（粉丝通知是增强功能，不阻塞审核） */
    private void publishSongPublished(Song song) {
        try {
            playEventPublisher.publishSongPublished(song.getId(), song.getSingerId(), song.getName());
        } catch (Exception e) {
            log.warn("歌曲上架事件发送失败: songId={}, {}", song.getId(), e.getMessage());
        }
    }

    @Override
    public List<SongVO> bySinger(Long singerId) {
        List<Song> songs = songMapper.selectList(
                new LambdaQueryWrapper<Song>()
                        .eq(Song::getSingerId, singerId)
                        .eq(Song::getStatus, 1)
                        .orderByDesc(Song::getPlayCount));
        return fillSingerName(toVOList(songs));
    }

    @Override
    public List<Long> publishedIdsBySinger(Long singerId) {
        return songMapper.selectList(new LambdaQueryWrapper<Song>()
                        .select(Song::getId)
                        .eq(Song::getSingerId, singerId)
                        .eq(Song::getStatus, 1))
                .stream().map(Song::getId).toList();
    }

    @Override
    public List<Long> hotSingerIds(int limit) {
        return songMapper.selectHotSingerIds(limit).stream()
                .map(row -> Long.valueOf(row.get("singer_id").toString()))
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, Long> stats() {
        Map<String, Long> stats = new java.util.HashMap<>();
        stats.put("songCount", songMapper.selectCount(null));
        stats.put("publishedCount", songMapper.selectCount(
                new LambdaQueryWrapper<Song>().eq(Song::getStatus, 1)));
        stats.put("playCount", songMapper.sumPublishedPlayCount());
        return stats;
    }

    @Override
    public int backfillDurations(Map<Long, Integer> durationBySongId) {
        if (durationBySongId == null || durationBySongId.isEmpty()) {
            return 0;
        }
        int updated = 0;
        for (Map.Entry<Long, Integer> e : durationBySongId.entrySet()) {
            if (e.getKey() == null || e.getValue() == null || e.getValue() <= 0) {
                continue;
            }
            // 只回填"未知时长"(0)的歌，不覆盖已有值
            updated += songMapper.update(null,
                    new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Song>()
                            .eq(Song::getId, e.getKey())
                            .eq(Song::getDuration, 0)
                            .set(Song::getDuration, e.getValue()));
        }
        log.info("歌曲时长回填完成: 传入={}, 更新={}", durationBySongId.size(), updated);
        return updated;
    }

    private List<SongVO> toVOList(List<Song> songs) {
        return songs.stream().map(song -> {
            SongVO vo = new SongVO();
            BeanUtils.copyProperties(song, vo);
            return vo;
        }).collect(Collectors.toList());
    }

    /** 批量聚合歌手名；Feign 失败时降级为不显示歌手名，不阻塞榜单主流程 */
    private List<SongVO> fillSingerName(List<SongVO> vos) {
        if (vos.isEmpty()) {
            return vos;
        }
        String ids = vos.stream()
                .map(SongVO::getSingerId)
                .distinct()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        Map<Long, String> fetched;
        try {
            fetched = singerClient.names(ids);
        } catch (Exception e) {
            log.warn("获取歌手名失败，降级为空: {}", e.getMessage());
            fetched = Collections.emptyMap();
        }
        final Map<Long, String> nameMap = fetched;
        vos.forEach(vo -> vo.setSingerName(nameMap.get(vo.getSingerId())));
        return vos;
    }
}
