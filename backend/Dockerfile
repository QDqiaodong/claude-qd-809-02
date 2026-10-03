ARG DOCKER_REGISTRY=docker.io/library/
ARG DEPS_REGISTRY=claude-qd-deps/

# ===== 构建阶段：依赖已烘在预热镜像里，第一次构建也不用下载 =====
FROM ${DEPS_REGISTRY}maven:17 AS build

WORKDIR /app

# 注意：这里【不要】再用 --mount=type=cache,target=/root/.m2
# （挂上 cache 会把镜像里预热好的 /root/.m2 遮住，反而要重新下载）
COPY pom.xml settings.xml ./
RUN mvn -s settings.xml -B -q dependency:go-offline

COPY src ./src
RUN mvn -s settings.xml -B -q package -DskipTests

# ===== 运行阶段 =====
ARG DOCKER_REGISTRY=docker.io/library/
FROM ${DOCKER_REGISTRY}eclipse-temurin:17-jre

WORKDIR /app
ENV LANG=zh_CN.UTF-8 \
    LC_ALL=zh_CN.UTF-8 \
    TZ=Asia/Shanghai \
    JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8"

COPY --from=build /app/target/*.jar app.jar

ARG BACKEND_INTERNAL_PORT=8080
EXPOSE ${BACKEND_INTERNAL_PORT}

ENTRYPOINT ["java", "-XX:TieredStopAtLevel=1", "-XX:+UseSerialGC", "-Xss1m", \
            "-Dspring.jmx.enabled=false", "-Dfile.encoding=UTF-8", "-jar", "app.jar"]
