package com.qxt.yuexiang.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qxt.yuexiang.user.entity.UserFollow;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户关注 Mapper
 */
@Mapper
public interface UserFollowMapper extends BaseMapper<UserFollow> {
}
