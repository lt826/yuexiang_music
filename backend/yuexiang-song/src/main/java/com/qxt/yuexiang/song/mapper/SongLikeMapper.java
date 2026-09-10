package com.qxt.yuexiang.song.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qxt.yuexiang.song.entity.SongLike;
import org.apache.ibatis.annotations.Mapper;

/**
 * 歌曲点赞 Mapper
 */
@Mapper
public interface SongLikeMapper extends BaseMapper<SongLike> {
}
