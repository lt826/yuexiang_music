#!/usr/bin/env bash
# ============================================================
# 悦享音乐 音频/封面导出脚本（在开发机上执行）
#
# 作用：把 file 服务 volume 里的歌曲/封面/头像打包成 media.tar.gz，
#       传到服务器项目目录后，deploy.sh 会自动解包进 file volume。
#
# 用法：
#   bash scripts/export-media.sh
#   scp media.tar.gz user@服务器:项目目录/
# ============================================================
set -euo pipefail

cd "$(dirname "$0")/.."
VOLUME="yuexiang_backed_file_uploads"

# 挂载用当前目录路径：Git Bash 需 -W 给出 Windows 盘符路径，Linux 原生 pwd 即可
HOST_DIR=$(pwd -W 2>/dev/null || pwd)

# volume 不存在说明 file 服务从未启动过，无数据可导
if ! docker volume inspect "$VOLUME" >/dev/null 2>&1; then
  echo "❌ volume $VOLUME 不存在，请先本地 docker compose up -d 启动过 file 服务" >&2
  exit 1
fi

COUNT=$(docker run --rm -v "$VOLUME":/data alpine sh -c "find /data -type f | wc -l")
if [[ "$COUNT" -eq 0 ]]; then
  echo "❌ volume 为空，没有可导出的文件" >&2
  exit 1
fi

echo "==> 导出 $COUNT 个文件到 media.tar.gz ..."
docker run --rm -v "$VOLUME":/data -v "$HOST_DIR":/backup alpine \
  sh -c "tar czf /backup/media.tar.gz -C /data ."
echo "✅ 完成：$HOST_DIR/media.tar.gz（$(du -h media.tar.gz | cut -f1)）"
echo "   下一步：scp media.tar.gz user@服务器:项目目录/"
