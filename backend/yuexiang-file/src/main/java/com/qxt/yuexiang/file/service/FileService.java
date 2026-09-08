package com.qxt.yuexiang.file.service;

import com.qxt.yuexiang.file.vo.FileVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件上传下载服务
 */
public interface FileService {

    /**
     * 上传文件：校验类型与大小，UUID 落盘，写 file_info 表
     *
     * @param file       上传的文件
     * @param type       业务类型 SONG/COVER/AVATAR
     * @param uploaderId 上传者用户ID（网关透传）
     */
    FileVO upload(MultipartFile file, String type, Long uploaderId);

    /**
     * 按存储文件名读取磁盘文件，用于下载与在线播放
     */
    org.springframework.core.io.Resource download(String storedName);
}
