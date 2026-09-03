#!/usr/bin/env bash
# ============================================================
# 悦享音乐 服务器部署脚本
#
# 用法（在服务器上的项目目录执行）：
#   ./deploy.sh          # 全量：package + build + up
#   ./deploy.sh fast     # 快速：跳过 package（jar 没变，只重建镜像层缓存能加速）
#
# 前置（首次部署执行一次）：
#   1. 服务器已装 Docker + Docker Compose v2
#   2. 宿主机 MySQL（已执行 sql/init.sql + seed 脚本）与 Nacos 已运行
#   3. cp .env.example .env && vi .env   # 改掉所有默认密码与 DeepSeek key
#   4. 音频/封面数据迁移：本地执行 scripts/export-media.sh 导出 media.tar.gz，
#      传到服务器后由本脚本自动解包进 file volume（见步骤 [3/5]）
# ============================================================
set -euo pipefail

COMPOSE="docker compose -f docker-compose.yml -f docker-compose.prod.yml"
VOLUME="yuexiang_backed_file_uploads"

echo "==> [1/5] 构建后端 jar（yuexiang-common 需先 install 进本地仓库）"
if [[ "${1:-}" != "fast" ]]; then
  ./mvnw -q install -pl yuexiang-common -DskipTests
  ./mvnw -q package -DskipTests
else
  echo "    (fast 模式：跳过 package，使用已有 target/*.jar)"
fi

echo "==> [2/5] 构建前端与后端镜像"
docker compose build

# 挂载用项目目录路径：Git Bash 需 -W 给出 Windows 盘符路径，Linux 原生 pwd 即可
HOST_DIR=$(pwd -W 2>/dev/null || pwd)

echo "==> [3/5] 检查音频/封面数据（数据库歌曲 URL 全部指向 /api/file/**，文件缺失则整站无声）"
# 先确保 volume 存在（up 前手动创建，属主对齐容器内 root）
docker volume create "$VOLUME" >/dev/null
if [[ -f media.tar.gz ]]; then
  echo "    发现 media.tar.gz，解包进 file volume..."
  docker run --rm -v "$VOLUME":/data -v "$HOST_DIR":/backup alpine \
    sh -c "tar xzf /backup/media.tar.gz -C /data && echo '    解包完成：' \$(find /data -type f | wc -l) 个文件"
elif [[ "$(docker run --rm -v "$VOLUME":/data alpine sh -c "find /data/song -type f 2>/dev/null | wc -l")" -eq 0 ]]; then
  echo ""
  echo "    ⚠️  file volume 为空且未找到 media.tar.gz！"
  echo "    歌曲/封面文件还留在开发机上（file 服务 volume），服务器上播放会 404。"
  echo "    迁移方法（在开发机执行）："
  echo "      1) bash scripts/export-media.sh          # 导出 media.tar.gz（约 60MB）"
  echo "      2) scp media.tar.gz user@服务器:项目目录/  # 传到服务器本目录"
  echo "      3) 重新执行 ./deploy.sh fast"
  echo ""
else
  echo "    file volume 已有音频数据，跳过迁移。"
fi

echo "==> [4/5] 启动全部服务（叠加生产覆盖：收端口 + 内存限制）"
$COMPOSE up -d

echo "==> [5/5] 等待服务就绪并冒烟测试"
sleep 40
for i in $(seq 1 10); do
  if curl -sf -o /dev/null "http://localhost/api/song/ping"; then
    echo "✅ 网关就绪"
    break
  fi
  echo "    等待网关... ($i/10)"
  sleep 6
done

curl -sf -o /dev/null "http://localhost/api/song/chart/realtime?limit=1" \
  && echo "✅ 冒烟测试通过：实时热榜接口 200"

# 音频可达性抽查：取一首歌的 URL 实际请求一次，文件缺失时给出明确提示
SONG_URL=$(curl -sf "http://localhost/api/song/chart/realtime?limit=1" \
  | grep -o '"url":"[^"]*"' | head -1 | cut -d'"' -f4 || true)
if [[ -n "$SONG_URL" ]]; then
  curl -sf -o /dev/null "http://localhost$SONG_URL" \
    && echo "✅ 音频文件可访问：$SONG_URL" \
    || echo "⚠️  音频 404：$SONG_URL —— file volume 缺文件，回看步骤 [3/5] 的迁移提示"
fi

curl -sf -o /dev/null "http://localhost:8082/" \
  && echo "✅ 前端可访问：http://<服务器IP>:8082  （或经网关 http://<服务器IP>/）"

echo "==> 部署完成。查看日志：docker compose logs -f [服务名]"
