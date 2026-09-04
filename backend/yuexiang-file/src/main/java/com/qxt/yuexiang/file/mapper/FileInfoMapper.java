package com.qxt.yuexiang.file.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qxt.yuexiang.file.entity.FileInfo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文件元数据 Mapper
 */
@Mapper
public interface FileInfoMapper extends BaseMapper<FileInfo> {
}
