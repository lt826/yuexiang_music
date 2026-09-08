package com.qxt.yuexiang.song.task;

import com.qxt.yuexiang.song.service.SongService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 播放计数回写任务：把 Redis 缓冲的播放增量批量合并进 MySQL。
 * 写合并——N 次播放只在回写时产生一次 UPDATE，数据库不再逐次承压。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlayCountFlushTask {

    private final SongService songService;

    /** 每 5 分钟回写一次（fixedDelay 防止上一轮没跑完就叠加） */
    @Scheduled(fixedDelay = 5 * 60 * 1000, initialDelay = 60 * 1000)
    public void flush() {
        try {
            songService.flushPlayCount();
        } catch (Exception e) {
            log.warn("播放计数回写失败（下轮重试）: {}", e.getMessage());
        }
    }
}
