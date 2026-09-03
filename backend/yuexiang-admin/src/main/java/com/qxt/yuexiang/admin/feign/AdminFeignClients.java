package com.qxt.yuexiang.admin.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * 业务服务 Feign 聚合客户端（管理端统一转发，网关只暴露 /api/admin/**）
 */
public final class AdminFeignClients {

    private AdminFeignClients() {
    }

    @FeignClient(name = "yuexiang-user")
    public interface UserClient {
        /** GET /user/admin/list → Result<Map> 分页+关键字用户列表 */
        @GetMapping("/user/admin/list")
        Map<String, Object> list(@RequestParam(value = "keyword", required = false) String keyword,
                                 @RequestParam("page") int page,
                                 @RequestParam("size") int size);

        /** POST /user/admin/status */
        @PostMapping("/user/admin/status")
        Map<String, Object> changeStatus(@RequestParam("userId") Long userId,
                                         @RequestParam("enable") boolean enable);

        /** POST /user/admin/create 管理端新增用户 */
        @PostMapping("/user/admin/create")
        Map<String, Object> create(@org.springframework.web.bind.annotation.RequestBody Map<String, Object> body);

        /** PUT /user/admin/update 管理端编辑用户 */
        @org.springframework.web.bind.annotation.PutMapping("/user/admin/update")
        Map<String, Object> update(@RequestParam("userId") Long userId,
                                   @org.springframework.web.bind.annotation.RequestBody Map<String, Object> body);

        /** DELETE /user/admin/delete 管理端删除用户（逻辑删除） */
        @org.springframework.web.bind.annotation.DeleteMapping("/user/admin/delete")
        Map<String, Object> delete(@RequestParam("userId") Long userId);

        /** GET /user/stats → 裸 Map（总用户/歌手/正常账号数，门户统计用） */
        @GetMapping("/user/stats")
        Map<String, Object> stats();
    }

    @FeignClient(name = "yuexiang-singer")
    public interface SingerClient {
        /** GET /singer/pending → Result<List<SingerVO>> */
        @GetMapping("/singer/pending")
        Map<String, Object> pending();

        /** POST /singer/audit/{id} */
        @PostMapping("/singer/audit/{id}")
        Map<String, Object> audit(@org.springframework.web.bind.annotation.PathVariable("id") Long singerId,
                                  @RequestParam("approve") boolean approve,
                                  @RequestParam(value = "reason", required = false) String reason);
    }

    @FeignClient(name = "yuexiang-song")
    public interface SongClient {
        /** GET /song/pending → Result<List<SongVO>> */
        @GetMapping("/song/pending")
        Map<String, Object> pending();

        /** POST /song/audit/{id} */
        @PostMapping("/song/audit/{id}")
        Map<String, Object> audit(@org.springframework.web.bind.annotation.PathVariable("id") Long songId,
                                  @RequestParam("approve") boolean approve,
                                  @RequestParam(value = "reason", required = false) String reason);

        /** GET /song/stats → 裸 Map（歌曲总数/已发布数/总播放量，门户统计用） */
        @GetMapping("/song/stats")
        Map<String, Object> stats();

        /** GET /song/admin/list → Result<Map> 管理端全量歌曲（分页+关键字） */
        @GetMapping("/song/admin/list")
        Map<String, Object> adminList(@RequestParam(value = "keyword", required = false) String keyword,
                                      @RequestParam("page") int page,
                                      @RequestParam("size") int size);

        /** POST /song/admin/offline 管理员强制下架 */
        @PostMapping("/song/admin/offline")
        Map<String, Object> adminOffline(@RequestParam("songId") Long songId,
                                         @RequestParam(value = "reason", required = false) String reason);
    }

    @FeignClient(name = "yuexiang-message")
    public interface MessageClient {
        /** POST /message/broadcast 群发公告 */
        @PostMapping("/message/broadcast")
        Map<String, Object> broadcast(@RequestParam("title") String title,
                                      @RequestParam("content") String content);

        /** GET /message/admin/list 管理员收件箱（分页+未读） */
        @GetMapping("/message/admin/list")
        Map<String, Object> adminList(@RequestParam("page") int page, @RequestParam("size") int size);

        /** GET /message/admin/unread 管理员收件箱未读数 */
        @GetMapping("/message/admin/unread")
        Map<String, Object> adminUnread();

        /** POST /message/admin/read/{id} 单条已读 */
        @PostMapping("/message/admin/read/{id}")
        Map<String, Object> adminRead(@org.springframework.web.bind.annotation.PathVariable("id") Long messageId);

        /** POST /message/admin/read/all 全部已读 */
        @PostMapping("/message/admin/read/all")
        Map<String, Object> adminReadAll();
    }
}
