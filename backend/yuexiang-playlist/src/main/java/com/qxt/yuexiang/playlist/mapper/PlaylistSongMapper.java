package com.qxt.yuexiang.playlist.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qxt.yuexiang.playlist.entity.PlaylistSong;
import org.apache.ibatis.annotations.Mapper;

/**
 * 歌单-歌曲关联 Mapper
 */
@Mapper
public interface PlaylistSongMapper extends BaseMapper<PlaylistSong> {
}
