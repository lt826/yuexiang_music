-- ============================================================
-- 乐坊（圈子）：增量建表脚本（可重复执行，表存在则跳过）
-- 全部归 yuexiang_circle 库（乐坊业务独立微服务）
-- 执行：mysql -uroot -p < add_circle.sql
-- ============================================================
CREATE DATABASE IF NOT EXISTS `yuexiang_circle`
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE yuexiang_circle;

-- ------------------------------------------------------------
-- 圈子表：所有人可创建，名称需管理员审核（status 状态机）
-- 0 待审核 1 正常 2 驳回（驳回可改名重新提交）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `circle` (
    `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '圈子ID',
    `creator_id`    BIGINT       NOT NULL COMMENT '圈主用户ID（来自 yuexiang_user 服务）',
    `name`          VARCHAR(50)  NOT NULL COMMENT '圈子名称（需审核）',
    `description`   VARCHAR(500) DEFAULT NULL COMMENT '圈子简介',
    `cover`         VARCHAR(500) DEFAULT NULL COMMENT '封面图URL',
    `status`        TINYINT      NOT NULL DEFAULT 0 COMMENT '审核状态 0待审核 1正常 2驳回',
    `reject_reason` VARCHAR(200) DEFAULT NULL COMMENT '驳回原因（改名重提后清空）',
    `member_count`  INT          NOT NULL DEFAULT 1 COMMENT '成员数（圈主算第1个）',
    `post_count`    INT          NOT NULL DEFAULT 0 COMMENT '帖子数',
    `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0正常 1已解散',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_creator` (`creator_id`),
    KEY `idx_status_time` (`status`, `create_time` DESC)
) ENGINE = InnoDB COMMENT '乐坊圈子表';

-- ------------------------------------------------------------
-- 圈子成员表：加入方式默认公开，自主加入退出
-- role：OWNER 圈主 / ADMIN 圈管理员 / MEMBER 普通成员
-- 退出即物理 DELETE（对齐 favorite_song 惯例）；解散圈子时级联清理
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `circle_member` (
    `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '成员记录ID',
    `circle_id`   BIGINT   NOT NULL COMMENT '圈子ID',
    `user_id`     BIGINT   NOT NULL COMMENT '用户ID（来自 yuexiang_user 服务）',
    `role`        VARCHAR(10) NOT NULL DEFAULT 'MEMBER' COMMENT '角色 OWNER圈主 ADMIN管理员 MEMBER成员',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_circle_user` (`circle_id`, `user_id`),
    KEY `idx_user` (`user_id`)
) ENGINE = InnoDB COMMENT '乐坊圈子成员表';

-- ------------------------------------------------------------
-- 帖子表：文字帖 + 可选歌曲卡片；置顶最多 2 条由 service 层校验
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `circle_post` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '帖子ID',
    `circle_id`   BIGINT       NOT NULL COMMENT '圈子ID',
    `user_id`     BIGINT       NOT NULL COMMENT '发帖人用户ID',
    `content`     VARCHAR(2000) NOT NULL COMMENT '帖子文字内容',
    `song_id`     BIGINT       DEFAULT NULL COMMENT '插入的歌曲ID（可空=纯文字帖）',
    `image`       VARCHAR(500) DEFAULT NULL COMMENT '帖子图片URL（可空，最多1张，/api/file/image/ 下发）',
    `pinned`      TINYINT      NOT NULL DEFAULT 0 COMMENT '置顶 0普通 1置顶（最多2条）',
    `like_count`  INT          NOT NULL DEFAULT 0 COMMENT '点赞数',
    `comment_count` INT        NOT NULL DEFAULT 0 COMMENT '评论数',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0正常 1已删除',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_circle_pinned_time` (`circle_id`, `pinned`, `create_time` DESC),
    KEY `idx_user` (`user_id`)
) ENGINE = InnoDB COMMENT '乐坊帖子表';

-- ------------------------------------------------------------
-- 帖子评论表：parent_id 支持楼中楼（NULL=评论帖子，非空=回复某条评论）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `circle_comment` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '评论ID',
    `post_id`     BIGINT       NOT NULL COMMENT '帖子ID',
    `user_id`     BIGINT       NOT NULL COMMENT '评论人用户ID',
    `parent_id`   BIGINT       DEFAULT NULL COMMENT '父评论ID（NULL=直接评论帖子）',
    `reply_to_user_id` BIGINT  DEFAULT NULL COMMENT '被回复人用户ID（楼中楼展示"@某人"）',
    `content`     VARCHAR(500) NOT NULL COMMENT '评论内容',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0正常 1已删除',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '评论时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_post_time` (`post_id`, `create_time`),
    KEY `idx_parent` (`parent_id`)
) ENGINE = InnoDB COMMENT '乐坊帖子评论表';

-- ------------------------------------------------------------
-- 帖子点赞表：唯一约束防重复点赞；取消点赞物理 DELETE
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `circle_like` (
    `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '点赞记录ID',
    `post_id`     BIGINT   NOT NULL COMMENT '帖子ID',
    `user_id`     BIGINT   NOT NULL COMMENT '点赞人用户ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '点赞时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_post_user` (`post_id`, `user_id`),
    KEY `idx_user` (`user_id`)
) ENGINE = InnoDB COMMENT '乐坊帖子点赞表';

-- ------------------------------------------------------------
-- 一起听房间表：持久化房间元信息（实时播放状态走 Redis，见服务端 ListenRoomService）
-- status：0 进行中 1 已结束（历史记录保留，便于回看活跃圈子）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `listen_room` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '房间ID',
    `circle_id`   BIGINT       NOT NULL COMMENT '所属圈子ID',
    `creator_id`  BIGINT       NOT NULL COMMENT '发起人用户ID（圈主/管理员）',
    `theme`       VARCHAR(100) DEFAULT NULL COMMENT '房间主题（选填）',
    `initial_song_id` BIGINT   DEFAULT NULL COMMENT '初始歌曲ID',
    `status`      TINYINT      NOT NULL DEFAULT 0 COMMENT '房间状态 0进行中 1已结束',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0正常 1已删除',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发起时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_circle_status` (`circle_id`, `status`)
) ENGINE = InnoDB COMMENT '一起听房间表';

-- ------------------------------------------------------------
-- 一起听成员流水表：进出记录（禁言状态在 Redis 实时层，不入库）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `listen_room_member` (
    `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    `room_id`     BIGINT   NOT NULL COMMENT '房间ID',
    `user_id`     BIGINT   NOT NULL COMMENT '用户ID',
    `join_time`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '进入时间',
    `leave_time`  DATETIME DEFAULT NULL COMMENT '离开时间（NULL=仍在房间）',
    PRIMARY KEY (`id`),
    KEY `idx_room` (`room_id`)
) ENGINE = InnoDB COMMENT '一起听成员进出流水表';

-- ------------------------------------------------------------
-- 增量迁移：帖子图片（存量库执行；新库由上方建表语句直接带 image 列，
-- ALTER 会因列已存在报错可忽略）
-- ------------------------------------------------------------
ALTER TABLE `circle_post`
    ADD COLUMN `image` VARCHAR(500) DEFAULT NULL COMMENT '帖子图片URL（可空，最多1张，/api/file/image/ 下发）' AFTER `song_id`;
