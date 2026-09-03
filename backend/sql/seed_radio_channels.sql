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
INSERT INTO playlist (user_id, title, description, tag, is_official, published, play_count)
VALUES (0, '悦享电台 · 睡前 FM 88.4', '声音调暗，夜就深了。适合睡前放松的舒缓歌单。', '助眠', 1, 1, 0);
SET @pl := LAST_INSERT_ID();
INSERT INTO playlist_song (playlist_id, song_id, sort) VALUES
    (@pl, 25, 1),   -- Love Yourself - Justin Bieber：木吉他慢板
    (@pl, 35, 2),   -- 微醺 - 卡卡/胡夏：慵懒轻柔民谣
    (@pl, 18, 3),   -- 戏文说 - 于少保：舒缓古风叙事
    (@pl, 17, 4),   -- 唯一 - 于少保：安静抒情
    (@pl, 44, 5),   -- 演员 - 薛之谦：慢板钢琴叙事
    (@pl, 41, 6);   -- 认真的雪 - 薛之谦：冬夜慢歌

-- 频道 2：学习（study）—— 平稳不吵、无人声或旋律克制
INSERT INTO playlist (user_id, title, description, tag, is_official, published, play_count)
VALUES (0, '悦享电台 · 学习 FM 91.7', '专注，是不被打扰的歌。旋律平稳的学习背景乐。', '专注', 1, 1, 0);
SET @pl := LAST_INSERT_ID();
INSERT INTO playlist_song (playlist_id, song_id, sort) VALUES
    (@pl, 45, 1),   -- 诀别书 - 于少保：后摇纯音乐，无干扰
    (@pl, 28, 2),   -- 海阔天空 - 卡卡（古典）：器乐改编
    (@pl, 15, 3),   -- 晴天 - 周杰伦（电子风格标注）：旋律流畅不抢注意力
    (@pl, 47, 4),   -- 大眠 - 于少保：爵士慢摇，律动稳定
    (@pl, 18, 5);   -- 戏文说 - 于少保：民谣叙事，人声温和

-- 频道 3：通勤（commute）—— 耐听顺口、节奏中速
INSERT INTO playlist (user_id, title, description, tag, is_official, published, play_count)
VALUES (0, '悦享电台 · 通勤 FM 94.0', '路很远，歌要够长。通勤路上轻松耐听的陪伴。', '通勤', 1, 1, 0);
SET @pl := LAST_INSERT_ID();
INSERT INTO playlist_song (playlist_id, song_id, sort) VALUES
    (@pl, 32, 1),   -- Who Says - Selena Gomez：中速流行
    (@pl, 20, 2),   -- As Long As You Love Me - Justin Bieber
    (@pl, 43, 3),   -- 天外来物 - 薛之谦：流行耐听
    (@pl, 37, 4),   -- I don't wanna see you anymore - NINEONE：轻快叙事
    (@pl, 36, 5),   -- 东京不太热 - 卡卡：轻松说唱流行
    (@pl, 42, 6);   -- 绅士 - 薛之谦：中板流行

-- 频道 4：运动（sport）—— 节奏强、有推动力
INSERT INTO playlist (user_id, title, description, tag, is_official, published, play_count)
VALUES (0, '悦享电台 · 运动 FM 97.5', '心跳跟上鼓点。节奏感强的运动推力歌单。', '运动', 1, 1, 0);
SET @pl := LAST_INSERT_ID();
INSERT INTO playlist_song (playlist_id, song_id, sort) VALUES
    (@pl, 33, 1),   -- Wolves - Selena Gomez：电子节拍推进
    (@pl, 48, 2),   -- 破茧 - 于少保：说唱爆发的斗志
    (@pl, 23, 3),   -- Despacito - Justin Bieber：律动强烈
    (@pl, 22, 4),   -- Beauty and a Beat - Justin Bieber：EDM 节奏
    (@pl, 34, 5),   -- I Want You To Know - Selena Gomez：电舞曲
    (@pl, 27, 6),   -- Yummy - Justin Bieber：节奏鲜明
    (@pl, 29, 7);   -- 红色高跟鞋 - 卡卡（rock）：律动轻快

-- 频道 5：emo（emo）—— 伤感情歌、情绪出口
INSERT INTO playlist (user_id, title, description, tag, is_official, published, play_count)
VALUES (0, '悦享电台 · emo FM 101.3', '丧的时候，歌懂你。情绪化的伤感情歌集中营。', 'emo', 1, 1, 0);
SET @pl := LAST_INSERT_ID();
INSERT INTO playlist_song (playlist_id, song_id, sort) VALUES
    (@pl, 37, 1),   -- I don't wanna see you anymore - NINEONE：失恋叙事
    (@pl, 44, 2),   -- 演员 - 薛之谦：感情里的伪装与戳破
    (@pl, 41, 3),   -- 认真的雪 - 薛之谦：雪夜伤歌
    (@pl, 36, 4),   -- 东京不太热 - 卡卡：暗恋不得的落寞
    (@pl, 47, 5),   -- 大眠 - 于少保：感情里被当傻子
    (@pl, 40, 6),   -- 你还要我怎样 - 薛之谦：分手质问
    (@pl, 39, 7);   -- 怪咖 - 薛之谦：孤独自嘲

-- 频道 6：开心（happy）—— 明亮欢快、好心情配副歌
INSERT INTO playlist (user_id, title, description, tag, is_official, published, play_count)
VALUES (0, '悦享电台 · 开心 FM 103.6', '好心情要配副歌。明亮欢快的高兴歌单。', '欢快', 1, 1, 0);
SET @pl := LAST_INSERT_ID();
INSERT INTO playlist_song (playlist_id, song_id, sort) VALUES
    (@pl, 21, 1),   -- Baby - Justin Bieber：经典欢快
    (@pl, 27, 2),   -- Yummy - Justin Bieber：轻快律动
    (@pl, 26, 3),   -- What Do You Mean - Justin Bieber：热带浩室明亮
    (@pl, 32, 4),   -- Who Says - Selena Gomez：正能量打气
    (@pl, 24, 5),   -- Let Me Love You - Justin Bieber：EDM 明快
    (@pl, 15, 6);   -- 晴天 - 周杰伦：青春晴朗

-- 频道 7：雨天（rain）—— 慵懒舒缓、有氛围感
INSERT INTO playlist (user_id, title, description, tag, is_official, published, play_count)
VALUES (0, '悦享电台 · 雨天 FM 106.2', '雨声和它更配。雨天窗边的氛围感歌单。', '雨天', 1, 1, 0);
SET @pl := LAST_INSERT_ID();
INSERT INTO playlist_song (playlist_id, song_id, sort) VALUES
    (@pl, 15, 1),   -- 晴天 - 周杰伦：歌词里的下雨天
    (@pl, 41, 2),   -- 认真的雪 - 薛之谦：雪雨天气氛围
    (@pl, 35, 3),   -- 微醺 - 卡卡：慵懒微醺氛围感
    (@pl, 25, 4),   -- Love Yourself - Justin Bieber：木吉他雨天感
    (@pl, 44, 5),   -- 演员 - 薛之谦：绵长叙事配雨声
    (@pl, 47, 6);   -- 大眠 - 于少保：爵士氛围

-- 频道 8：咖啡（coffee）—— 慵懒轻柔、咖啡馆调性
INSERT INTO playlist (user_id, title, description, tag, is_official, published, play_count)
VALUES (0, '悦享电台 · 咖啡 FM 108.9', '一杯咖啡一首歌。咖啡馆里的慵懒轻柔。', '咖啡', 1, 1, 0);
SET @pl := LAST_INSERT_ID();
INSERT INTO playlist_song (playlist_id, song_id, sort) VALUES
    (@pl, 35, 1),   -- 微醺 - 卡卡：咖啡馆慵懒天花板
    (@pl, 19, 2),   -- As Long As You Love Me（jazz 版）- Justin Bieber：爵士改编
    (@pl, 47, 3),   -- 大眠 - 于少保：爵士慢摇
    (@pl, 25, 4),   -- Love Yourself - Justin Bieber：原声吉他
    (@pl, 17, 5),   -- 唯一 - 于少保：安静抒情
    (@pl, 28, 6);   -- 海阔天空 - 卡卡（古典）：器乐轻响
