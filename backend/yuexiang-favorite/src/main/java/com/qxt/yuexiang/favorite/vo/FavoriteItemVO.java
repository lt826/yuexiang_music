package com.qxt.yuexiang.favorite.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 收藏/历史列表项：关联信息由 song 服务聚合
 */
@Data
public class FavoriteItemVO {

    private Long songId;

    private String name;

    private String cover;

    private String url;

    private String style;

    private Long playCount;

    private String singerName;

    /** 收藏/收听时间 */
    private LocalDateTime time;

    /** 供 service 组装：先建骨架（songId + 时间），再批量填充歌曲信息 */
    public static FavoriteItemVO of(Long songId, LocalDateTime time) {
        FavoriteItemVO vo = new FavoriteItemVO();
        vo.setSongId(songId);
        vo.setTime(time);
        return vo;
    }
}
