package com.qxt.yuexiang.singer.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qxt.yuexiang.singer.entity.Singer;
import org.apache.ibatis.annotations.Mapper;

/**
 * 歌手 Mapper
 */
@Mapper
public interface SingerMapper extends BaseMapper<Singer> {
}
