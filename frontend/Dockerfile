# ============================================================
# 菁选校园 — 前端 Docker 构建
# ============================================================
FROM node:24-alpine AS build
WORKDIR /build
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build
FROM nginx:alpine AS runtime
COPY --from=build /build/dist /usr/share/nginx/html/jingxuan
# 地图边界 GeoJSON 从 public 显式拷贝（vite public 目录产物确定性保障）
COPY --from=build /build/public/geo /usr/share/nginx/html/jingxuan/geo
# 学生端 H5 产物（含 2D 地图 GeoJSON 与安装页）：显式拷贝，避免依赖 vite public 复制行为
COPY --from=build /build/public/app /usr/share/nginx/html/jingxuan/app
COPY --from=build /build/public/app-download /usr/share/nginx/html/jingxuan/app-download
COPY nginx.conf /etc/nginx/conf.d/default.conf
RUN apk add --no-cache libcap \
    && mkdir -p /var/cache/nginx /var/run \
    && chown -R nginx:nginx /usr/share/nginx/html /var/cache/nginx /var/log/nginx /run \
    && setcap cap_net_bind_service=+ep /usr/sbin/nginx
EXPOSE 80
USER nginx
