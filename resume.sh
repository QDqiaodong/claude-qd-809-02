#!/usr/bin/env sh
# 从 stop 状态恢复（容器还在，最快）。
set -eu
cd "$(dirname "$0")"
docker compose start
