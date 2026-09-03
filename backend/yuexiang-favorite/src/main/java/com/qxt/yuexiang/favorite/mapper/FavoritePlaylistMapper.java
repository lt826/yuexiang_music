package com.qxt.yuexiang.favorite.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qxt.yuexiang.favorite.entity.FavoritePlaylist;
import org.apache.ibatis.annotations.Mapper;

/**
 * 歌单收藏 Mapper
 */
@Mapper
public interface FavoritePlaylistMapper extends BaseMapper<FavoritePlaylist> {
}
