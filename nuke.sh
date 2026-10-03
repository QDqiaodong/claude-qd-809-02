#!/usr/bin/env sh
# 连自建镜像一起删（下次 start.sh 会重建，依赖仍走预热镜像，十几秒）。
set -eu
cd "$(dirname "$0")"
docker compose down -v --rmi local
