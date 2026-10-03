ARG DOCKER_REGISTRY=docker.io/library/
ARG DEPS_REGISTRY=claude-qd-deps/

# ===== 构建阶段：依赖已烘在预热镜像的 npm 缓存里 =====
FROM ${DEPS_REGISTRY}node:20 AS build

WORKDIR /app

# 同样不要再用 --mount=type=cache,target=/root/.npm（会遮住镜像里预热好的缓存）
COPY package.json package-lock.json ./
RUN npm ci --prefer-offline --no-audit --no-fund --registry=https://registry.npmmirror.com

COPY . .
RUN npm run build

# ===== 运行阶段 =====
ARG DOCKER_REGISTRY=docker.io/library/
FROM ${DOCKER_REGISTRY}nginx:alpine

ENV LANG=zh_CN.UTF-8 TZ=Asia/Shanghai

COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/nginx.conf

EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
