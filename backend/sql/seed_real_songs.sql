-- ============================================================
-- 真实曲库种子数据：30 首真实歌曲元数据（歌名/歌手/风格为公开事实信息）
-- 音频文件复用项目内已有的免版权占位音频（本地 data/file/song 目录）
-- 用途：课程演示 — 让首页/搜索/榜单展示真实歌名与歌手
-- 执行顺序：先 init.sql（建库建表），再本文件
-- 注意：id=12「晴天」若已由用户上传（含真实音频与歌词），本脚本不覆盖；
--       已上传环境下已将 id=13 的种子版删除并把歌单指向用户原版
-- ============================================================

USE yuexiang_singer;

-- 真实歌手（简介为公开资料概括）
INSERT INTO `singer` (`id`, `name`, `introduction`, `status`) VALUES
(7,  '周杰伦',   '华语流行天王，融合中国风与 R&B 的开创者。', 1),
(8,  '林俊杰',   '新加坡唱作人，人称"行走的CD"。', 1),
(9,  '陈奕迅',   '香港实力唱将，情歌演绎细腻入微。', 1),
(10, '五月天',   '台湾摇滚天团，青春代名词。', 1),
(11, '邓紫棋',   '香港铁肺歌姬，高音穿透力极强。', 1),
(12, '薛之谦',   '内地唱作人，词曲皆有个人印记。', 1),
(13, '毛不易',   '内地民谣新声，词作平实动人。', 1),
(14, '李荣浩',   '内地全才音乐人，编曲制作一把抓。', 1),
(15, ' Taylor Swift', '美国创作型歌手，乡村转流行的巨星。', 1),
(16, 'Ed Sheeran', '英国唱作人，循环系和弦与温柔嗓音。', 1),
(17, 'Beyond',   '香港传奇摇滚乐队，影响几代人。', 1),
(18, '孙燕姿',   '新加坡天后，声线清亮独特。', 1)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

USE yuexiang_song;

-- 占位音频池（已存在于 data/file/song）
-- SET A = 4e4b27df71eee59c4c826e4d98120210.wav  SET B = 2869d8b0bc80c2cee88abc3a44c84296.wav
-- SET C = 1d7ddb79ffbabc0babbda1cbb9cbc65d.wav  SET D = 128da0cda7958d48e043dc2ceb991279.wav
-- SET E = c8d0ae1da1790bc6d179d7b59d037cc3.wav  SET F = 15cad74336305a00477a3db32d8f950a.wav
-- SET G = 8fa2fef3deab3df8cecc3564c4d2b694.wav  SET H = afff8bfb5d33a01791a854f2bfb71052.wav
-- SET I = 94dbcd28620ffc77c0de53e4c94cd730.wav  SET J = 02f4259b96afe3ecc7b3dbc0e591a0f1.wav

INSERT INTO `song` (`id`, `singer_id`, `name`, `style`, `play_count`, `status`, `url`) VALUES
-- 周杰伦
(13, 7, '晴天',       '流行', 98210432, 1, '/api/file/song/4e4b27df71eee59c4c826e4d98120210.wav'),
(14, 7, '七里香',     '流行', 87654321, 1, '/api/file/song/2869d8b0bc80c2cee88abc3a44c84296.wav'),
(15, 7, '稻香',       '流行', 75643210, 1, '/api/file/song/1d7ddb79ffbabc0babbda1cbb9cbc65d.wav'),
(16, 7, '夜曲',       '流行', 69876543, 1, '/api/file/song/128da0cda7958d48e043dc2ceb991279.wav'),
(17, 7, '青花瓷',     '中国风', 65432178, 1, '/api/file/song/c8d0ae1da1790bc6d179d7b59d037cc3.wav'),
-- 林俊杰
(18, 8, '江南',       '流行', 58765432, 1, '/api/file/song/15cad74336305a00477a3db32d8f950a.wav'),
(19, 8, '修炼爱情',   '流行', 51234098, 1, '/api/file/song/8fa2fef3deab3df8cecc3564c4d2b694.wav'),
(20, 8, '不为谁而作的歌', '流行', 45678912, 1, '/api/file/song/afff8bfb5d33a01791a854f2bfb71052.wav'),
-- 陈奕迅
(21, 9, '十年',       '流行', 88765432, 1, '/api/file/song/94dbcd28620ffc77c0de53e4c94cd730.wav'),
(22, 9, '浮夸',       '流行', 43210987, 1, '/api/file/song/02f4259b96afe3ecc7b3dbc0e591a0f1.wav'),
(23, 9, '孤勇者',     '流行', 99876543, 1, '/api/file/song/4e4b27df71eee59c4c826e4d98120210.wav'),
-- 五月天
(24, 10, '倔强',      '摇滚', 54321098, 1, '/api/file/song/2869d8b0bc80c2cee88abc3a44c84296.wav'),
(25, 10, '突然好想你', '摇滚', 49876543, 1, '/api/file/song/1d7ddb79ffbabc0babbda1cbb9cbc65d.wav'),
(26, 10, '知足',      '摇滚', 41234567, 1, '/api/file/song/128da0cda7958d48e043dc2ceb991279.wav'),
-- 邓紫棋
(27, 11, '光年之外',  '流行', 67891234, 1, '/api/file/song/c8d0ae1da1790bc6d179d7b59d037cc3.wav'),
(28, 11, '泡沫',      '流行', 39876521, 1, '/api/file/song/15cad74336305a00477a3db32d8f950a.wav'),
-- 薛之谦
(29, 12, '演员',      '流行', 58912345, 1, '/api/file/song/8fa2fef3deab3df8cecc3564c4d2b694.wav'),
(30, 12, '丑八怪',    '流行', 34567892, 1, '/api/file/song/afff8bfb5d33a01791a854f2bfb71052.wav'),
-- 毛不易
(31, 13, '消愁',      '民谣', 46789123, 1, '/api/file/song/94dbcd28620ffc77c0de53e4c94cd730.wav'),
(32, 13, '像我这样的人', '民谣', 38765412, 1, '/api/file/song/02f4259b96afe3ecc7b3dbc0e591a0f1.wav'),
-- 李荣浩
(33, 14, '李白',      '流行', 37896541, 1, '/api/file/song/4e4b27df71eee59c4c826e4d98120210.wav'),
(34, 14, '年少有为',  '流行', 42315678, 1, '/api/file/song/2869d8b0bc80c2cee88abc3a44c84296.wav'),
-- Taylor Swift
(35, 15, 'Love Story',    '乡村', 28976543, 1, '/api/file/song/1d7ddb79ffbabc0babbda1cbb9cbc65d.wav'),
(36, 15, 'Blank Space',   '流行', 27654321, 1, '/api/file/song/128da0cda7958d48e043dc2ceb991279.wav'),
-- Ed Sheeran
(37, 16, 'Shape of You',  '流行', 31234567, 1, '/api/file/song/c8d0ae1da1790bc6d179d7b59d037cc3.wav'),
(38, 16, 'Perfect',       '流行', 29876512, 1, '/api/file/song/15cad74336305a00477a3db32d8f950a.wav'),
-- Beyond
(39, 17, '海阔天空',  '摇滚', 78965412, 1, '/api/file/song/8fa2fef3deab3df8cecc3564c4d2b694.wav'),
(40, 17, '光辉岁月',  '摇滚', 56789123, 1, '/api/file/song/afff8bfb5d33a01791a854f2bfb71052.wav'),
-- 孙燕姿
(41, 18, '遇见',      '流行', 45678921, 1, '/api/file/song/94dbcd28620ffc77c0de53e4c94cd730.wav'),
(42, 18, '天黑黑',    '流行', 32145678, 1, '/api/file/song/02f4259b96afe3ecc7b3dbc0e591a0f1.wav')
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

-- 旧的虚构歌曲下架（保留晴天=12，其余演示曲转下架，让首页以真实曲目为主）
UPDATE `song` SET `status` = 2 WHERE `id` IN (1,2,3,4,5,6,7,8,9,10) ;
UPDATE `song` SET `status` = 2 WHERE `id` = 11;

-- 旧虚构歌手冻结（保留你的账号"好好学习"=6）
UPDATE yuexiang_singer.singer SET `status` = 3 WHERE `id` IN (1,2,3,4,5);

USE yuexiang_playlist;

-- 官方歌单更新为真实曲目主题
UPDATE `playlist` SET `title` = '华语经典·那些年循环过的歌', `description` = '周董、Eason、JJ——刻进DNA的旋律。', `tag` = '经典' WHERE `id` = 1;
UPDATE `playlist` SET `title` = '摇滚不死', `description` = '五月天与 Beyond，把耳机开到最大声。', `tag` = '摇滚' WHERE `id` = 4;
UPDATE `playlist` SET `title` = '欧美热单', `description` = 'Taylor Swift 与 Ed Sheeran 的全球热单。', `tag` = '欧美' WHERE `id` = 3;
UPDATE `playlist` SET `title` = '深夜民谣', `description` = '毛不易的温柔叙事，适合安静的夜。', `tag` = '民谣' WHERE `id` = 5;
UPDATE `playlist` SET `title` = '华语流行精选', `description` = '当下最热的华语单曲循环。', `tag` = '流行' WHERE `id` = 2;

-- 歌单-歌曲关联（覆盖新曲目）
DELETE FROM `playlist_song` WHERE `playlist_id` IN (1,2,3,4,5);
INSERT INTO `playlist_song` (`playlist_id`, `song_id`, `sort`) VALUES
-- 华语经典
(1, 13, 1), (1, 14, 2), (1, 21, 3), (1, 18, 4), (1, 39, 5),
-- 华语流行精选
(2, 23, 1), (2, 27, 2), (2, 29, 3), (2, 34, 4), (2, 41, 5),
-- 欧美热单
(3, 35, 1), (3, 36, 2), (3, 37, 3), (3, 38, 4),
-- 摇滚不死
(4, 24, 1), (4, 25, 2), (4, 26, 3), (4, 39, 4), (4, 40, 5),
-- 深夜民谣
(5, 31, 1), (5, 32, 2);
