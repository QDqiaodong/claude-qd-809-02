#!/usr/bin/env sh
# 暂停：只停容器、不删容器、不删数据。下次 ./resume.sh 秒级恢复。
set -eu
cd "$(dirname "$0")"
docker compose stop
