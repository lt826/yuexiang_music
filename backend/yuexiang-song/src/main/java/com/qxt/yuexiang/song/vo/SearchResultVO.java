package com.qxt.yuexiang.song.vo;

import lombok.Data;

import java.util.List;

/**
 * 搜索分页结果
 */
@Data
public class SearchResultVO {

    private Long total;

    private Integer page;

    private Integer size;

    private List<SongVO> songs;
}
