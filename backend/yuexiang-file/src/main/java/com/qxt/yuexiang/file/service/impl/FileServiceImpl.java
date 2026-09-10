package com.qxt.yuexiang.file.service.impl;

import com.qxt.yuexiang.common.exception.BusinessException;
import com.qxt.yuexiang.common.result.ResultCode;
import com.qxt.yuexiang.file.entity.FileInfo;
import com.qxt.yuexiang.file.mapper.FileInfoMapper;
import com.qxt.yuexiang.file.service.FileService;
import com.qxt.yuexiang.file.util.Mp3DurationParser;
import com.qxt.yuexiang.file.vo.FileVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 文件上传下载实现：本地磁盘存储起步（对齐概要设计「存储选型：开发期本地目录」）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    private final FileInfoMapper fileInfoMapper;

    /** 存储根目录 */
    @Value("${file.storage-root:./data/file}")
    private String storageRoot;

    /** 各类型允许的扩展名与大小上限（字节） */
    private static final Map<String, List<String>> ALLOWED_EXTENSIONS = Map.of(
            "SONG", List.of("mp3", "flac", "wav"),
            "COVER", List.of("jpg", "jpeg", "png", "webp"),
            "AVATAR", List.of("jpg", "jpeg", "png", "webp"),
            "IMAGE", List.of("jpg", "jpeg", "png", "webp", "gif")
    );

    /** 音频 50MB、图片 5MB（对齐需求：文件最大 100MB，业务按类型收紧） */
    private static final Map<String, Long> MAX_SIZE = Map.of(
            "SONG", 50L * 1024 * 1024,
            "COVER", 5L * 1024 * 1024,
            "AVATAR", 5L * 1024 * 1024,
            "IMAGE", 5L * 1024 * 1024
    );

    @Override
    public FileVO upload(MultipartFile file, String type, Long uploaderId) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "请选择要上传的文件");
        }
        String normalizedType = type == null ? "" : type.toUpperCase(Locale.ROOT);
        if (!ALLOWED_EXTENSIONS.containsKey(normalizedType)) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "不支持的业务类型: " + type);
        }

        String originalName = StringUtils.cleanPath(file.getOriginalFilename());
        String extension = getExtension(originalName);
        if (!ALLOWED_EXTENSIONS.get(normalizedType).contains(extension)) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                    "文件格式不支持，仅允许 " + String.join("/", ALLOWED_EXTENSIONS.get(normalizedType)));
        }
        Long limit = MAX_SIZE.get(normalizedType);
        if (file.getSize() > limit) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(),
                    "文件超出大小限制（最大 " + (limit / 1024 / 1024) + "MB）");
        }

        // 按类型分目录，UUID 命名防冲突（对齐详细设计 7.2 存储规范）
        String storedName = UUID.randomUUID().toString().replace("-", "") + "." + extension;
        Path typeDir = Paths.get(storageRoot, normalizedType.toLowerCase());
        Path target;
        try {
            Files.createDirectories(typeDir);
            target = typeDir.resolve(storedName);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("文件落盘失败: {}", originalName, e);
            throw new BusinessException("文件保存失败，请稍后重试");
        }

        // 歌曲文件解析时长（秒）：电台时间轴/前端展示用；解析失败按 0 兜底不阻断上传
        int duration = 0;
        if ("SONG".equals(normalizedType) && "mp3".equals(extension)) {
            duration = Mp3DurationParser.parseSeconds(target);
            if (duration <= 0) {
                log.warn("MP3 时长解析失败（按未知处理）: {}", storedName);
            }
        }

        FileInfo info = new FileInfo();
        info.setOriginalName(originalName);
        info.setStoredName(storedName);
        info.setUrl("/api/file/" + normalizedType.toLowerCase() + "/" + storedName);
        info.setType(normalizedType);
        info.setSize(file.getSize());
        info.setDuration(duration);
        info.setUploaderId(uploaderId);
        fileInfoMapper.insert(info);

        FileVO vo = new FileVO();
        BeanUtils.copyProperties(info, vo);
        log.info("文件上传成功: id={}, type={}, size={}B, uploaderId={}",
                info.getId(), normalizedType, info.getSize(), uploaderId);
        return vo;
    }

    @Override
    public Resource download(String storedName) {
        // 存储名只允许「目录 + 文件名」形态，拒绝路径穿越（如 ..%2F）
        if (!storedName.matches("[a-z]+/[a-f0-9]+\\.[a-z0-9]+")) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "非法的文件路径");
        }
        Path path = Paths.get(storageRoot).resolve(storedName).normalize();
        if (!path.startsWith(Paths.get(storageRoot).normalize())) {
            throw new BusinessException(ResultCode.BAD_REQUEST.getCode(), "非法的文件路径");
        }
        try {
            Resource resource = new UrlResource(path.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "文件不存在");
            }
            return resource;
        } catch (MalformedURLException e) {
            throw new BusinessException(ResultCode.NOT_FOUND.getCode(), "文件不存在");
        }
    }

    private String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
