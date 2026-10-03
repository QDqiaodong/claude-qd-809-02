ARG DOCKER_REGISTRY=docker.io/library/
FROM ${DOCKER_REGISTRY}mysql:8.0

# 把「初始化」这一步从「每次启动」挪到「构建期」：
# 构建时跑一次官方 entrypoint（建库、建用户、导入 schema.sql），然后把 mysqld 关掉，
# 镜像里留下已经初始化好的数据目录。之后每次起容器都是「秒起」，不再跑 initdb。
ARG MYSQL_DATABASE
ARG MYSQL_USER
ARG MYSQL_PASSWORD
ARG MYSQL_ROOT_PASSWORD

ENV MYSQL_DATABASE=${MYSQL_DATABASE} \
    MYSQL_USER=${MYSQL_USER} \
    MYSQL_PASSWORD=${MYSQL_PASSWORD} \
    MYSQL_ROOT_PASSWORD=${MYSQL_ROOT_PASSWORD} \
    MYSQL_INITDB_SKIP_TZINFO=1

COPY schema.sql /docker-entrypoint-initdb.d/01-schema.sql

RUN set -eux; \
    /entrypoint.sh mysqld --skip-log-bin --performance-schema=OFF & \
    pid=$!; \
    for i in $(seq 1 120); do \
        if mysqladmin ping -h 127.0.0.1 -uroot -p"${MYSQL_ROOT_PASSWORD}" --silent >/dev/null 2>&1; then \
            echo "== initdb done (${i}s) =="; break; \
        fi; \
        sleep 1; \
    done; \
    mysql -h 127.0.0.1 -uroot -p"${MYSQL_ROOT_PASSWORD}" -e "SHOW DATABASES;" >/dev/null; \
    mysqladmin -h 127.0.0.1 -uroot -p"${MYSQL_ROOT_PASSWORD}" shutdown; \
    wait $pid || true; \
    rm -f /docker-entrypoint-initdb.d/01-schema.sql
