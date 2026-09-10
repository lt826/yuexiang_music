-- ============================================================
-- 心情电台：8 个频道的固定节目单（官方歌单）
-- 频道 key 与前端 CHANNELS 一一对应：sleep/study/commute/sport/emo/happy/rain/coffee
-- user_id = 0 表示系统官号持有；is_official = 1 全站可见（顺带填充首页"官方推荐"位）
-- 歌单顺序即播出一轮的顺序（playlist_song.sort）
-- 幂等：重复执行先清理同名电台歌单
-- ============================================================
USE yuexiang_playlist;

DELETE ps FROM playlist_song ps
JOIN playlist p ON p.id = ps.playlist_id
WHERE p.title LIKE '悦享电台 · %';
DELETE FROM playlist WHERE title LIKE '悦享电台 · %';

-- 频道 1：睡前（sleep）—— 慢歌、轻声、安定
INSERT INTO playlist (id, user_id, title, description, tag, is_official, published, play_count)
VALUES (26, 0, '悦享电台 · 睡前 FM 88.4', '声音调暗，夜就深了。适合睡前放松的舒缓歌单。', '助眠', 1, 1, 0);
SET @pl := 26;
INSERT INTO playlist_song (playlist_id, song_id, sort) VALUES
    (@pl, 56, 1),   -- 晚安星球：睡前氛围轻音乐
    (@pl, 52, 2),   -- 星夜钢琴：夜色钢琴曲
    (@pl, 79, 3),   -- 呼吸练习：安定助眠
    (@pl, 55, 4),   -- 纸短情长（纯音乐版）：舒缓器乐
    (@pl, 78, 5),   -- 深空冥想：冥想放松
    (@pl, 57, 6);   -- 月下抚琴：夜色国风

-- 频道 2：学习（study）—— 平稳不吵、无人声或旋律克制
INSERT INTO playlist (id, user_id, title, description, tag, is_official, published, play_count)
VALUES (27, 0, '悦享电台 · 学习 FM 91.7', '专注，是不被打扰的歌。旋律平稳的学习背景乐。', '专注', 1, 1, 0);
SET @pl := 27;
INSERT INTO playlist_song (playlist_id, song_id, sort) VALUES
    (@pl, 47, 1),   -- 晨光微语：轻音乐无干扰
    (@pl, 50, 2),   -- 林间小径：自然环境轻音乐
    (@pl, 48, 3),   -- 云端漫步：旋律平稳
    (@pl, 49, 4),   -- 风铃与夏：轻快不抢注意力
    (@pl, 80, 5);   -- 雨林白噪音：白噪音专注

-- 频道 3：通勤（commute）—— 耐听顺口、节奏中速
INSERT INTO playlist (id, user_id, title, description, tag, is_official, published, play_count)
VALUES (28, 0, '悦享电台 · 通勤 FM 94.0', '路很远，歌要够长。通勤路上轻松耐听的陪伴。', '通勤', 1, 1, 0);
SET @pl := 28;
INSERT INTO playlist_song (playlist_id, song_id, sort) VALUES
    (@pl, 60, 1),   -- 慢慢喜欢：中速流行耐听
    (@pl, 62, 2),   -- 晚风信笺：轻快流行
    (@pl, 76, 3),   -- 毕业那年的夏天：民谣叙事
    (@pl, 77, 4),   -- 同桌的你2026：民谣回忆
    (@pl, 84, 5),   -- 一个人的城市：都市流行
    (@pl, 61, 6);   -- 温柔坠落：抒情流行

-- 频道 4：运动（sport）—— 节奏强、有推动力
INSERT INTO playlist (id, user_id, title, description, tag, is_official, published, play_count)
VALUES (29, 0, '悦享电台 · 运动 FM 97.5', '心跳跟上鼓点。节奏感强的运动推力歌单。', '运动', 1, 1, 0);
SET @pl := 29;
INSERT INTO playlist_song (playlist_id, song_id, sort) VALUES
    (@pl, 68, 1),   -- 零点蹦迪：电子节拍推进
    (@pl, 66, 2),   -- 午夜脉冲：电舞曲
    (@pl, 67, 3),   -- 激光雨：律动强烈
    (@pl, 75, 4),   -- 巷口Freestyle：说唱爆发
    (@pl, 63, 5),   -- 霓虹废墟：摇滚推力
    (@pl, 82, 6),   -- 糖果星球：节奏鲜明
    (@pl, 83, 7);   -- 闪亮登场：活力偶像

-- 频道 5：emo（emo）—— 伤感情歌、情绪出口
INSERT INTO playlist (id, user_id, title, description, tag, is_official, published, play_count)
VALUES (30, 0, '悦享电台 · emo FM 101.3', '丧的时候，歌懂你。情绪化的伤感情歌集中营。', 'emo', 1, 1, 0);
SET @pl := 30;
INSERT INTO playlist_song (playlist_id, song_id, sort) VALUES
    (@pl, 89, 1),   -- 沦陷：失恋叙事
    (@pl, 87, 2),   -- 孤独颂歌：孤独情绪
    (@pl, 88, 3),   -- 海市蜃楼：求不得
    (@pl, 90, 4),   -- 雾里：迷茫低落
    (@pl, 92, 5),   -- 好想抱住你：想念
    (@pl, 65, 6),   -- 碎玻璃：破碎感摇滚
    (@pl, 86, 7);   -- 野蔷薇：倔强的伤

-- 频道 6：开心（happy）—— 明亮欢快、好心情配副歌
INSERT INTO playlist (id, user_id, title, description, tag, is_official, published, play_count)
VALUES (31, 0, '悦享电台 · 开心 FM 103.6', '好心情要配副歌。明亮欢快的高兴歌单。', '欢快', 1, 1, 0);
SET @pl := 31;
INSERT INTO playlist_song (playlist_id, song_id, sort) VALUES
    (@pl, 81, 1),   -- 心跳信号：甜美偶像风
    (@pl, 82, 2),   -- 糖果星球：明快跳跃
    (@pl, 83, 3),   -- 闪亮登场：活力四射
    (@pl, 60, 4),   -- 慢慢喜欢：甜甜恋爱感
    (@pl, 49, 5),   -- 风铃与夏：夏日明快
    (@pl, 94, 6);   -- 愿你：温暖祝福

-- 频道 7：雨天（rain）—— 慵懒舒缓、有氛围感
INSERT INTO playlist (id, user_id, title, description, tag, is_official, published, play_count)
VALUES (32, 0, '悦享电台 · 雨天 FM 106.2', '雨声和它更配。雨天窗边的氛围感歌单。', '雨天', 1, 1, 0);
SET @pl := 32;
INSERT INTO playlist_song (playlist_id, song_id, sort) VALUES
    (@pl, 51, 1),   -- 雨后咖啡馆：雨后氛围感
    (@pl, 90, 2),   -- 雾里：朦胧雨天感
    (@pl, 53, 3),   -- 暖阳午后：慵懒舒缓
    (@pl, 55, 4),   -- 纸短情长（纯音乐版）：绵长器乐
    (@pl, 58, 5),   -- 长安秋辞：秋意国风
    (@pl, 71, 6);   -- Blue Midnight：雨夜爵士

-- 频道 8：咖啡（coffee）—— 慵懒轻柔、咖啡馆调性
INSERT INTO playlist (id, user_id, title, description, tag, is_official, published, play_count)
VALUES (33, 0, '悦享电台 · 咖啡 FM 108.9', '一杯咖啡一首歌。咖啡馆里的慵懒轻柔。', '咖啡', 1, 1, 0);
SET @pl := 33;
INSERT INTO playlist_song (playlist_id, song_id, sort) VALUES
    (@pl, 51, 1),   -- 雨后咖啡馆：咖啡馆主题曲
    (@pl, 73, 2),   -- 慢摇摆：爵士慵懒
    (@pl, 72, 3),   -- 烟嗓威士忌：微醺爵士
    (@pl, 71, 4),   -- Blue Midnight：深夜蓝调
    (@pl, 74, 5),   -- 老街故事：说唱叙事
    (@pl, 53, 6);   -- 暖阳午后：午后轻柔
