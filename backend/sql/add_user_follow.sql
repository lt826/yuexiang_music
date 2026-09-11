-- ============================================================
-- 用户互关功能：关注/粉丝表（yuexiang_user 库）
-- 用法：mysql -uroot -p < add_user_follow.sql
-- 说明：与歌手关注（yuexiang_singer.singer_follow）相互独立——
--       singer_follow 是"粉丝 → 认证歌手"，本表是"用户 → 用户"的社交关注
-- ============================================================
USE yuexiang_user;

-- 用户关注表：唯一约束保证一个用户对另一用户只能关注一次；
-- 取消关注即物理 DELETE，无 deleted 字段（对齐 singer_follow / song_like 惯例）
CREATE TABLE IF NOT EXISTS `user_follow` (
    `id`           BIGINT   NOT NULL AUTO_INCREMENT COMMENT '关注记录ID',
    `follower_id`  BIGINT   NOT NULL COMMENT '关注者用户ID',
    `following_id` BIGINT   NOT NULL COMMENT '被关注用户ID',
    `create_time`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '关注时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_follower_following` (`follower_id`, `following_id`),
    KEY `idx_following` (`following_id`)
) ENGINE = InnoDB COMMENT '用户关注表';
