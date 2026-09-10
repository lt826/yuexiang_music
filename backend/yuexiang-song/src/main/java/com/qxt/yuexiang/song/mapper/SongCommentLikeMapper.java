package com.qxt.yuexiang.song.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qxt.yuexiang.song.entity.SongCommentLike;
import org.apache.ibatis.annotations.Mapper;

/**
 * 评论点赞 Mapper
 */
@Mapper
public interface SongCommentLikeMapper extends BaseMapper<SongCommentLike> {
}
