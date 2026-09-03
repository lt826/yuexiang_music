-- 晴天数据规范化：清理上传残留的随机前缀，歌名对齐显示
USE yuexiang_song;
UPDATE `song`
SET `name` = '晴天'
WHERE `id` = 12 AND `name` LIKE '%晴天%';
