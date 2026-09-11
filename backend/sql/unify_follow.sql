-- ============================================================
-- 统一关注关系迁移：认证歌手的关注一律落 user_follow（用户互关），
-- singer_follow 仅保留给平台导入的虚拟歌手（无所属账号）。
-- 用法：mysql -uroot -p < unify_follow.sql
-- ============================================================

-- 1) 认证歌手的旧关注关系迁入用户互关表（幂等：唯一约束冲突时跳过）
INSERT INTO yuexiang_user.user_follow (follower_id, following_id)
SELECT sf.user_id, s.user_id
FROM yuexiang_singer.singer_follow sf
JOIN yuexiang_singer.singer s ON sf.singer_id = s.id
WHERE s.user_id IS NOT NULL
ON DUPLICATE KEY UPDATE following_id = following_id;

-- 2) 删除已迁移的旧记录（避免双计数）
USE yuexiang_singer;
DELETE sf
FROM singer_follow sf
JOIN singer s ON sf.singer_id = s.id
WHERE s.user_id IS NOT NULL;

-- 3) 修正认证时留下的艺名快照：仅修正"艺名 = 该用户用户名"的历史数据
--    （认证界面当时强制取昵称，用户后来改昵称导致艺名停留在旧昵称/用户名）
UPDATE singer s
JOIN yuexiang_user.`user` u ON s.user_id = u.id
SET s.name = u.nickname
WHERE u.nickname IS NOT NULL AND u.nickname <> ''
  AND s.name = u.username;
