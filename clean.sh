#!/usr/bin/env sh
# 收摊：删容器 + 删数据卷。
#
# 必须带 -v：mysql 官方镜像自带 `VOLUME /var/lib/mysql`，即使 compose 没写 volumes，
# Docker 也会给它建一个【匿名卷】。不删的话下一轮 up 会读到上一轮写进去的数据（脏数据），
# 于是「种子数据条数不对」「规则不该拦却拦了」这类怪现象就来了。
set -eu
cd "$(dirname "$0")"
docker compose down -v
