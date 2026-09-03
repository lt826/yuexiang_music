-- ============================================================
-- 轻音乐曲库 + 20 个演示用户 种子数据
-- 音频复用项目内已有的免版权占位音频（data/file/song 目录）
-- 用户密码均为 123456（BCrypt 加密，与后端 BCryptPasswordEncoder 兼容）
-- 可重复执行：先删后插
-- ============================================================

USE yuexiang_singer;
DELETE FROM `singer` WHERE `id` = 20;
INSERT INTO `singer` (`id`, `name`, `introduction`, `status`) VALUES
(20, '轻音工坊', '专注轻音乐/纯音乐制作，适合学习、工作与放松。', 1);

USE yuexiang_song;
DELETE FROM `song` WHERE `id` BETWEEN 47 AND 56;
INSERT INTO `song` (`id`, `singer_id`, `name`, `style`, `play_count`, `status`, `url`) VALUES
(47, 20, '晨光微语',   '轻音乐', 45210, 1, '/api/file/song/6421900dbc2a4907ad9e43a453ee7ecf.mp3'),
(48, 20, '云端漫步',   '轻音乐', 39870, 1, '/api/file/song/27b76c7897574bb99e7828f8a3cbe116.mp3'),
(49, 20, '风铃与夏',   '轻音乐', 36120, 1, '/api/file/song/774556cec11b4a519196d08a86be0623.mp3'),
(50, 20, '林间小径',   '轻音乐', 33450, 1, '/api/file/song/5a89cf4f3751419c9fd7cf7a68d0cf9e.mp3'),
(51, 20, '雨后咖啡馆', '轻音乐', 30980, 1, '/api/file/song/a1f3c2d84e9b47c6b8d2051e3f7a9042.mp3'),
(52, 20, '星夜钢琴',   '轻音乐', 28760, 1, '/api/file/song/c3d7a1e25b8f49c68e15d3a7c9b02e44.mp3'),
(53, 20, '暖阳午后',   '轻音乐', 26430, 1, '/api/file/song/d4a8b2f36c9d40d78f26e4b8d0c13a55.mp3'),
(54, 20, '海底呼吸',   '轻音乐', 24180, 1, '/api/file/song/f6c0d4b58e1f42970a48c6d0f2e35c77.mp3'),
(55, 20, '纸短情长（纯音乐版）', '轻音乐', 21940, 1, '/api/file/song/6421900dbc2a4907ad9e43a453ee7ecf.mp3'),
(56, 20, '晚安星球',   '轻音乐', 19870, 1, '/api/file/song/27b76c7897574bb99e7828f8a3cbe116.mp3');

USE yuexiang_user;
-- 20 个演示用户，密码统一为 123456（BCrypt）
DELETE FROM `user` WHERE `username` IN
('musiclover01','quietnight','sunnyday','rainy_cafe','piano_keys',
 'starlight','forest_walk','ocean_breeze','late_night','early_bird',
 'cloud9','melody20','vinyl_fan','jazz_lee','mocha_tan',
 'aurora_wang','echo_chen','luna_zhao','breeze_lin','nova_shu');
INSERT INTO `user` (`username`, `password`, `nickname`, `gender`, `signature`, `role`, `status`) VALUES
('musiclover01', '$2b$10$DPwuNir2xIwWvalzsHedVe1f8n4vDCNxZIDdhOYdCsEEqaa/500rK', '爱乐人01',  1, '每天都要听歌', 'USER', 1),
('quietnight',   '$2b$10$DPwuNir2xIwWvalzsHedVe1f8n4vDCNxZIDdhOYdCsEEqaa/500rK', '静夜',      2, '夜深了，听点轻音乐', 'USER', 1),
('sunnyday',     '$2b$10$DPwuNir2xIwWvalzsHedVe1f8n4vDCNxZIDdhOYdCsEEqaa/500rK', '晴天小猪',  2, '保持好心情', 'USER', 1),
('rainy_cafe',   '$2b$10$DPwuNir2xIwWvalzsHedVe1f8n4vDCNxZIDdhOYdCsEEqaa/500rK', '雨天咖啡',  2, '雨声和咖啡更配', 'USER', 1),
('piano_keys',   '$2b$10$DPwuNir2xIwWvalzsHedVe1f8n4vDCNxZIDdhOYdCsEEqaa/500rK', '黑白键',    1, '钢琴爱好者', 'USER', 1),
('starlight',    '$2b$10$DPwuNir2xIwWvalzsHedVe1f8n4vDCNxZIDdhOYdCsEEqaa/500rK', '星光',      2, '追星赶月', 'USER', 1),
('forest_walk',  '$2b$10$DPwuNir2xIwWvalzsHedVe1f8n4vDCNxZIDdhOYdCsEEqaa/500rK', '林间漫步',  1, '亲近大自然', 'USER', 1),
('ocean_breeze', '$2b$10$DPwuNir2xIwWvalzsHedVe1f8n4vDCNxZIDdhOYdCsEEqaa/500rK', '海风',      2, '面朝大海', 'USER', 1),
('late_night',   '$2b$10$DPwuNir2xIwWvalzsHedVe1f8n4vDCNxZIDdhOYdCsEEqaa/500rK', '夜猫子',    1, '凌晨的耳机党', 'USER', 1),
('early_bird',   '$2b$10$DPwuNir2xIwWvalzsHedVe1f8n4vDCNxZIDdhOYdCsEEqaa/500rK', '早起鸟',    1, '晨跑必备 BGM', 'USER', 1),
('cloud9',       '$2b$10$DPwuNir2xIwWvalzsHedVe1f8n4vDCNxZIDdhOYdCsEEqaa/500rK', '九霄云上',  2, '快乐至上', 'USER', 1),
('melody20',     '$2b$10$DPwuNir2xIwWvalzsHedVe1f8n4vDCNxZIDdhOYdCsEEqaa/500rK', '旋律控',    1, '旋律就是一切', 'USER', 1),
('vinyl_fan',    '$2b$10$DPwuNir2xIwWvalzsHedVe1f8n4vDCNxZIDdhOYdCsEEqaa/500rK', '黑胶粉',    1, '复古音质党', 'USER', 1),
('jazz_lee',     '$2b$10$DPwuNir2xIwWvalzsHedVe1f8n4vDCNxZIDdhOYdCsEEqaa/500rK', '李爵士',    1, '即兴万岁', 'USER', 1),
('mocha_tan',    '$2b$10$DPwuNir2xIwWvalzsHedVe1f8n4vDCNxZIDdhOYdCsEEqaa/500rK', '摩卡谭',    2, '音乐配摩卡', 'USER', 1),
('aurora_wang',  '$2b$10$DPwuNir2xIwWvalzsHedVe1f8n4vDCNxZIDdhOYdCsEEqaa/500rK', '极光',      2, '追逐极光的人', 'USER', 1),
('echo_chen',    '$2b$10$DPwuNir2xIwWvalzsHedVe1f8n4vDCNxZIDdhOYdCsEEqaa/500rK', '回声',      1, '山谷回声', 'USER', 1),
('luna_zhao',    '$2b$10$DPwuNir2xIwWvalzsHedVe1f8n4vDCNxZIDdhOYdCsEEqaa/500rK', '月神',      2, '月光下的歌', 'USER', 1),
('breeze_lin',   '$2b$10$DPwuNir2xIwWvalzsHedVe1f8n4vDCNxZIDdhOYdCsEEqaa/500rK', '微风',      1, '轻音乐常驻', 'USER', 1),
('nova_shu',     '$2b$10$DPwuNir2xIwWvalzsHedVe1f8n4vDCNxZIDdhOYdCsEEqaa/500rK', '新星',      2, '新星正在升起', 'USER', 1);
