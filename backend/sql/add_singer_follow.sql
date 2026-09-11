-- ============================================================
-- 歌手关注功能：粉丝关注表（yuexiang_singer 库）
-- 用法：mysql -uroot -p < add_singer_follow.sql
-- ============================================================
USE yuexiang_singer;

-- 歌手关注表：唯一约束保证一个用户对一个歌手只能关注一次；
-- 取消关注即物理 DELETE，无 deleted 字段（对齐 song_like / favorite_song 惯例）
CREATE TABLE IF NOT EXISTS `singer_follow` (
    `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '关注记录ID',
    `user_id`     BIGINT   NOT NULL COMMENT '关注者用户ID（来自 yuexiang_user 服务）',
    `singer_id`   BIGINT   NOT NULL COMMENT '被关注歌手ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '关注时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_singer` (`user_id`, `singer_id`),
    KEY `idx_singer` (`singer_id`)
) ENGINE = InnoDB COMMENT '歌手关注表';
