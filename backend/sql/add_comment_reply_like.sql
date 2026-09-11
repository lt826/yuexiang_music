-- ============================================================
-- 评论体系升级：楼中楼回复 + 评论点赞（yuexiang_song 库）
-- 用法（路径含中文，mysql source 读不到，走管道）：
-- Get-Content -Raw -Encoding UTF8 add_comment_reply_like.sql | & mysql -uroot -p
-- ============================================================
USE yuexiang_song;

-- 回复挂载：root_id = 所属根评论ID（根评论自身为 NULL，回复直挂根避免递归无限深）
ALTER TABLE `song_comment`
    ADD COLUMN `root_id` BIGINT DEFAULT NULL COMMENT '根评论ID（NULL=根评论；回复直挂根）' AFTER `user_id`,
    ADD COLUMN `reply_user_id` BIGINT DEFAULT NULL COMMENT '被回复的用户ID（回复场景展示"回复 @xx"）' AFTER `root_id`,
    ADD COLUMN `like_count` INT NOT NULL DEFAULT 0 COMMENT '点赞数（冗余计数，点赞接口维护）' AFTER `content`,
    ADD COLUMN `reply_count` INT NOT NULL DEFAULT 0 COMMENT '回复数（冗余计数，回复时维护）' AFTER `like_count`,
    ADD KEY `idx_root` (`root_id`),
    ADD KEY `idx_song_like` (`song_id`, `like_count`);

-- 评论点赞表：唯一约束一人一评一次；取消即物理 DELETE
CREATE TABLE IF NOT EXISTS `song_comment_like` (
    `id`          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '点赞记录ID',
    `user_id`     BIGINT   NOT NULL COMMENT '点赞用户ID',
    `comment_id`  BIGINT   NOT NULL COMMENT '评论ID',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '点赞时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_comment` (`user_id`, `comment_id`),
    KEY `idx_comment` (`comment_id`)
) ENGINE = InnoDB COMMENT '评论点赞表';
