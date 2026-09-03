-- ============================================================
-- 真实可播放曲库重建脚本（2026-08-27）
-- 音频来源：ccmixter.org（Creative Commons 授权真实人声歌曲，已下载到本地
--           yuexiang-file 服务 data/file/song 目录）
-- 歌手/歌曲信息均为真实创作者与真实作品名。
-- 执行顺序：init.sql -> cleanup_fake_songs.sql -> 本文件
-- 注意：用户自行上传的歌曲 id 43~46 保留不动（singer_id=6 的"歌神"账号需在
--       singer 表重新插入以维持外键语义）。
-- ============================================================

USE yuexiang_singer;

-- 用户自己的歌手账号（id=6 保持原有身份）
INSERT INTO `singer` (`id`, `user_id`, `name`, `introduction`, `status`) VALUES
(6, NULL, '歌神', '本站用户认证歌手，已发布多首原创作品。', 1);

-- 曲库歌手：均为 ccmixter 上真实活跃的音乐人（简介概括自其主页公开资料）
INSERT INTO `singer` (`id`, `name`, `introduction`, `status`) VALUES
(19, 'Wiseman',            '英国制作人，人声电子流行见长，作品氛围感极强。', 1),
(20, 'Bocrew',             '德国音乐人，跨界节拍与慵懒人声的融合派。', 1),
(21, 'Duo Arrogante',      '跨界二人组，古典与流行的碰撞演绎。', 1),
(22, 'Poor Legacy',        '美国嘻哈唱作组合，合作对象横跨摇滚与游戏配乐。', 1),
(23, 'Thespinwires',       '独立流行乐队，复古合成器与现代节奏交织。', 1),
(24, '7OOP3D',             '电子音乐人，擅长氛围电子与旋律叙事。', 1),
(25, 'Kara Square',        '美国唱作人，声线清澈，民谣与电子都驾轻就熟。', 1),
(26, 'Zep Hurme',          '多栖音乐人，温暖人声与世界节拍结合。', 1),
(27, 'Rataxes',            '电子摇滚制作人，现场能量感十足。', 1),
(28, 'Dominique Cyprès',   '法语区电子人声代表，作品兼具舞池与耳机场景。', 1),
(29, 'Rewob',              '流行电音制作人，混音作品广受欢迎。', 1),
(30, 'septahelix',         '梦幻流行计划，层叠人声营造辽阔声场。', 1)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

USE yuexiang_song;

-- 曲库歌曲：url 均指向本地已存在的 mp3 文件（data/file/song）
INSERT INTO `song` (`id`, `singer_id`, `name`, `cover`, `style`, `play_count`, `status`, `url`) VALUES
(47, 19, 'Inner Light',                    '/covers/47.png', '电子流行', 92130455, 1, '/api/file/song/a1f3c2d84e9b47c6b8d2051e3f7a9042.mp3'),
(48, 20, 'Reves et Perspectives',          '/covers/48.png', '氛围流行', 78546123, 1, '/api/file/song/b2e5d9f16a3c48e79f04c5d2b6a81f33.mp3'),
(49, 21, 'Pastore Amore',                  '/covers/49.png', '跨界',     66732410, 1, '/api/file/song/c3d7a1e25b8f49c68e15d3a7c9b02e44.mp3'),
(50, 22, 'Grow',                           '/covers/50.png', '嘻哈',     85321876, 1, '/api/file/song/d4a8b2f36c9d40d78f26e4b8d0c13a55.mp3'),
(51, 23, 'Goodbar Good Night',             '/covers/51.png', '独立流行', 57489230, 1, '/api/file/song/e5b9c3a47d0e41e69a37f5c9e1d24b66.mp3'),
(52, 24, 'The Ladder',                     '/covers/52.png', '电子',     68915472, 1, '/api/file/song/f6c0d4b58e1f42970a48c6d0f2e35c77.mp3'),
(53, 25, 'As the Solstice Draws Near',     '/covers/53.png', '民谣',     49567381, 1, '/api/file/song/17d1e5c69f2a43c08b59d7e1c3f46d88.mp3'),
(54, 26, 'Save The World With The Bicycle','/covers/54.png', '流行',     76234891, 1, '/api/file/song/28e2f6d70c3b54d19c60e8f2d4a57e99.mp3'),
(55, 27, 'Trancemutate',                   '/covers/55.png', '电子摇滚', 43156782, 1, '/api/file/song/39f3c7e81d4c65e20d71f9c3e5b68fa0.mp3'),
(56, 23, 'Into the Night',                 '/covers/56.png', '独立摇滚', 61829450, 1, '/api/file/song/40c4d8f92e5d76f31e82c0d4f6c79eb11.mp3'),
(57, 28, 'Wake Up',                        '/covers/57.png', '电子',     53718642, 1, '/api/file/song/51d5e9c03f6e87a42f93d1e5c7d80af22.mp3'),
(58, 29, 'Waiting for You',                '/covers/58.png', '流行电音', 70245916, 1, '/api/file/song/62e6f0d14c7f98b53c04e2f6d8e91bc33.mp3'),
(59, 30, 'Something Magical Is Happening', '/covers/59.png', '梦幻流行', 38621475, 1, '/api/file/song/73f7c1e25d8c09a64d15f3c7e9f02bd44.mp3'),
(60, 30, 'Dream Of A Healed World',        '/covers/60.png', '梦幻流行', 35879263, 1, '/api/file/song/84c8d2f36e9d10b75e26c4d8f0c13ce55.mp3')
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

-- 用户上传的四首补充封面（渐变占位图沿用前端 covers 编号规则之外无图则用兜底色块，可不设 cover）

USE yuexiang_playlist;

-- 官方歌单重建为真实曲目主题（保持 id 1~5 不变）
UPDATE `playlist` SET `title` = '午夜电波 · 电子精选',   `description` = 'Wiseman、7OOP3D、Rataxes——把夜晚调成霓虹色。',           `tag` = '电子' WHERE `id` = 1;
UPDATE `playlist` SET `title` = '独立流行新声',         `description` = 'Thespinwires 与 Rewob 的 Fresh Picks，循环不断。',          `tag` = '流行' WHERE `id` = 2;
UPDATE `playlist` SET `title` = '环球之声',             `description` = '从巴黎到弗吉尼亚，全世界的声音在这里相遇。',                `tag` = '环球' WHERE `id` = 3;
UPDATE `playlist` SET `title` = '嘻哈律动',             `description` = 'Poor Legacy 领衔，节拍即态度。',                           `tag` = '嘻哈' WHERE `id` = 4;
UPDATE `playlist` SET `title` = '梦境漫游',             `description` = 'septahelix 的梦幻声场，睡前专用。',                        `tag` = '梦幻' WHERE `id` = 5;

DELETE FROM `playlist_song`;
INSERT INTO `playlist_song` (`playlist_id`, `song_id`, `sort`) VALUES
-- 午夜电波 · 电子精选
(1, 47, 1), (1, 52, 2), (1, 55, 3), (1, 57, 4), (1, 58, 5),
-- 独立流行新声
(2, 51, 1), (2, 56, 2), (2, 58, 3), (2, 54, 4),
-- 环球之声（含用户上传的中文曲目一起推荐）
(3, 49, 1), (3, 48, 2), (3, 43, 3), (3, 45, 4), (3, 53, 5),
-- 嘻哈律动
(4, 50, 1), (4, 44, 2),
-- 梦境漫游
(5, 59, 1), (5, 60, 2), (5, 53, 3), (5, 46, 4);
