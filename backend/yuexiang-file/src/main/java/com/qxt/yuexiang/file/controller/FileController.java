package com.qxt.yuexiang.file.controller;

import com.qxt.yuexiang.common.result.Result;
import com.qxt.yuexiang.file.service.FileService;
import com.qxt.yuexiang.file.vo.FileVO;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件上传下载接口
 */
@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    /** POST /file/upload multipart 上传，type 取值 SONG/COVER/AVATAR */
    @PostMapping("/upload")
    public Result<FileVO> upload(@RequestParam("file") MultipartFile file,
                                 @RequestParam("type") String type,
                                 @RequestHeader(value = "X-User-Id", required = false) Long uploaderId) {
        return Result.success(fileService.upload(file, type, uploaderId));
    }

    /**
     * GET /file/{type}/{storedName} 下载与在线访问（音频边下边播、图片展示共用）
     * {type}/{storedName} 整体作为路径变量，服务层做路径校验
     */
    @GetMapping("/{type}/{storedName}")
    public ResponseEntity<Resource> download(@PathVariable("type") String type,
                                             @PathVariable("storedName") String storedName) {
        Resource resource = fileService.download(type + "/" + storedName);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + storedName + "\"")
                .body(resource);
    }
}
