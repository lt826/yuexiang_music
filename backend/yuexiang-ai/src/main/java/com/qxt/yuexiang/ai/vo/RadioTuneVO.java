package com.qxt.yuexiang.ai.vo;

import com.qxt.yuexiang.ai.vo.AiSongVO;
import lombok.Data;

import java.util.List;

/**
 * 心情电台调频结果：频道节目单 + 服务端时间轴上的当前位置。
 * 播放位置是服务器时间的纯函数（epoch=当天零点，按节目单总时长取模），
 * 所有并发用户同一时刻算出的位置一致，天然实现"多人共享播放进度"。
 */
@Data
public class RadioTuneVO {

    /** 频道 key（sleep/study/...） */
    private String channel;

    /** 虚拟调频频率（88.4 等，前端调频盘展示） */
    private String freq;

    /** 节目单歌单 ID（0 表示无官方歌单，电台不可用） */
    private Long playlistId;

    /** 节目单标题 */
    private String playlistTitle;

    /** 有序节目单 */
    private List<AiSongVO> songs;

    /** 当前正在播第几首（0 起） */
    private Integer currentIndex;

    /** 当前这首已播到第几秒 */
    private Integer offsetSeconds;

    /** 本套节目单的模拟单曲时长（秒），前端倒计时/进度展示用 */
    private Integer segmentSeconds;
}
