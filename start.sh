#!/usr/bin/env sh
# 启动。镜像已存在就直接起（秒级）；首次或传 --build 才构建。
# 首次构建也很快：依赖来自 claude-qd-deps/maven:17 与 claude-qd-deps/node:20 预热镜像。
set -eu
cd "$(dirname "$0")"

if [ "${1:-}" = "--build" ] \
   || [ -z "$(docker images -q --filter "reference=*backend" --filter "dangling=false" 2>/dev/null | head -1)" ]; then
    echo "[start] 构建并启动..."
    exec docker compose up -d --build
fi

echo "[start] 直接启动..."
exec docker compose up -d
