package com.qxt.yuexiang.circle.listen;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * 一起听房间实时状态（Redis Hash 存储，key: listen:room:{roomId}）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoomState {

    /** 房间ID */
    private Long roomId;

    /** 所属圈子ID */
    private Long circleId;

    /** 主题（选填） */
    private String theme;

    /** 发起人用户ID */
    private Long creatorId;

    /** 当前歌曲ID（可空=idle） */
    private Long currentSongId;

    /** 当前歌曲快照（name/singerName/cover/duration，避免每帧查 song 服务） */
    private String currentSongJson;

    /** 播放状态 playing / paused / idle */
    private String status;

    /** 指令落定时刻（服务器毫秒），客户端按 (now - actionAt) + position 计算应播进度 */
    private Long actionAt;

    /** 指令落定时的播放位置（秒） */
    private Double position;

    /** 在线成员 userId 集合（独立 key listen:room:{roomId}:members，此字段仅传输用） */
    private Set<Long> onlineUserIds;

    /** 在线人数 */
    private Integer memberCount;

    /** 房间创建时间（服务器毫秒） */
    private Long createdAt;
}
