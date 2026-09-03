package com.qxt.yuexiang.favorite.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qxt.yuexiang.favorite.entity.FavoriteSong;
import org.apache.ibatis.annotations.Mapper;

/**
 * 歌曲收藏 Mapper
 */
@Mapper
public interface FavoriteSongMapper extends BaseMapper<FavoriteSong> {
}
