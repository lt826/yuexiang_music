-- ============================================================
-- 歌词补充脚本：给真实曲库种子的已上架歌曲写入 LRC 歌词
-- 背景：seed_real_songs.sql 的 INSERT 不含 lyric 列，全部歌曲无歌词，
--       播放页只能显示"暂无歌词"
-- 重要说明：曲库使用真实歌名/歌手做课程演示，但下方歌词均为
--           【原创演示占位词，非原曲歌词】，仅用于展示歌词滚动效果，
--           请勿对外分发或声称是原作品词。
-- 执行顺序：先 init.sql、seed_real_songs.sql 建好数据，再执行本文件
-- 幂等性：可重复执行（直接覆盖 lyric 字段）
-- 不触碰：id=12（用户上传的《晴天》，已有自己的音频与歌词）
-- ============================================================

USE yuexiang_song;

-- ---------- 歌词模板（原创占位词） ----------

-- T1 华语抒情
SET @t_ballad =
'[00:04.00]街灯亮起 城市慢慢安静
[00:18.00]晚风穿过熟悉的巷口
[00:32.00]你的名字藏在每阵风里
[00:46.00]吹过耳畔又轻轻远走
[01:02.00]如果时间能停在这一秒
[01:16.00]我想把心事都说给你听
[01:30.00]夜色温柔星光落满肩头
[01:44.00]这一刻的心跳只为你停留
[02:00.00]如果明天我们还会相遇
[02:14.00]请记得今晚月亮替我说保重';

-- T2 华语流行节奏
SET @t_pop =
'[00:03.00]清晨的闹钟叫醒沉睡的梦
[00:17.00]背包里装满未知的期待
[00:31.00]地铁呼啸穿过城市心脏
[00:45.00]每一站都有新的色彩
[01:00.00]别管昨天留下了什么遗憾
[01:14.00]今天的太阳一样会灿烂
[01:28.00]跟着节拍把烦恼都甩开
[01:42.00]年轻的脚步永远不更改
[01:58.00]奔跑吧向着发光的方向
[02:12.00]全世界都会为你让路鼓掌';

-- T3 摇滚
SET @t_rock =
'[00:05.00]轰鸣的引擎撕开夜幕
[00:19.00]血液里翻涌着不服输
[00:33.00]汗水砸在滚烫的舞台
[00:47.00]吼出胸口压抑的孤独
[01:03.00]谁来定义我的路
[01:17.00]命运从不由他人书写答复
[01:31.00]哪怕撞上南墙也不退步
[01:45.00]裂缝里才能长出倔强的树
[02:01.00]点燃这黑夜像野火燎原
[02:15.00]燃烧到最后一秒不认输';

-- T4 电子
SET @t_electronic =
'[00:06.00]霓虹在夜色里流动成河
[00:20.00]信号灯闪烁节拍脉搏
[00:34.00]声波穿过人潮的缝隙
[00:48.00]每个心跳都在同频共振着
[01:04.00]让电流带走白天的沉默
[01:18.00]让低音唤醒沉睡的自我
[01:32.00]跳出这被设定好的时刻
[01:46.00]午夜的频率只属于你我
[02:02.00]光年之外传来海的信号
[02:16.00]仲夏夜的序曲正在播放';

-- T5 民谣
SET @t_folk =
'[00:08.00]南方的雨落在旧屋顶
[00:23.00]木吉他唱着走调的温存
[00:38.00]巷口的猫眯着眼打盹
[00:53.00]时光在这里走得特别认真
[01:10.00]一封信写了三年没寄出
[01:25.00]信纸里夹着干枯的花束
[01:40.00]如果你也路过那间便利店
[01:55.00]替我买一杯热的关东煮
[02:12.00]旧日的风还吹着旧信
[02:27.00]远行的人啊记得回家吃晚饭';

-- T6 后摇（无人声，环境氛围式短句）
SET @t_postrock =
'[00:10.00]雾漫过山脊
[00:30.00]候鸟掠过湖面
[00:52.00]风把云推向远方
[01:15.00]灯塔在远处明灭
[01:40.00]浪花碎成星星
[02:05.00]帆影驶向黎明
[02:30.00]海平线上升起太阳';

-- T7 英文（原创占位词）
SET @t_english =
'[00:04.00]Streetlights paint the night in gold
[00:18.00]Every corner has a story untold
[00:32.00]We were young and chasing summer rain
[00:46.00]Writing futures on a window pane
[01:02.00]Hold the moment let it shine
[01:16.00]Every heartbeat keeping time
[01:30.00]Underneath the silver sky
[01:44.00]You and I we learned to fly
[02:00.00]Perfect strangers turned to friends
[02:14.00]Love story never really ends';

-- T8 中国风
SET @t_chinese =
'[00:05.00]青花瓷上釉色晕开烟雨
[00:19.00]素胚勾勒江南的涟漪
[00:33.00]一把纸伞遮住旧梦依稀
[00:47.00]青石板路延伸到回忆
[01:03.00]墨香染透宣纸的叹息
[01:17.00]笔锋转折藏不住思绪
[01:31.00]月光淌过雕花的窗棂
[01:45.00]惊起檐下栖息的燕语
[02:01.00]一曲古筝弹尽千年风雨
[02:15.00]梦里花开花落又一季';

-- ---------- 分配：按歌曲气质就近套用模板 ----------
-- 说明：UPDATE 均限定具体 id，不影响用户自己上传的歌曲

UPDATE `song` SET `lyric` = @t_ballad      WHERE `id` = 13; -- 晴天(种子版)
UPDATE `song` SET `lyric` = @t_ballad      WHERE `id` = 14; -- 七里香
UPDATE `song` SET `lyric` = @t_folk        WHERE `id` = 15; -- 稻香
UPDATE `song` SET `lyric` = @t_rock        WHERE `id` = 16; -- 夜曲
UPDATE `song` SET `lyric` = @t_chinese     WHERE `id` = 17; -- 青花瓷
UPDATE `song` SET `lyric` = @t_pop         WHERE `id` = 18; -- 江南
UPDATE `song` SET `lyric` = @t_ballad      WHERE `id` = 19; -- 修炼爱情
UPDATE `song` SET `lyric` = @t_pop         WHERE `id` = 20; -- 不为谁而作的歌
UPDATE `song` SET `lyric` = @t_ballad      WHERE `id` = 21; -- 十年
UPDATE `song` SET `lyric` = @t_rock        WHERE `id` = 22; -- 浮夸
UPDATE `song` SET `lyric` = @t_pop         WHERE `id` = 23; -- 孤勇者
UPDATE `song` SET `lyric` = @t_rock        WHERE `id` = 24; -- 倔强
UPDATE `song` SET `lyric` = @t_ballad      WHERE `id` = 25; -- 突然好想你
UPDATE `song` SET `lyric` = @t_folk        WHERE `id` = 26; -- 知足
UPDATE `song` SET `lyric` = @t_pop         WHERE `id` = 27; -- 光年之外
UPDATE `song` SET `lyric` = @t_ballad      WHERE `id` = 28; -- 泡沫
UPDATE `song` SET `lyric` = @t_ballad      WHERE `id` = 29; -- 演员
UPDATE `song` SET `lyric` = @t_pop         WHERE `id` = 30; -- 丑八怪
UPDATE `song` SET `lyric` = @t_folk        WHERE `id` = 31; -- 消愁
UPDATE `song` SET `lyric` = @t_folk        WHERE `id` = 32; -- 像我这样的人
UPDATE `song` SET `lyric` = @t_chinese     WHERE `id` = 33; -- 李白
UPDATE `song` SET `lyric` = @t_ballad      WHERE `id` = 34; -- 年少有为
UPDATE `song` SET `lyric` = @t_english     WHERE `id` = 35; -- Love Story
UPDATE `song` SET `lyric` = @t_english     WHERE `id` = 36; -- Blank Space
UPDATE `song` SET `lyric` = @t_english     WHERE `id` = 37; -- Shape of You
UPDATE `song` SET `lyric` = @t_english     WHERE `id` = 38; -- Perfect
UPDATE `song` SET `lyric` = @t_rock        WHERE `id` = 39; -- 海阔天空
UPDATE `song` SET `lyric` = @t_rock        WHERE `id` = 40; -- 光辉岁月
UPDATE `song` SET `lyric` = @t_ballad      WHERE `id` = 41; -- 遇见
UPDATE `song` SET `lyric` = @t_ballad      WHERE `id` = 42; -- 天黑黑

-- 校验（执行后手动跑）：剩余无词的已上架歌曲数，应为 0
-- SELECT COUNT(*) FROM `song` WHERE `status` = 1 AND (`lyric` IS NULL OR `lyric` = '');
