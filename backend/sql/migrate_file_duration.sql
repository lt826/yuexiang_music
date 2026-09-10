-- ============================================================
-- 迁移：file_info 表增加 duration 列（2026-09-10 歌曲时长解析）
-- 现象：缺列时所有 /file/upload 上传都报 500"操作失败"
--       （文件落盘成功但元数据 INSERT 报 Unknown column 'duration'）
-- 幂等：列已存在则跳过
-- 执行：mysql -uroot -p < sql/migrate_file_duration.sql
-- ============================================================
USE yuexiang_file;

SET @has_duration := (SELECT COUNT(*) FROM information_schema.COLUMNS
                      WHERE TABLE_SCHEMA = 'yuexiang_file'
                        AND TABLE_NAME = 'file_info'
                        AND COLUMN_NAME = 'duration');
SET @ddl := IF(@has_duration = 0,
    'ALTER TABLE `file_info` ADD COLUMN `duration` INT NOT NULL DEFAULT 0 COMMENT ''音频时长（秒，SONG 类型上传时解析；0=未知）'' AFTER `size`',
    'SELECT ''duration 列已存在，跳过''');
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
