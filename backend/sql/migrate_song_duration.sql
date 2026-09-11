-- ============================================================
-- 迁移：song 表增加 duration 列（2026-09-04 电台真实时长时间轴）
--
-- 背景：原时间轴每首歌固定占 240s，真实时长不足的歌播完后
--       客户端要空等时间轴走完剩余秒数才换下一首（卡几十秒）。
--       改为按真实时长排轴后空窗消失。
--
-- 执行：mysql -uroot -p < sql/migrate_song_duration.sql
-- 幂等：重复执行不报错
-- ============================================================
USE yuexiang_song;

-- 加列（存在则跳过）
SET @has_duration := (SELECT COUNT(*) FROM information_schema.COLUMNS
                      WHERE TABLE_SCHEMA = 'yuexiang_song'
                        AND TABLE_NAME = 'song'
                        AND COLUMN_NAME = 'duration');
SET @ddl := IF(@has_duration = 0,
    'ALTER TABLE `song` ADD COLUMN `duration` INT NOT NULL DEFAULT 0 COMMENT ''音频时长（秒；0=未知。上传时解析，电台时间轴用）'' AFTER `play_count`',
    'SELECT ''duration 列已存在，跳过''');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SELECT COUNT(*) AS 未知时长歌曲数 FROM `song` WHERE `duration` = 0;
