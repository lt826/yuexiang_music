-- ============================================================
-- 悦享音乐 微服务数据库初始化脚本（初版结构，后续随功能迭代）
-- 每个微服务独立数据库，可重复执行（DROP 后重建）
-- 字符集统一 utf8mb4，逻辑删除 deleted：0 正常 / 1 已删除
-- ============================================================

-- -----------------------------------------------------------
-- 1. yuexiang_user：登录注册 + 用户信息设置
-- -----------------------------------------------------------
CREATE DATABASE IF NOT EXISTS yuexiang_user DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE yuexiang_user;

DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    `username`    VARCHAR(50)  NOT NULL COMMENT '用户名',
    `password`    VARCHAR(100) NOT NULL COMMENT '密码（加密存储）',
    `nickname`    VARCHAR(50)  DEFAULT NULL COMMENT '昵称',
    `avatar`      VARCHAR(255) DEFAULT NULL COMMENT '头像URL',
    `phone`       VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
    `email`       VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
    `gender`      TINYINT      DEFAULT 0 COMMENT '性别 0未知 1男 2女',
    `birthday`    DATE         DEFAULT NULL COMMENT '生日',
    `signature`   VARCHAR(255) DEFAULT NULL COMMENT '个性签名',
    `role`        VARCHAR(20)  NOT NULL DEFAULT 'USER' COMMENT '角色 USER普通用户 SINGER歌手 ADMIN管理员',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 0禁用 1正常',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE = InnoDB COMMENT '用户表';

-- -----------------------------------------------------------
-- 2. yuexiang_song：音乐搜索推荐 + 歌曲管理
-- -----------------------------------------------------------
CREATE DATABASE IF NOT EXISTS yuexiang_song DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE yuexiang_song;

DROP TABLE IF EXISTS `song`;
CREATE TABLE `song` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '歌曲ID',
    `singer_id`   BIGINT       NOT NULL COMMENT '歌手ID（来自 yuexiang_singer 服务）',
    `name`        VARCHAR(100) NOT NULL COMMENT '歌名',
    `cover`       VARCHAR(255) DEFAULT NULL COMMENT '封面URL',
    `url`         VARCHAR(255) DEFAULT NULL COMMENT '音频文件URL',
    `lyric`       TEXT         COMMENT '歌词',
    `style`       VARCHAR(50)  DEFAULT NULL COMMENT '风格（流行/摇滚/民谣等）',
    `play_count`  BIGINT       NOT NULL DEFAULT 0 COMMENT '播放量',
    `status`      TINYINT      NOT NULL DEFAULT 0 COMMENT '审核状态 0待审核 1已发布 2已下架',
    `reject_reason` VARCHAR(500) DEFAULT NULL COMMENT '审核驳回原因',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_singer_id` (`singer_id`),
    KEY `idx_name` (`name`),
    FULLTEXT KEY `ft_name_lyric` (`name`, `lyric`) WITH PARSER ngram
) ENGINE = InnoDB COMMENT '歌曲表';

-- -----------------------------------------------------------
-- 3. yuexiang_singer：歌手
-- -----------------------------------------------------------
CREATE DATABASE IF NOT EXISTS yuexiang_singer DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE yuexiang_singer;

DROP TABLE IF EXISTS `singer`;
CREATE TABLE `singer` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '歌手ID',
    `user_id`     BIGINT       DEFAULT NULL COMMENT '关联用户ID（来自 yuexiang_user 服务）',
    `name`        VARCHAR(50)  NOT NULL COMMENT '歌手名/艺名',
    `avatar`      VARCHAR(255) DEFAULT NULL COMMENT '头像URL',
    `introduction` VARCHAR(500) DEFAULT NULL COMMENT '简介',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 0待审核 1正常 2驳回 3冻结',
    `reject_reason` VARCHAR(500) DEFAULT NULL COMMENT '驳回原因（status=2 时有值）',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE = InnoDB COMMENT '歌手表';

-- -----------------------------------------------------------
-- 4. yuexiang_playlist：歌单
-- -----------------------------------------------------------
CREATE DATABASE IF NOT EXISTS yuexiang_playlist DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE yuexiang_playlist;

DROP TABLE IF EXISTS `playlist`;
CREATE TABLE `playlist` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '歌单ID',
    `user_id`     BIGINT       NOT NULL COMMENT '创建者用户ID',
    `title`       VARCHAR(100) NOT NULL COMMENT '歌单标题',
    `cover`       VARCHAR(255) DEFAULT NULL COMMENT '封面URL',
    `description` VARCHAR(500) DEFAULT NULL COMMENT '描述',
    `tag`         VARCHAR(50)  DEFAULT NULL COMMENT '标签（推荐歌单的分类）',
    `is_official` TINYINT      NOT NULL DEFAULT 0 COMMENT '是否官方推荐歌单 0否 1是',
    `published`   TINYINT      NOT NULL DEFAULT 0 COMMENT '是否投稿到歌单广场 0私藏 1公开',
    `play_count`  BIGINT       NOT NULL DEFAULT 0 COMMENT '播放量',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE = InnoDB COMMENT '歌单表';

DROP TABLE IF EXISTS `playlist_song`;
CREATE TABLE `playlist_song` (
    `id`          BIGINT   NOT NULL AUTO_INCREMENT,
    `playlist_id` BIGINT   NOT NULL COMMENT '歌单ID',
    `song_id`     BIGINT   NOT NULL COMMENT '歌曲ID（来自 yuexiang_song 服务）',
    `sort`        INT      NOT NULL DEFAULT 0 COMMENT '排序',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_playlist_song` (`playlist_id`, `song_id`)
) ENGINE = InnoDB COMMENT '歌单-歌曲关联表';

-- -----------------------------------------------------------
-- 5. yuexiang_favorite：音乐收藏 + 收听历史
-- -----------------------------------------------------------
CREATE DATABASE IF NOT EXISTS yuexiang_favorite DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE yuexiang_favorite;

DROP TABLE IF EXISTS `favorite_song`;
CREATE TABLE `favorite_song` (
    `id`          BIGINT   NOT NULL AUTO_INCREMENT,
    `user_id`     BIGINT   NOT NULL COMMENT '用户ID',
    `song_id`     BIGINT   NOT NULL COMMENT '歌曲ID（来自 yuexiang_song 服务）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_song` (`user_id`, `song_id`)
) ENGINE = InnoDB COMMENT '歌曲收藏表';

DROP TABLE IF EXISTS `favorite_playlist`;
CREATE TABLE `favorite_playlist` (
    `id`          BIGINT   NOT NULL AUTO_INCREMENT,
    `user_id`     BIGINT   NOT NULL COMMENT '用户ID',
    `playlist_id` BIGINT   NOT NULL COMMENT '歌单ID（来自 yuexiang_playlist 服务）',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_playlist` (`user_id`, `playlist_id`)
) ENGINE = InnoDB COMMENT '歌单收藏表';

DROP TABLE IF EXISTS `listen_history`;
CREATE TABLE `listen_history` (
    `id`           BIGINT   NOT NULL AUTO_INCREMENT,
    `user_id`      BIGINT   NOT NULL COMMENT '用户ID',
    `song_id`      BIGINT   NOT NULL COMMENT '歌曲ID',
    `listen_time`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收听时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_time` (`user_id`, `listen_time` DESC)
) ENGINE = InnoDB COMMENT '收听历史表';

-- -----------------------------------------------------------
-- 6. yuexiang_message：消息通知
-- -----------------------------------------------------------
CREATE DATABASE IF NOT EXISTS yuexiang_message DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE yuexiang_message;

DROP TABLE IF EXISTS `message`;
CREATE TABLE `message` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`     BIGINT       NOT NULL COMMENT '接收人用户ID',
    `title`       VARCHAR(100) NOT NULL COMMENT '消息标题',
    `content`     VARCHAR(500) NOT NULL COMMENT '消息内容',
    `type`        VARCHAR(20)  NOT NULL DEFAULT 'SYSTEM' COMMENT '类型 SYSTEM系统 AUDIT审核 FAN粉丝',
    `is_read`     TINYINT      NOT NULL DEFAULT 0 COMMENT '是否已读 0未读 1已读',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_read` (`user_id`, `is_read`)
) ENGINE = InnoDB COMMENT '消息通知表';

-- -----------------------------------------------------------
-- 7. yuexiang_admin：后台管理
-- -----------------------------------------------------------
CREATE DATABASE IF NOT EXISTS yuexiang_admin DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE yuexiang_admin;

DROP TABLE IF EXISTS `admin`;
CREATE TABLE `admin` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `username`    VARCHAR(50)  NOT NULL COMMENT '管理员账号',
    `password`    VARCHAR(100) NOT NULL COMMENT '密码（加密存储）',
    `nickname`    VARCHAR(50)  DEFAULT NULL COMMENT '昵称',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE = InnoDB COMMENT '管理员表';

-- 默认管理员 admin/admin123（上线前必须改密；BCrypt 密文，明文为 admin123）
INSERT INTO `admin` (`username`, `password`, `nickname`)
VALUES ('admin', '$2b$10$CNWLR/rDTn0Ccpx4oR/mFOnwIrO1OTwvYnZUeNesaaJwWxNtGu4k.', '超级管理员');

-- 首页轮播图（管理员后台上传/管理；image 可为上传海报或歌曲封面）
DROP TABLE IF EXISTS `banner`;
CREATE TABLE `banner` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '轮播图ID',
    `title`       VARCHAR(100) NOT NULL COMMENT '主标题（如歌星名）',
    `subtitle`    VARCHAR(200) DEFAULT NULL COMMENT '副标题/推广语',
    `image_url`   VARCHAR(500) NOT NULL COMMENT '海报图片URL（上传或歌曲封面）',
    `song_id`     BIGINT       DEFAULT NULL COMMENT '关联歌曲ID（可选，点击轮播跳播放）',
    `sort_order`  INT          NOT NULL DEFAULT 0 COMMENT '排序（小的在前）',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '状态 0下架 1上架',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB COMMENT '首页轮播图';

-- -----------------------------------------------------------
-- 首页演示种子数据（歌手 / 歌曲 / 官方推荐歌单）
-- -----------------------------------------------------------
USE yuexiang_singer;
INSERT INTO `singer` (`id`, `name`, `introduction`, `status`) VALUES
(1, '林晚风', '独立民谣歌手，代表作《南方小巷》。', 1),
(2, '陈屿', '流行唱作人，擅长都市抒情。', 1),
(3, 'SummerWave', '电子乐队，夏日氛围担当。', 1),
(4, '苏离', '摇滚女声，现场爆发力极强。', 1),
(5, '白噪实验室', '氛围后摇厂牌。', 1);

USE yuexiang_song;
-- 音频文件为程序合成的原创纯音乐（免版权），存放于 yuexiang-file 服务 data/file/song 目录
INSERT INTO `song` (`id`, `singer_id`, `name`, `style`, `play_count`, `status`, `url`) VALUES
(1, 1, '南方小巷', '民谣', 98210, 1, '/api/file/song/4e4b27df71eee59c4c826e4d98120210.wav'),
(2, 2, '城市黄昏', '流行', 87564, 1, '/api/file/song/2869d8b0bc80c2cee88abc3a44c84296.wav'),
(3, 3, '海边信号', '电子', 74320, 1, '/api/file/song/1d7ddb79ffbabc0babbda1cbb9cbc65d.wav'),
(4, 4, '逆光而行', '摇滚', 66543, 1, '/api/file/song/128da0cda7958d48e043dc2ceb991279.wav'),
(5, 1, '旧信', '民谣', 58760, 1, '/api/file/song/c8d0ae1da1790bc6d179d7b59d037cc3.wav'),
(6, 2, '凌晨三点的便利店', '流行', 51230, 1, '/api/file/song/15cad74336305a00477a3db32d8f950a.wav'),
(7, 5, '雾中风景', '后摇', 44180, 1, '/api/file/song/8fa2fef3deab3df8cecc3564c4d2b694.wav'),
(8, 3, '仲夏夜序曲', '电子', 39870, 1, '/api/file/song/afff8bfb5d33a01791a854f2bfb71052.wav'),
(9, 4, '野火', '摇滚', 33560, 1, '/api/file/song/94dbcd28620ffc77c0de53e4c94cd730.wav'),
(10, 5, '远航', '后摇', 28940, 1, '/api/file/song/02f4259b96afe3ecc7b3dbc0e591a0f1.wav');

USE yuexiang_playlist;
INSERT INTO `playlist` (`id`, `user_id`, `title`, `description`, `tag`, `is_official`, `play_count`) VALUES
(1, 0, '民谣小馆', '适合深夜单曲循环的温柔民谣。', '民谣', 1, 120300),
(2, 0, '通勤能量站', '早高峰也要元气满满。', '流行', 1, 98600),
(3, 0, '电子夏夜', '合成器与海风。', '电子', 1, 76540),
(4, 0, '摇滚现场', '把耳机开到最大声。', '摇滚', 1, 54300),
(5, 0, '后摇漫游', '无人声的辽阔与自由。', '后摇', 1, 41200);

-- -----------------------------------------------------------
-- 8. yuexiang_file：上传下载（文件元数据；本地磁盘存储起步）
-- -----------------------------------------------------------
CREATE DATABASE IF NOT EXISTS yuexiang_file DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE yuexiang_file;

DROP TABLE IF EXISTS `file_info`;
CREATE TABLE `file_info` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `original_name` VARCHAR(255) NOT NULL COMMENT '原始文件名',
    `stored_name`  VARCHAR(255) NOT NULL COMMENT '存储文件名（UUID）',
    `url`          VARCHAR(500) NOT NULL COMMENT '访问URL',
    `type`         VARCHAR(20)  NOT NULL COMMENT '类型 SONG歌曲 COVER封面 AVATAR头像',
    `size`         BIGINT       NOT NULL COMMENT '文件大小（字节）',
    `uploader_id`  BIGINT       DEFAULT NULL COMMENT '上传者用户ID',
    `deleted`      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    PRIMARY KEY (`id`),
    KEY `idx_uploader` (`uploader_id`)
) ENGINE = InnoDB COMMENT '文件元数据表';
