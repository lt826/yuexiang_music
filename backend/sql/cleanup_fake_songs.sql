-- ============================================================
-- 清理无法播放的假歌曲（2026-08-27）
-- 背景：种子脚本曾插入 id 1~42 的歌曲元数据，但指向的 wav 占位音频
--       早已从 data/file/song 目录移除，全部无法播放。
--       仅保留用户真实上传的 id 43~46 四首。
-- 附带：清理失效轮播关联、官方歌单曲目、收藏与历史孤儿记录、无效歌手。
-- 执行顺序：在 init.sql 之后执行本文件。
-- ============================================================

USE yuexiang_playlist;
-- 官方歌单里的曲目全部指向假歌曲，先清空关联
DELETE FROM `playlist_song`;

USE yuexiang_favorite;
-- 收藏/收听历史中对已删歌曲的引用一并清理
DELETE FROM `favorite_song` WHERE `song_id` <= 42;
DELETE FROM `listen_history` WHERE `song_id` <= 42;

USE yuexiang_admin;
-- 轮播图若关联了将删除的歌曲，解除关联（保留图片展示）
UPDATE `banner` SET `song_id` = NULL WHERE `song_id` IS NOT NULL AND `song_id` <= 42;

USE yuexiang_song;
-- 物理删除所有不可播放歌曲（含重复的种子版晴天 id=12/13），仅保留用户上传的 43~46
DELETE FROM `song` WHERE `id` <= 42;

USE yuexiang_singer;
-- 冻结的旧虚构歌手彻底移除；真实种子歌手也先移除，由 seed_real_audio.sql 重建
DELETE FROM `singer`;
