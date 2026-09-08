-- ============================================================
-- 歌曲点赞 + 评论：增量建表脚本（可重复执行，表存在则跳过）
-- 两张表都归 yuexiang_song 库（点赞/评论随歌曲生命周期）
-- 执行：mysql -uroot -p < add_song_like_comment.sql
-- ============================================================
USE yuexiang_song;

-- 歌曲点赞表：唯一约束保证一个用户对一首歌只能点赞一次；
-- 取消点赞即物理 DELETE（对齐 favorite_song 惯例，无 deleted 字段）
CREATE TABLE IF NOT EXISTS `song_like` (
    `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '点赞记录ID',
    `user_id`     BIGINT   NOT NULL COMMENT '用户ID（来自 yuexiang_user 服务）',
    `song_id`     BIGINT   NOT NULL COMMENT '歌曲ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '点赞时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_song` (`user_id`, `song_id`),
    KEY `idx_song` (`song_id`)
) ENGINE = InnoDB COMMENT '歌曲点赞表';

-- 歌曲评论表：平铺列表（无楼中楼），逻辑删除（用户删自己的评论）
CREATE TABLE IF NOT EXISTS `song_comment` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '评论ID',
    `song_id`     BIGINT       NOT NULL COMMENT '歌曲ID',
    `user_id`     BIGINT       NOT NULL COMMENT '评论人用户ID（来自 yuexiang_user 服务）',
    `content`     VARCHAR(500) NOT NULL COMMENT '评论内容',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0正常 1已删除',
    `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '评论时间',
    `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_song_time` (`song_id`, `create_time` DESC)
) ENGINE = InnoDB COMMENT '歌曲评论表';
